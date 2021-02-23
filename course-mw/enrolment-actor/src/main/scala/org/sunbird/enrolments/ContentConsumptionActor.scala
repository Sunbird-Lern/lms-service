package org.sunbird.enrolments

import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import javax.inject.Inject
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util._
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.helper.ServiceFactory
import org.sunbird.kafka.client.{InstructionEventGenerator, KafkaClient}
import org.sunbird.learner.constants.{CourseJsonKey, InstructionEvent}
import org.sunbird.learner.util.Util

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet

class ContentConsumptionActor @Inject() extends BaseEnrolmentActor {
    private val mapper = new ObjectMapper
    private var cassandraOperation = ServiceFactory.getInstance
    private var pushTokafkaEnabled: Boolean = true //TODO: to be removed once all are in scala
    private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
    private val assessmentAggregatorDBInfo = Util.dbInfoMap.get(JsonKey.ASSESSMENT_AGGREGATOR_DB)
    val dateFormatter = ProjectUtil.getDateFormatter

    //Added default fields in env varible:
    //content.default.fields=contentid,userid,batchid,courseid,completedcount,completionpercentage,lastcompletedtime,status,viewcount
    val defaultFields: Set[String] = ProjectUtil.getConfigValue("content.default.fields").split(",").toSet
    val jsonFields = Set[String]("progressdetails")

    override def onReceive(request: Request): Unit = {
        Util.initializeContext(request, TelemetryEnvKey.BATCH)
        request.getOperation match {
            case "updateConsumption" => updateConsumption(request)
            case "getConsumption" => getConsumption(request)
            case _ => onReceiveUnsupportedOperation(request.getOperation)
        }
    }
    
    def updateConsumption(request: Request): Unit = {
        val requestBy = request.get(JsonKey.REQUESTED_BY).asInstanceOf[String]
        val requestedFor = request.get(JsonKey.REQUESTED_FOR).asInstanceOf[String]
        
        processAssessments(request, requestBy, requestedFor)
        processContents(request, requestBy, requestedFor)
    }

