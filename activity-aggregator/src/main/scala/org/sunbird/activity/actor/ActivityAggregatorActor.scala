package org.sunbird.activity.actor

import com.google.gson.Gson
import org.apache.pekko.actor.Props
import org.apache.commons.lang3.StringUtils
import org.sunbird.activity.domain.{CollectionProgress, ContentStatus, TelemetryEvent, UserContentConsumption, UserEnrolmentAgg}
import org.sunbird.activity.util.{ActivityAggregateUtil, CertificateUtil, ContentSearchUtil, DeDupUtil, RedisUtil}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.helper.ServiceFactory
import org.sunbird.kafka.client.KafkaClient
import org.sunbird.learner.util.Util

import java.util
import javax.inject.Inject
import scala.collection.JavaConverters._

class ActivityAggregatorActor @Inject()(implicit val cacheUtil: RedisCacheUtil) extends BaseEnrolmentActor {

  private val cassandraOperation: CassandraOperation = ServiceFactory.getInstance
  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)
  private val activityAggDBInfo = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB)
  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  
  private val activityAggUtil = new ActivityAggregateUtil()
  private val redisUtil = RedisUtil()
  private val certificateUtil = CertificateUtil()
  private val contentSearchUtil = ContentSearchUtil()
  private val deDupUtil = DeDupUtil()
  private val gson = new Gson()
  
  private val auditEventTopic = ProjectUtil.getConfigValue("kafka_topics_audit_event") match {
    case value if value != null => value
    case _ => "dev.telemetry.raw"
  }
  
  private val moduleAggEnabled = ProjectUtil.getConfigValue("enable_module_aggregation") match {
    case value if value != null => value.toBoolean
    case _ => true
  }
  
  private val filterCompletedEnrolments = ProjectUtil.getConfigValue("filter_processed_enrolments") match {
    case value if value != null => value.toBoolean
    case _ => true
  }
  
  private val dedupEnabled = ProjectUtil.getConfigValue("activity_input_dedup_enabled") match {
    case value if value != null => value.toBoolean
    case _ => false
  }

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "updateActivityAggregates" => updateActivityAggregates(request)
      case _ => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  private def updateActivityAggregates(request: Request): Unit = {
    val requestContext = request.getRequestContext
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val batchId = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val courseId = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
    val contentsRaw = request.get(JsonKey.CONTENTS)
    val contents = if (contentsRaw != null) contentsRaw.asInstanceOf[util.List[util.Map[String, AnyRef]]] else null

    try {
      processActivityAggregates(userId, batchId, courseId, contents, requestContext)
      sender().tell(successResponse(), self)
    } catch {
      case ex: Exception =>
        logger.error(requestContext, s"ActivityAggregatorActor failed for userId: $userId, courseId: $courseId", ex)
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, ex.getMessage)
    }
  }

  private def processActivityAggregates(
                                         userId: String,
                                         batchId: String,
                                         courseId: String,
                                         contents: util.List[util.Map[String, AnyRef]],
                                         requestContext: RequestContext
                                       ): Unit = {
    logger.info(requestContext, s"ActivityAggregatorActor: START processActivityAggregates - userId: $userId, courseId: $courseId, batchId: $batchId")
    
    var dbUserConsumption: UserContentConsumption = null
    
    val uniqueContents = if (contents != null && !contents.isEmpty) {
      val filteredContents = filterValidContents(contents)
      if (filteredContents.isEmpty) {
        logger.info(requestContext, s"No valid contents to process for userId: $userId, courseId: $courseId")
        return
      }
      
      val unique = deduplicateContents(filteredContents, userId, batchId, courseId, requestContext)
      if (unique.isEmpty) {
        logger.info(requestContext, s"No unique contents after deduplication for userId: $userId, courseId: $courseId")
        return
      }
      unique
    } else if (contents == null) {
      logger.info(requestContext, s"Contents key missing, fetching from DB (Force Sync) for userId: $userId, courseId: $courseId")
      dbUserConsumption = getContentStatusFromDB(userId, courseId, batchId, requestContext)
      if (dbUserConsumption.contents.isEmpty) {
        logger.info(requestContext, s"No existing consumption in DB to sync for userId: $userId, courseId: $courseId")
        return
      }
      dbUserConsumption.contents.values.map(c => {
         val m = new java.util.HashMap[String, AnyRef]()
         m.put(JsonKey.CONTENT_ID, c.contentId)
         m.put(JsonKey.STATUS, c.status.asInstanceOf[AnyRef])
         m.put(JsonKey.LAST_ACCESS_TIME, c.lastAccessTime)
         m.put(JsonKey.COMPLETED_COUNT, c.completedCount.asInstanceOf[AnyRef])
         m.put(JsonKey.VIEW_COUNT, c.viewCount.asInstanceOf[AnyRef])
         m.put(JsonKey.PROGRESS, c.progress.asInstanceOf[AnyRef])
         if (c.lastUpdatedTime != null) m.put(JsonKey.LAST_UPDATED_TIME, c.lastUpdatedTime)
         if (c.lastCompletedTime != null) m.put(JsonKey.LAST_COMPLETED_TIME, c.lastCompletedTime)
         m
      }).toList
    } else {
      logger.info(requestContext, s"Received empty contents list. No processing required.")
      return
    }
    
    val contentStatusMap = activityAggUtil.getContentStatusFromContents(uniqueContents.asJava)
    val inputUserConsumption = UserContentConsumption(userId, batchId, courseId, contentStatusMap)
    
    if (dbUserConsumption == null) {
      dbUserConsumption = getContentStatusFromDB(userId, courseId, batchId, requestContext)
    }
    val finalUserConsumption = activityAggUtil.mergeConsumptionData(inputUserConsumption, dbUserConsumption)
    
    updateContentConsumption(finalUserConsumption, requestContext)
    
    // Fetch leaf nodes and optional nodes from Redis
    val leafNodes = redisUtil.getLeafNodes(courseId, courseId, requestContext)
    val optionalNodes = redisUtil.getOptionalNodes(courseId, courseId, requestContext)
    
    if (leafNodes.nonEmpty) {
      val courseAggregations = computeCourseAggregations(finalUserConsumption, courseId, leafNodes, optionalNodes, requestContext)
      updateActivityAggregates(courseAggregations, requestContext)
      
      val collectionProgressList = courseAggregations.filter(agg => agg.collectionProgress.nonEmpty).map(agg => agg.collectionProgress.get)
      val latestRead = activityAggUtil.getLatestReadDetails(userId, batchId, courseId, uniqueContents)
      updateCollectionProgress(collectionProgressList, leafNodes, optionalNodes, latestRead, requestContext)
      
      if (dedupEnabled) {
        uniqueContents.foreach { content =>
          val contentId = Option(content.get(JsonKey.CONTENT_ID)).getOrElse(content.get("contentid")).asInstanceOf[String]
          val status = content.get("status").asInstanceOf[Number].intValue()
          val checksum = deDupUtil.getMessageId(courseId, batchId, userId, contentId, status)
          deDupUtil.storeChecksum(checksum, requestContext)
        }
      }
      
      publishContentAuditEvents(finalUserConsumption, requestContext)
      logger.info(requestContext, s"ActivityAggregatorActor: Successfully processed all aggregates for userId: $userId, courseId: $courseId")
    } else {
      handleMissingLeafNodes(courseId, requestContext)
    }
  }

  private def filterValidContents(contents: util.List[util.Map[String, AnyRef]]): List[util.Map[String, AnyRef]] = {
    val filtered = contents.asScala.filter(c => {
      val contentId = Option(c.get(JsonKey.CONTENT_ID)).getOrElse(c.get("contentid")).asInstanceOf[String]
      val status = c.getOrDefault("status", 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
      StringUtils.isNotBlank(contentId) && status > 0
    }).toList
    if (contents.size() != filtered.size) {
      logger.info(null, s"filterValidContents: Filtered ${contents.size()} -> ${filtered.size} valid contents")
    }
    filtered
  }

  private def deduplicateContents(
                                   contents: List[util.Map[String, AnyRef]],
                                   userId: String,
                                   batchId: String,
                                   courseId: String,
                                   requestContext: RequestContext
                                 ): List[util.Map[String, AnyRef]] = {
    if (!dedupEnabled) {
      logger.info(requestContext, s"deduplicateContents: Deduplication disabled, returning all ${contents.size} contents")
      return contents
    }
    
    logger.info(requestContext, s"deduplicateContents: Checking ${contents.size} contents for duplicates")
    val unique = contents.filter(content => {
      val contentId = Option(content.get(JsonKey.CONTENT_ID)).getOrElse(content.get("contentid")).asInstanceOf[String]
      val status = content.get("status").asInstanceOf[Number].intValue()
      val checksum = deDupUtil.getMessageId(courseId, batchId, userId, contentId, status)
      deDupUtil.isUniqueEvent(checksum, requestContext)
    })
    logger.info(requestContext, s"deduplicateContents: Found ${unique.size} unique contents out of ${contents.size}")
    unique
  }

  private def updateContentConsumption(userConsumption: UserContentConsumption, requestContext: RequestContext): Unit = {
    logger.info(requestContext, s"updateContentConsumption: Creating batch update queries for ${userConsumption.contents.size} records")
    val queries: java.util.List[java.util.Map[String, java.util.Map[String, Object]]] = 
      new java.util.ArrayList[java.util.Map[String, java.util.Map[String, Object]]]()
    
    userConsumption.contents.foreach { case (contentId, content) =>
      val updateMaps = activityAggUtil.createContentConsumptionUpdateMap(
        userConsumption.userId,
        userConsumption.courseId,
        userConsumption.batchId,
        content
      )
      val queryMap: java.util.Map[String, java.util.Map[String, Object]] = new util.HashMap[String, java.util.Map[String, Object]]()
      queryMap.put(JsonKey.PRIMARY_KEY, updateMaps._1.asInstanceOf[java.util.Map[String, Object]])
      queryMap.put(JsonKey.NON_PRIMARY_KEY, updateMaps._2.asInstanceOf[java.util.Map[String, Object]])
      queries.add(queryMap)
    }
    
    if (!queries.isEmpty) {
      logger.info(requestContext, s"updateContentConsumption: Executing batch update with ${queries.size()} queries")
      cassandraOperation.batchUpdate(consumptionDBInfo.getKeySpace, "user_content_consumption", queries, requestContext)
      logger.info(requestContext, s"updateContentConsumption: Batch update completed successfully")
    } else {
      logger.warn(requestContext, s"updateContentConsumption: No queries to execute")
    }
  }

  private def computeCourseAggregations(
                                         userConsumption: UserContentConsumption,
                                         courseId: String,
                                         leafNodes: List[String],
                                         optionalNodes: List[String],
                                         requestContext: RequestContext
                                       ): List[UserEnrolmentAgg] = {
    logger.info(requestContext, s"computeCourseAggregations: Computing course-level aggregation for courseId: $courseId")
    val courseAggOpt = activityAggUtil.computeCourseActivityAgg(userConsumption, leafNodes, optionalNodes, requestContext)
    val courseAggs = if (courseAggOpt.nonEmpty) {
      logger.info(requestContext, s"computeCourseAggregations: Course aggregation computed successfully")
      List(courseAggOpt.get)
    } else {
      logger.warn(requestContext, s"computeCourseAggregations: No course aggregation computed")
      List()
    }
    
    if (moduleAggEnabled) {
      logger.info(requestContext, s"computeCourseAggregations: Module aggregation enabled, computing module-level aggregations")
      val ancestors = userConsumption.contents.map { case (contentId, content) =>
        val ancestorList = redisUtil.getAncestors(courseId, content.contentId, requestContext)
        logger.info(requestContext, s"computeCourseAggregations: contentId: $contentId has ${ancestorList.size} ancestors")
        (contentId, ancestorList)
      }.toMap
      
      val childCollections = ancestors.values.flatten.filter(a => a != courseId).toList.distinct
      logger.info(requestContext, s"computeCourseAggregations: Found ${childCollections.size} child collections: ${childCollections.mkString(", ")}")
      
      val collectionsWithLeafNodes = childCollections.map(collectionId => {
        val collectionLeafNodes = redisUtil.getRequiredLeafNodes(courseId, collectionId, requestContext)
        logger.info(requestContext, s"computeCourseAggregations: collectionId: $collectionId has ${collectionLeafNodes.size} required leaf nodes")
        (collectionId, collectionLeafNodes)
      }).toMap

      val moduleAggs = activityAggUtil.computeModuleActivityAgg(userConsumption, courseId, ancestors, collectionsWithLeafNodes, requestContext)
      logger.info(requestContext, s"computeCourseAggregations: Computed ${moduleAggs.size} module aggregations")
      courseAggs ++ moduleAggs
    } else {
      logger.info(requestContext, s"computeCourseAggregations: Module aggregation disabled, returning only course aggregation")
      courseAggs
    }
  }

  private def updateActivityAggregates(courseAggregations: List[UserEnrolmentAgg], requestContext: RequestContext): Unit = {
    val aggQueries = courseAggregations.map { agg =>
      activityAggUtil.createActivityAggUpdateMap(agg.activityAgg)
    }.asJava
    
    if (!aggQueries.isEmpty) {
      cassandraOperation.batchUpdateWithPutAll(activityAggDBInfo.getKeySpace, activityAggDBInfo.getTableName, aggQueries, requestContext)
    }
  }

  private def updateCollectionProgress(
                                        collectionProgressList: List[CollectionProgress],
                                        leafNodes: List[String],
                                        optionalNodes: List[String],
                                        latestRead: util.Map[String, AnyRef],
                                        requestContext: RequestContext
                                      ): Unit = {
    collectionProgressList.foreach { progress =>
      val shouldUpdateProgress = if (filterCompletedEnrolments) {
        val enrolmentStatus = getEnrolmentStatus(progress.userId, progress.courseId, progress.batchId, requestContext)
        enrolmentStatus != 2
      } else true

      if (shouldUpdateProgress) {
        val updatedLeafNodes = leafNodes.diff(optionalNodes)
        val completionStatus = activityAggUtil.getCompletionStatus(progress.progress, updatedLeafNodes.size)

        val progressUpdateMap = activityAggUtil.createProgressUpdateMap(
          progress.userId, progress.courseId, progress.batchId, 
          progress.progress, 
          completionStatus, 
          progress.completedOn,
          progress.contentStatus,
          latestRead
        )
        cassandraOperation.updateRecordV2(enrolmentDBInfo.getKeySpace, 
          enrolmentDBInfo.getTableName, progressUpdateMap._1, progressUpdateMap._2, true, requestContext)

        if (progress.completed) {
          logger.info(requestContext, s"updateCollectionProgress: Course completed for userId: ${progress.userId}, Publishing certificate event")
          certificateUtil.publishCertificateIssueEvent(progress.userId, progress.courseId, progress.batchId, requestContext)
          publishEnrolmentCompleteAuditEvent(progress, requestContext)
        }
      }
    }
  }


  private def publishContentAuditEvents(userConsumption: UserContentConsumption, requestContext: RequestContext): Unit = {
    val auditEvents = activityAggUtil.generateContentAuditEvents(userConsumption)
    if (auditEvents.nonEmpty) {
      logger.info(requestContext, s"publishContentAuditEvents: Publishing ${auditEvents.size} audit events to topic: $auditEventTopic")
      auditEvents.foreach { event =>
        publishAuditEvent(event, requestContext)
      }
    }
  }

  protected def publishEnrolmentCompleteAuditEvent(progress: CollectionProgress, requestContext: RequestContext): Unit = {
    import org.sunbird.activity.domain._
    
    val auditEvent = TelemetryEvent(
      actor = ActorObject(id = progress.userId, `type` = "User"),
      edata = EventData(props = Array("status", "completedon"), `type` = "enrol-complete"),
      context = EventContext(cdata = Array(
        Map("type" -> "CourseBatch", "id" -> progress.batchId).asJava,
        Map("type" -> "Course", "id" -> progress.courseId).asJava
      )),
      `object` = EventObject(id = progress.userId, `type` = "User", rollup = Map("l1" -> progress.courseId).asJava)
    )
    publishAuditEvent(auditEvent, requestContext)
  }

  protected def publishAuditEvent(event: TelemetryEvent, requestContext: RequestContext): Unit = {
    try {
      val eventJson = gson.toJson(event)
      logger.info(requestContext, s"publishAuditEvent: Publishing event to Kafka - topic: $auditEventTopic")
      KafkaClient.send(eventJson, auditEventTopic)
      logger.info(requestContext, s"publishAuditEvent: Event published successfully")
    } catch {
      case ex: Exception =>
        logger.error(requestContext, s"publishAuditEvent: Failed to publish audit event to Kafka topic $auditEventTopic: ${ex.getMessage}", ex)
    }
  }

  private def handleMissingLeafNodes(courseId: String, requestContext: RequestContext): Unit = {
    val collectionStatus = contentSearchUtil.getCollectionStatus(courseId, requestContext)
    
    if (StringUtils.equalsIgnoreCase(collectionStatus, "Retired")) {
      logger.warn(requestContext, s"Contents consumed from retired collection: $courseId", null)
    } else {
      val errorMsg = s"Leaf nodes not available for published collection: $courseId (status: $collectionStatus)"
      logger.error(requestContext, errorMsg, null)
      throw new Exception(errorMsg)
    }
  }

  private def getEnrolmentStatus(userId: String, courseId: String, batchId: String, requestContext: RequestContext): Int = {
    logger.info(requestContext, s"getEnrolmentStatus: Querying user_enrolments for userId: $userId, courseId: $courseId, batchId: $batchId")
    val selectMap = new util.HashMap[String, AnyRef]() {{
      put("userid", userId)
      put("courseid", courseId)
      put("batchid", batchId)
    }}
    
    val response = cassandraOperation.getRecordsByProperties(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, selectMap, requestContext)
    
    if (response != null && response.getResult != null) {
      val result = response.getResult.get(JsonKey.RESPONSE).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (!result.isEmpty) {
        val enrolment = result.get(0)
        val status = enrolment.getOrDefault("status", 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
        logger.info(requestContext, s"getEnrolmentStatus: Found enrolment with status: $status")
        return status
      } else {
        logger.info(requestContext, s"getEnrolmentStatus: No enrolment found, returning status 0")
      }
    } else {
      logger.warn(requestContext, s"getEnrolmentStatus: Null response from Cassandra")
    }
    
    0
  }

  private def getContentStatusFromDB(userId: String, courseId: String, batchId: String, requestContext: RequestContext): UserContentConsumption = {
    logger.info(requestContext, s"getContentStatusFromDB: Querying user_content_consumption for userId: $userId, courseId: $courseId, batchId: $batchId")
    val response = cassandraOperation.getRecordsByProperties(consumptionDBInfo.getKeySpace, "user_content_consumption", 
      new util.HashMap[String, AnyRef]() {{
        put("userid", userId)
        put("courseid", courseId)
        put("batchid", batchId)
      }}, requestContext)
    
    if (response != null && response.getResult != null) {
      val result = response.getResult.get(JsonKey.RESPONSE).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      logger.info(requestContext, s"getContentStatusFromDB: Found ${result.size()} records in DB")
      
      if (!result.isEmpty) {
        
        val consumptionMap = result.asScala.flatMap(row => {
          val contentId = Option(row.get("contentid"))
            .orElse(Option(row.get("contentId")))
            .orElse(Option(row.get("content_id")))
            .map(_.asInstanceOf[String]).getOrElse("")
            
          if (StringUtils.isNotBlank(contentId)) {
            val status = Option(row.get("status")).orElse(Option(row.get("Status"))).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
            val viewCount = Option(row.get("viewcount")).orElse(Option(row.get("viewCount"))).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
            val completedCount = Option(row.get("completedcount")).orElse(Option(row.get("completedCount"))).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
            val progress = Option(row.get("progress")).orElse(Option(row.get("Progress"))).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
            
            val lastAccessTime = activityAggUtil.parseDate(Option(row.get("lastaccesstime")).getOrElse(row.get("lastAccessTime")))
            val lastCompletedTime = activityAggUtil.parseDate(Option(row.get("lastcompletedtime")).getOrElse(row.get("lastCompletedTime")))
            val lastUpdatedTime = activityAggUtil.parseDate(Option(row.get("lastupdatedtime")).getOrElse(row.get("lastUpdatedTime")))
            
            Some(contentId -> ContentStatus(contentId, status, completedCount, viewCount, progress, lastAccessTime, lastCompletedTime, lastUpdatedTime, fromInput = false))
          } else {
            logger.warn(requestContext, s"getContentStatusFromDB: Skipping row with missing contentId. Keys: ${row.keySet()}")
            None
          }
        }).toMap
        
        logger.info(requestContext, s"getContentStatusFromDB: Returning ${consumptionMap.size} content status records")
        return UserContentConsumption(userId, batchId, courseId, consumptionMap)
      }
    }
    
    logger.info(requestContext, s"getContentStatusFromDB: No existing consumption found, returning empty")
    UserContentConsumption(userId, batchId, courseId, Map.empty)
  }
}

object ActivityAggregatorActor {
  def props(cacheUtil: RedisCacheUtil): Props = Props(new ActivityAggregatorActor()(cacheUtil))
}
