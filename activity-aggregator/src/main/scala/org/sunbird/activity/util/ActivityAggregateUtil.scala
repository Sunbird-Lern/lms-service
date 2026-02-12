package org.sunbird.activity.util

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.activity.domain._
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext

import java.util
import java.util.Date
import scala.collection.JavaConverters._

class ActivityAggregateUtil {

  private val logger = new LoggerUtil(classOf[ActivityAggregateUtil])
  private val formatter = ProjectUtil.getDateFormatter

  /**
   * Convert Java Map contents to Scala ContentStatus map
   */
  def getContentStatusFromContents(contents: util.List[util.Map[String, AnyRef]]): Map[String, ContentStatus] = {
    if (CollectionUtils.isEmpty(contents)) {
      Map.empty
    } else {
      val enrichedContents = contents.asScala.map(content => {
        val contentId = Option(content.get(JsonKey.CONTENT_ID)).getOrElse(content.get("contentid")).asInstanceOf[String]
        val status = Option(content.get("status")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
        val progress = Option(content.get("progress")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
        val viewCount = Option(content.get("viewcount")).map(_.asInstanceOf[Number].intValue()).getOrElse(1)
        val completedCount = Option(content.get(JsonKey.COMPLETED_COUNT))
          .orElse(Option(content.get("completedcount")))
          .map(_.asInstanceOf[Number].intValue())
          .getOrElse(if (status == 2) 1 else 0)

        val lastAccessTime = parseDate(Option(content.get(JsonKey.LAST_ACCESS_TIME)).getOrElse(content.get(JsonKey.LAST_ACCESS_TIME_KEY)))
        val lastCompletedTime = parseDate(Option(content.get(JsonKey.LAST_COMPLETED_TIME)).getOrElse(content.get("last_completed_time")))
        ContentStatus(contentId, status, completedCount, viewCount, progress, lastAccessTime, lastCompletedTime, fromInput = true)
      }).filter(t => StringUtils.isNotBlank(t.contentId) && t.status > 0)
        .groupBy(_.contentId)
      val result = enrichedContents.map { case (contentId, contentList) =>
        val finalStatus = contentList.map(_.status).max
        val views = contentList.map(_.viewCount).sum
        val completion = contentList.map(_.completedCount).sum
        val maxProgress = contentList.map(_.progress).max
        val latestAccess = contentList.flatMap(c => Option(c.lastAccessTime)).sortWith(_.after(_)).headOption.orNull
        val latestCompleted = contentList.flatMap(c => Option(c.lastCompletedTime)).sortWith(_.after(_)).headOption.orNull
        (contentId, ContentStatus(contentId, finalStatus, completion, views, maxProgress, latestAccess, latestCompleted, fromInput = true))
      }
      result
    }
  }

  /**
   * Merge input consumption data with DB consumption data
   * This is critical for maintaining accurate view counts and completion counts
   */
  def mergeConsumptionData(
                            inputData: UserContentConsumption,
                            dbData: UserContentConsumption
                          ): UserContentConsumption = {
    val dbContents = dbData.contents
    val processedContents = inputData.contents.map { case (contentId, inputCC) =>
      val dbCC = dbContents.getOrElse(contentId, ContentStatus(contentId, 0, 0, 0, fromInput = false))
      val finalStatus = List(inputCC.status, dbCC.status).max
      val views = inputCC.viewCount + dbCC.viewCount
      val completion = inputCC.completedCount + dbCC.completedCount
      val progress = if (finalStatus == 2) 100 else List(inputCC.progress, dbCC.progress).max
      val lastAccessTime = compareTime(dbCC.lastAccessTime, inputCC.lastAccessTime)
      val lastCompletedTime = if (finalStatus == 2) {
        if (dbCC.status < 2) compareTime(null, inputCC.lastCompletedTime)
        else compareTime(dbCC.lastCompletedTime, inputCC.lastCompletedTime)
      } else null
      val eventsFor = getEventActions(dbCC, inputCC)
      
      (contentId, ContentStatus(contentId, finalStatus, completion, views, progress, lastAccessTime, lastCompletedTime, ProjectUtil.getTimeStamp, inputCC.fromInput, eventsFor))
    }
    
    val existingContents = processedContents.keySet
    val remainingContents = dbData.contents.filterNot { case (key, _) => existingContents.contains(key) }
    val finalContentsMap = processedContents ++ remainingContents
    UserContentConsumption(inputData.userId, inputData.batchId, inputData.courseId, finalContentsMap)
  }

  /**
   * Determine which audit events should be generated for a content
   *
   * @param dbCC Content status from database
   * @param inputCC Content status from input
   * @return List of event types to generate ("start", "complete")
   */
  def getEventActions(dbCC: ContentStatus, inputCC: ContentStatus): List[String] = {
    val startAction = if (dbCC.viewCount == 0) List("start") else List()
    val completeAction = if (dbCC.completedCount == 0 && inputCC.completedCount > 0) List("complete") else List()
    startAction ::: completeAction
  }

  /**
   * Generate content audit events for telemetry
   */
  def generateContentAuditEvents(userConsumption: UserContentConsumption): List[TelemetryEvent] = {
    val userId = userConsumption.userId
    val courseId = userConsumption.courseId
    val batchId = userConsumption.batchId
    
    val contentsForEvents = userConsumption.contents.filter(_._2.eventsFor.nonEmpty).values
    
    contentsForEvents.flatMap { content =>
      content.eventsFor.map { action =>
        val properties = if (StringUtils.equalsIgnoreCase(action, "complete")) {
          Array("viewcount", "completedcount")
        } else {
          Array("viewcount")
        }
        
        TelemetryEvent(
          actor = ActorObject(id = userConsumption.userId),
          edata = EventData(props = properties, `type` = action),
          context = EventContext(cdata = Array(Map("type" -> "CourseBatch", "id" -> userConsumption.batchId).asJava)),
          `object` = EventObject(id = content.contentId, `type` = "Content", rollup = Map("l1" -> userConsumption.courseId).asJava)
        )
      }
    }.toList
  }

  /**
   * Compute course-level activity aggregates
   */
  def computeCourseActivityAgg(
                                 userConsumption: UserContentConsumption,
                                 leafNodes: List[String],
                                 optionalNodes: List[String],
                                 requestContext: RequestContext
                               ): Option[UserEnrolmentAgg] = {
    val courseId = userConsumption.courseId
    val userId = userConsumption.userId
    val contextId = "cb:" + userConsumption.batchId

    logger.info(requestContext, s"computeCourseActivityAgg: courseId: $courseId, userId: $userId, leafNodes: ${leafNodes.size}, optionalNodes: ${optionalNodes.size}")

    if (leafNodes.isEmpty) {
      logger.warn(requestContext, s"computeCourseActivityAgg: Leaf nodes are not available for courseId: $courseId", null)
      None
    } else {
      val updatedLeafNodes = leafNodes.diff(optionalNodes)
      logger.info(requestContext, s"computeCourseActivityAgg: Required leaf nodes (excluding optional): ${updatedLeafNodes.size}")
      
      val completedContents = userConsumption.contents.filter(cc => cc._2.status == 2).map(cc => cc._2.contentId).toList.distinct
      logger.info(requestContext, s"computeCourseActivityAgg: User has completed ${completedContents.size} contents")
      
      val completedCount = updatedLeafNodes.intersect(completedContents).size
      logger.info(requestContext, s"computeCourseActivityAgg: Completed count: $completedCount / ${updatedLeafNodes.size} required leaf nodes")

      val contentStatus = userConsumption.contents.map(cc => (cc._2.contentId, cc._2.status)).toMap
      val inputContents = userConsumption.contents.filter(cc => cc._2.fromInput).keys.toList
      
      val isCompleted = completedCount >= updatedLeafNodes.size
      logger.info(requestContext, s"computeCourseActivityAgg: Course completed: $isCompleted")
      
      val collectionProgress = if (isCompleted) {
        logger.info(requestContext, s"computeCourseActivityAgg: Creating CollectionProgress with completion date")
        Option(CollectionProgress(userId, userConsumption.batchId, courseId, completedCount, new Date(), contentStatus, inputContents, true))
      } else {
        logger.info(requestContext, s"computeCourseActivityAgg: Creating CollectionProgress without completion date")
        Option(CollectionProgress(userId, userConsumption.batchId, courseId, completedCount, null, contentStatus, inputContents, false))
      }

      val activityAgg = UserActivityAgg(
        "Course",
        userId,
        courseId,
        contextId,
        Map("completedCount" -> completedCount.toDouble),
        Map("completedCount" -> System.currentTimeMillis())
      )

      logger.info(requestContext, s"computeCourseActivityAgg: Created UserEnrolmentAgg with completedCount: $completedCount")
      Option(UserEnrolmentAgg(activityAgg, collectionProgress))
    }
  }

  /**
   * Compute module-level (collection children) activity aggregates
   */
  def computeModuleActivityAgg(
                                 userConsumption: UserContentConsumption,
                                 courseId: String,
                                 ancestors: Map[String, List[String]],
                                 collectionsWithLeafNodes: Map[String, List[String]],
                                 requestContext: RequestContext
                               ): List[UserEnrolmentAgg] = {
    val userId = userConsumption.userId
    val contextId = "cb:" + userConsumption.batchId
    val childCollections = ancestors.values.flatten.filter(a => !StringUtils.equals(a, courseId)).toList.distinct
    val userCompletedContents = userConsumption.contents.filter(cc => cc._2.status == 2).map(cc => cc._2.contentId).toList.distinct
    childCollections.flatMap(collectionId => {
      collectionsWithLeafNodes.get(collectionId).map(leafNodes => {
        val completedCount = leafNodes.intersect(userCompletedContents).size
        val activityAgg = UserActivityAgg(
          "Course",
          userId,
          collectionId,
          contextId,
          Map("completedCount" -> completedCount.toDouble),
          Map("completedCount" -> System.currentTimeMillis())
        )
        UserEnrolmentAgg(activityAgg, None)
      })
    })
  }

  /**
   * Get completion percentage
   */
  def getCompletionPercentage(completedCount: Int, leafNodesCount: Int): Int = {
    if (leafNodesCount == 0) 0
    else if (completedCount >= leafNodesCount) 100
    else (completedCount * 100) / leafNodesCount
  }

  /**
   * Get completion status
   * 0 = Not Started, 1 = In Progress, 2 = Completed
   */
  def getCompletionStatus(completedCount: Int, leafNodesCount: Int): Int = {
    if (completedCount == 0) 0
    else if (completedCount >= leafNodesCount) 2
    else 1
  }

  /**
   * Get latest read details for user_enrolments update
   */
  def getLatestReadDetails(userId: String, batchId: String, courseId: String, contents: List[util.Map[String, AnyRef]]): util.Map[String, AnyRef] = {
    val result = new util.HashMap[String, AnyRef]()
    if (contents == null || contents.isEmpty) return result
    
    val contentsWithTime = contents.map(c => {
      val time = parseDate(Option(c.get(JsonKey.LAST_ACCESS_TIME)).getOrElse(c.get(JsonKey.LAST_ACCESS_TIME_KEY)))
      (c, time)
    }).filter(_._2 != null)
    
    if (contentsWithTime.isEmpty) return result
    
    val lastAccessContent = contentsWithTime.maxBy(_._2)._1

    result.put("lastreadcontentid", lastAccessContent.get(JsonKey.CONTENT_ID))
    result.put("lastreadcontentstatus", Option(lastAccessContent.get("status")).getOrElse(1).asInstanceOf[AnyRef])
    result.put(JsonKey.LAST_CONTENT_ACCESS_TIME, lastAccessContent.get(JsonKey.LAST_ACCESS_TIME_KEY))
    result
  }

  /**
   * Create activity aggregate map for Cassandra upsert
   */
  def createActivityAggMap(activityAgg: UserActivityAgg): util.Map[String, AnyRef] = {
    new util.HashMap[String, AnyRef]() {{
      put("activity_type", activityAgg.activity_type)
      put("user_id", activityAgg.user_id)
      put("activity_id", activityAgg.activity_id)
      put("context_id", activityAgg.context_id)
      put("aggregates", activityAgg.aggregates.asJava)
      put("agg_last_updated", activityAgg.agg_last_updated.asJava)
    }}
  }

  /**
   * Create activity aggregate update map for batch update
   * Returns format needed for batchUpdate: Map with PRIMARY_KEY and NON_PRIMARY_KEY
   */
  def createActivityAggUpdateMap(activityAgg: UserActivityAgg): util.Map[String, util.Map[String, AnyRef]] = {
    val primaryKey = new util.HashMap[String, AnyRef]() {{
      put("activity_type", activityAgg.activity_type)
      put("activity_id", activityAgg.activity_id)
      put("user_id", activityAgg.user_id)
      put("context_id", activityAgg.context_id)
    }}
    
    val nonPrimaryKey = new util.HashMap[String, AnyRef]() {{
      put("aggregates", activityAgg.aggregates.asJava)
      put("agg_last_updated", activityAgg.agg_last_updated.asJava)
    }}
    
    new util.HashMap[String, util.Map[String, AnyRef]]() {{
      put(JsonKey.PRIMARY_KEY, primaryKey)
      put(JsonKey.NON_PRIMARY_KEY, nonPrimaryKey)
    }}
  }

  /**
   * Create progress update map for user_enrolments
   */
  def createProgressUpdateMap(
                               userId: String,
                               courseId: String,
                               batchId: String,
                               progress: Int,
                               status: Int,
                               completedOn: Date,
                               contentStatus: Map[String, Int],
                               latestRead: util.Map[String, AnyRef]
                             ): (util.Map[String, AnyRef], util.Map[String, AnyRef]) = {
    val selectMap = new util.HashMap[String, AnyRef]()
    selectMap.put("userid", userId)
    selectMap.put("courseid", courseId)
    selectMap.put("batchid", batchId)

    val updateMap = new util.HashMap[String, AnyRef]()
    updateMap.put("progress", progress.asInstanceOf[AnyRef])
    updateMap.put("status", status.asInstanceOf[AnyRef])
    updateMap.put("contentstatus", contentStatus.asJava)
    updateMap.put("datetime", System.currentTimeMillis().asInstanceOf[AnyRef])
    if (completedOn != null) {
      updateMap.put("completedon", completedOn)
    }
    if (latestRead != null && !latestRead.isEmpty) {
      updateMap.putAll(latestRead)
    }

    (selectMap, updateMap)
  }

  /**
   * Create content consumption update map for user_content_consumption table
   */
  def createContentConsumptionUpdateMap(
                                         userId: String,
                                         courseId: String,
                                         batchId: String,
                                         content: ContentStatus
                                       ): (util.Map[String, AnyRef], util.Map[String, AnyRef]) = {
    val selectMap = new util.HashMap[String, AnyRef]() {{
      put("userid", userId)
      put("courseid", courseId)
      put("batchid", batchId)
      put("contentid", content.contentId)
    }}

    val updateMap = new util.HashMap[String, AnyRef]() {{
      put("viewcount", content.viewCount.asInstanceOf[AnyRef])
      put("completedcount", content.completedCount.asInstanceOf[AnyRef])
      put("status", content.status.asInstanceOf[AnyRef])
      put("progress", content.progress.asInstanceOf[AnyRef])
      put("lastupdatedtime", formatDate(content.lastUpdatedTime))
      put("lastaccesstime", formatDate(content.lastAccessTime))
      if (content.lastCompletedTime != null) {
        put("lastcompletedtime", formatDate(content.lastCompletedTime))
      }
      put("datetime", content.lastUpdatedTime)
    }}
    (selectMap, updateMap)
  }

  def parseDate(dateString: AnyRef): Date = {
    dateString match {
      case s: String if StringUtils.isNotBlank(s) && !StringUtils.equalsIgnoreCase("null", s) => 
        try { formatter.parse(s) } catch { case _: Exception => null }
      case d: Date => d
      case _ => null
    }
  }

  def formatDate(date: Date): String = {
    if (date != null) formatter.format(date) else null
  }

  def compareTime(existingTime: Date, inputTime: Date): Date = {
    if (existingTime == null && inputTime == null) ProjectUtil.getTimeStamp
    else if (existingTime == null) inputTime
    else if (inputTime == null) existingTime
    else if (inputTime.after(existingTime)) inputTime
    else existingTime
  }
}