    def processAssessments(request: Request, requestedBy: String, requestedFor: String) = {
        val assessmentEvents = request.getRequest.getOrDefault(JsonKey.ASSESSMENT_EVENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        if(CollectionUtils.isNotEmpty(assessmentEvents)) {
            val batchAssessmentList: Map[String, List[java.util.Map[String, AnyRef]]] = assessmentEvents.filter(event => StringUtils.isNotBlank(event.getOrDefault(JsonKey.BATCH_ID, "").asInstanceOf[String])).toList.groupBy(event => event.get(JsonKey.BATCH_ID).asInstanceOf[String])
            val batchIds = batchAssessmentList.keySet.toList.asJava
            val batches:Map[String, List[java.util.Map[String, AnyRef]]] = getBatches(request.getRequestContext ,new java.util.ArrayList[String](batchIds), null).toList.groupBy(batch => batch.get(JsonKey.BATCH_ID).asInstanceOf[String])
            val invalidBatchIds = batchAssessmentList.keySet.diff(batches.keySet).toList.asJava
            val validBatches:Map[String, List[java.util.Map[String, AnyRef]]]  = batches.filterKeys(key => batchIds.contains(key))
            val completedBatchIds = validBatches.filter(batch => 1 != batch._2.head.get(JsonKey.STATUS).asInstanceOf[Integer]).keys.toList.asJava
            val invalidAssessments = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
            val validUserIds = List(requestedBy, requestedFor).filter(p => StringUtils.isNotBlank(p))
            val responseMessage = new java.util.HashMap[String, AnyRef]()
            batchAssessmentList.foreach(input => {
                val batchId = input._1
                if(!invalidBatchIds.contains(batchId) && !completedBatchIds.contains(batchId)) {
                    val userAssessments = getDataGroupedByUserId(input._2, requestedBy, requestedFor)
                    userAssessments.foreach(assessments => {
                        val userId = assessments._1
                        if(validUserIds.contains(userId)){
                            assessments._2.foreach(assessment => {
                                syncAssessmentData(assessment)
                                responseMessage.put(batchId, JsonKey.SUCCESS)
                            })
                        } else {
                            invalidAssessments.addAll(assessments._2.asJava)
                        }
                    })
                }
                
            })
            if(CollectionUtils.isNotEmpty(completedBatchIds)) responseMessage.put("NOT_A_ON_GOING_BATCH", completedBatchIds)
            if(CollectionUtils.isNotEmpty(invalidBatchIds)) responseMessage.put("BATCH_NOT_EXISTS", invalidBatchIds)
            if(CollectionUtils.isNotEmpty(invalidAssessments)) {
                val map = new java.util.HashMap[String, AnyRef]() {{
                    put("validUserIds", validUserIds)
                    put("invalidAssessments", invalidAssessments)
                    put("ets", System.currentTimeMillis.asInstanceOf[AnyRef])
                }}
                pushInvalidDataToKafka(request.getRequestContext, map, "Assessments")
            }
            val response = new Response()
            response.putAll(responseMessage)
            sender().tell(response, self)
        }
    }

    def processContents(request: Request, requestedBy: String, requestedFor: String): Unit = {
        val contentList = request.getRequest.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        if(CollectionUtils.isNotEmpty(contentList)) {
            val batchContentList: Map[String, List[java.util.Map[String, AnyRef]]] = contentList.filter(event => StringUtils.isNotBlank(event.getOrDefault(JsonKey.BATCH_ID, "").asInstanceOf[String])).toList.groupBy(event => event.get(JsonKey.BATCH_ID).asInstanceOf[String])
            val batchIds = batchContentList.keySet.toList.asJava
            val batches:Map[String, List[java.util.Map[String, AnyRef]]] = getBatches(request.getRequestContext ,new java.util.ArrayList[String](batchIds), null).toList.groupBy(batch => batch.get(JsonKey.BATCH_ID).asInstanceOf[String])
            val invalidBatchIds = batchContentList.keySet.diff(batches.keySet).toList.asJava
            val validBatches:Map[String, List[java.util.Map[String, AnyRef]]]  = batches.filterKeys(key => batchIds.contains(key))
            val completedBatchIds = validBatches.filter(batch => 1 != batch._2.head.get(JsonKey.STATUS).asInstanceOf[Integer]).keys.toList.asJava
            val responseMessage = new java.util.HashMap[String, AnyRef]()
            val invalidContents = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
            val validUserIds = List(requestedBy, requestedFor).filter(p => StringUtils.isNotBlank(p))
            batchContentList.foreach(input => {
                val batchId = input._1
                if(!invalidBatchIds.contains(batchId) && !completedBatchIds.contains(batchId)) {
                    val userContents = getDataGroupedByUserId(input._2, requestedBy, requestedFor)
                    userContents.foreach(entry => {
                        val userId = entry._1
                        if(validUserIds.contains(userId)) {
                            val courseId = if (entry._2.head.containsKey(JsonKey.COURSE_ID)) entry._2.head.getOrDefault(JsonKey.COURSE_ID, "").asInstanceOf[String] else entry._2.head.getOrDefault(JsonKey.COLLECTION_ID, "").asInstanceOf[String]
                            if(entry._2.head.containsKey(JsonKey.COLLECTION_ID)) entry._2.head.remove(JsonKey.COLLECTION_ID)
                            val contentIds = entry._2.map(e => e.getOrDefault(JsonKey.CONTENT_ID, "").asInstanceOf[String]).asJava
                            val existingContents = getContentsConsumption(userId, courseId, contentIds, batchId, defaultFields, request.getRequestContext).groupBy(x => x.get("contentId").asInstanceOf[String]).map(e => e._1 -> e._2.toList.head).toMap
                            val contents:List[java.util.Map[String, AnyRef]] = entry._2.toList.map(inputContent => {
                                val existingContent = existingContents.getOrElse(inputContent.get("contentId").asInstanceOf[String], new java.util.HashMap[String, AnyRef])
                                processContentConsumption(inputContent, existingContent, userId)
                            })
                            cassandraOperation.batchInsertLogged(request.getRequestContext, consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, contents)
                            val updateData = getLatestReadDetails(userId, batchId, contents)
                            cassandraOperation.updateRecordV2(request.getRequestContext, "sunbird_courses", "user_enrolments", updateData._1, updateData._2, true)
                            pushInstructionEvent(request.getRequestContext, userId, batchId, courseId, contents.asJava)
                            contentIds.map(id => responseMessage.put(id,JsonKey.SUCCESS))

                        } else {
                            logger.info(request.getRequestContext, "ContentConsumptionActor: addContent : User Id is invalid : " + userId)
                            invalidContents.addAll(entry._2.asJava)
                        }
                    })
                   
                }
            })
            if(CollectionUtils.isNotEmpty(completedBatchIds)) responseMessage.put("NOT_A_ON_GOING_BATCH", completedBatchIds)
            if(CollectionUtils.isNotEmpty(invalidBatchIds)) responseMessage.put("BATCH_NOT_EXISTS", invalidBatchIds)
            if(CollectionUtils.isNotEmpty(invalidContents)) {
                val map = new java.util.HashMap[String, AnyRef]() {{
                    put("validUserIds", validUserIds)
                    put("invalidContents", invalidContents)
                    put("ets", System.currentTimeMillis.asInstanceOf[AnyRef])
                }}
                pushInvalidDataToKafka(request.getRequestContext, map, "Contents")
            }
            val response = new Response()
            response.putAll(responseMessage)
            sender().tell(response, self)
        }
    }

    def getDataGroupedByUserId(data: List[java.util.Map[String, AnyRef]], requstedBy: String, requestedFor: String) = {
        val primaryUserId = if(StringUtils.isNotBlank(requestedFor)) requestedFor else requstedBy
        val updatedData: List[java.util.Map[String, AnyRef]] = data.map(f => {
            val userId = f.getOrDefault(JsonKey.USER_ID, "").asInstanceOf[String]
            if(StringUtils.isBlank(userId))
                f.put(JsonKey.USER_ID, primaryUserId)
            f
        })
        updatedData.groupBy(d => d.get(JsonKey.USER_ID).asInstanceOf[String])
    }

    def syncAssessmentData(assessment: java.util.Map[String, AnyRef]) = {
        val topic = ProjectUtil.getConfigValue("kafka_assessment_topic")
        if (StringUtils.isNotBlank(topic)) KafkaClient.send(mapper.writeValueAsString(assessment), topic)
        else throw new ProjectCommonException("BE_JOB_REQUEST_EXCEPTION", "Invalid topic id.", ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    private def pushInvalidDataToKafka(requestContext: RequestContext, data: java.util.Map[String, AnyRef], dataType: String): Unit = {
        logger.info(requestContext, "LearnerStateUpdater - Invalid " + dataType, data)
        val topic = ProjectUtil.getConfigValue("kafka_topics_contentstate_invalid")
        try {
            val event = mapper.writeValueAsString(data)
            KafkaClient.send(event, topic)
        } catch {
            case t: Throwable =>
                t.printStackTrace()
        }
    }

    def getContentsConsumption(userId: String, courseId : String, contentIds: java.util.List[String], batchId: String, fields: java.util.Set[String], requestContext: RequestContext):java.util.List[java.util.Map[String, AnyRef]] = {
        val filters = new java.util.HashMap[String, AnyRef]() {{
            put("userid", userId)
            put("courseid", courseId)
            put("batchid", batchId)
            if(CollectionUtils.isNotEmpty(contentIds))
                put("contentid", contentIds)
        }}
        val response = cassandraOperation.getRecords(requestContext, consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, filters, fields.toList)
        response.getResult.getOrDefault(JsonKey.RESPONSE, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    }

    def processContentConsumption(inputContent: java.util.Map[String, AnyRef], existingContent: java.util.Map[String, AnyRef], userId: String) = {
        val inputStatus = inputContent.getOrDefault(JsonKey.STATUS, 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
        val updatedContent = new java.util.HashMap[String, AnyRef]()
        updatedContent.putAll(inputContent)
        val parsedMap = new java.util.HashMap[String, AnyRef]()
        jsonFields.flatMap(f => if(inputContent.containsKey(f)) Some(parsedMap.put(f,mapper.writeValueAsString(inputContent.get(f)))) else None)
        updatedContent.putAll(parsedMap)

        val inputCompletedTime = parseDate(inputContent.getOrDefault(JsonKey.LAST_COMPLETED_TIME, "").asInstanceOf[String])
        val inputAccessTime = parseDate(inputContent.getOrDefault(JsonKey.LAST_ACCESS_TIME, "").asInstanceOf[String])
        if(MapUtils.isNotEmpty(existingContent)) {
            val existingAccessTime = parseDate(existingContent.getOrDefault(JsonKey.LAST_ACCESS_TIME, "").asInstanceOf[String])
            updatedContent.put(JsonKey.LAST_ACCESS_TIME, compareTime(existingAccessTime, inputAccessTime))
            val inputProgress = inputContent.getOrDefault(JsonKey.PROGRESS, 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
            val existingProgress = Option(existingContent.getOrDefault(JsonKey.PROGRESS, 0.asInstanceOf[AnyRef]).asInstanceOf[Number]).getOrElse(0.asInstanceOf[Number]).intValue()
            updatedContent.put(JsonKey.PROGRESS, List(inputProgress, existingProgress).max.asInstanceOf[AnyRef])
            val existingStatus = Option(existingContent.getOrDefault(JsonKey.STATUS, 0.asInstanceOf[AnyRef]).asInstanceOf[Number]).getOrElse(0.asInstanceOf[Number]).intValue()
            val existingCompletedTime = parseDate(existingContent.getOrDefault(JsonKey.LAST_COMPLETED_TIME, "").asInstanceOf[String])
            if(inputStatus >= existingStatus) {
                if(inputStatus >= 2) {
                    updatedContent.put(JsonKey.STATUS, 2.asInstanceOf[AnyRef])
                    updatedContent.put(JsonKey.PROGRESS, 100.asInstanceOf[AnyRef])
                    updatedContent.put(JsonKey.LAST_COMPLETED_TIME, compareTime(existingCompletedTime, inputCompletedTime))
                }
            } else {
                updatedContent.put(JsonKey.STATUS, existingStatus.asInstanceOf[AnyRef])
            }
        } else {
            if(inputStatus >= 2) {
                updatedContent.put(JsonKey.PROGRESS, 100.asInstanceOf[AnyRef])
                updatedContent.put(JsonKey.LAST_COMPLETED_TIME, compareTime(null, inputCompletedTime))
            } else {
                updatedContent.put(JsonKey.PROGRESS, 0.asInstanceOf[AnyRef])
            }
            updatedContent.put(JsonKey.LAST_ACCESS_TIME, compareTime(null, inputAccessTime))
        }
        updatedContent.put(JsonKey.LAST_UPDATED_TIME, ProjectUtil.getFormattedDate)
        updatedContent.put(JsonKey.USER_ID, userId)
        updatedContent
    }

    def parseDate(dateString: String) = {
        if(StringUtils.isNotBlank(dateString) && !StringUtils.equalsIgnoreCase(JsonKey.NULL, dateString)) {
            dateFormatter.parse(dateString)
        } else null
    }

    def compareTime(existingTime: java.util.Date, inputTime: java.util.Date): String = {
        if(null == existingTime && null == inputTime) {
            ProjectUtil.getFormattedDate
        } else if(null == existingTime) dateFormatter.format(inputTime)
        else if(null == inputTime) dateFormatter.format(existingTime)
        else {
            if(inputTime.after(existingTime)) dateFormatter.format(inputTime)
            else dateFormatter.format(existingTime)
        }
    }

    def getLatestReadDetails(userId: String, batchId: String, contents: List[java.util.Map[String, AnyRef]]) = {
       val lastAccessContent: java.util.Map[String, AnyRef] = contents.groupBy(x => x.getOrDefault(JsonKey.LAST_ACCESS_TIME, "").asInstanceOf[String]).maxBy(_._1)._2.get(0)
       val updateMap = new java.util.HashMap[String, AnyRef] () {{
            put("lastreadcontentid", lastAccessContent.get("contentId"))
            put("lastreadcontentstatus", lastAccessContent.get("status"))
        }}
      val selectMap = new util.HashMap[String, AnyRef]() {{
        put("batchId", batchId)
        put("userId", userId)
        put("courseId", lastAccessContent.get("courseId"))
      }}
      (selectMap, updateMap)
    }

    @throws[Exception]
    private def pushInstructionEvent(requestContext: RequestContext, userId: String, batchId: String, courseId: String, contents: java.util.List[java.util.Map[String, AnyRef]]): Unit = {
        val data = new java.util.HashMap[String, AnyRef]
        data.put(CourseJsonKey.ACTOR, new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.ID, InstructionEvent.BATCH_USER_STATE_UPDATE.getActorId)
            put(JsonKey.TYPE, InstructionEvent.BATCH_USER_STATE_UPDATE.getActorType)
        }})
        data.put(CourseJsonKey.OBJECT, new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.ID, batchId + CourseJsonKey.UNDERSCORE + userId)
            put(JsonKey.TYPE, InstructionEvent.BATCH_USER_STATE_UPDATE.getType)
        }})
        data.put(CourseJsonKey.ACTION, InstructionEvent.BATCH_USER_STATE_UPDATE.getAction)
        val contentsMap = contents.map(c => new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.CONTENT_ID, c.get(JsonKey.CONTENT_ID))
            put(JsonKey.STATUS, c.get(JsonKey.STATUS))
        }}).asJava
        data.put(CourseJsonKey.E_DATA, new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.USER_ID, userId)
            put(JsonKey.BATCH_ID, batchId)
            put(JsonKey.COURSE_ID, courseId)
            put(JsonKey.CONTENTS, contentsMap)
            put(CourseJsonKey.ACTION, InstructionEvent.BATCH_USER_STATE_UPDATE.getAction)
            put(CourseJsonKey.ITERATION, 1.asInstanceOf[AnyRef])
        }})
        val topic = ProjectUtil.getConfigValue("kafka_topics_instruction")
        logger.info(requestContext,"LearnerStateUpdateActor: pushInstructionEvent :Event Data " + data + " and Topic " + topic)
        if(pushTokafkaEnabled)
            InstructionEventGenerator.pushInstructionEvent(userId, topic, data)
    }

    def getConsumption(request: Request): Unit = {
        val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val courseId = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val contentIds = request.getRequest.getOrDefault(JsonKey.CONTENT_IDS, new java.util.ArrayList[String]()).asInstanceOf[java.util.List[String]]
        val fields = request.getRequest.getOrDefault(JsonKey.FIELDS, new java.util.ArrayList[String](){{ add(JsonKey.PROGRESS) }}).asInstanceOf[java.util.List[String]]
        //default fields added ..
        val finalFields = defaultFields ++ fields
        System.out.println("Fields == >"+fields)

        val contentsConsumed = getContentsConsumption(userId, courseId, contentIds, batchId, finalFields.asJava, request.getRequestContext)
        val response = new Response
        if(CollectionUtils.isNotEmpty(contentsConsumed)) {
            val filteredContents = contentsConsumed.map(m => {
                ProjectUtil.removeUnwantedFields(m, JsonKey.DATE_TIME, JsonKey.USER_ID, JsonKey.ADDED_BY, JsonKey.LAST_UPDATED_TIME)
                m.put(JsonKey.COLLECTION_ID, m.getOrDefault(JsonKey.COURSE_ID, ""))
                jsonFields.foreach(field =>
                  if(fields.contains(field))
                    m.put(field, mapper.readTree(m.get(field).asInstanceOf[String]))
                )
                if (fields.contains(JsonKey.ASSESSMENT_SCORE))
                    m.putAll(mapAsJavaMap(Map(JsonKey.ASSESSMENT_SCORE -> getScore(userId, courseId, m.get("contentId").asInstanceOf[String], batchId, request.getRequestContext))))
                m
            }).asJava
            response.put(JsonKey.RESPONSE, filteredContents)
        } else {
            response.put(JsonKey.RESPONSE, new java.util.ArrayList[AnyRef]())
        }
        sender().tell(response, self)
    }
    
    //TODO: to be removed once all in scala
    def setCassandraOperation(cassandraOps: CassandraOperation, kafkaEnabled: Boolean): ContentConsumptionActor = {
        pushTokafkaEnabled = kafkaEnabled
        cassandraOperation = cassandraOps
        this
    }

    def getScore(userId: String, courseId: String, contentId: String, batchId: String, requestContext: RequestContext): util.List[util.Map[String, AnyRef]] = {
        val filters = new java.util.HashMap[String, AnyRef]() {
            {
                put("user_id", userId)
                put("course_id", courseId)
                put("batch_id", batchId)
                put("content_id", contentId)
            }
        }
        val fieldsToGet = new java.util.ArrayList[String](){{
            add("attempt_id")
            add("last_attempted_on")
            add("total_max_score")
            add("total_score")
        }}
        val limit = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("assessment.attempts.limit")))
            (ProjectUtil.getConfigValue("assessment.attempts.limit")).asInstanceOf[Integer] else 25.asInstanceOf[Integer]
        val response = cassandraOperation.getRecordsWithLimit(requestContext, assessmentAggregatorDBInfo.getKeySpace, assessmentAggregatorDBInfo.getTableName, filters, fieldsToGet, limit)
        response.getResult.getOrDefault(JsonKey.RESPONSE, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    }
}
