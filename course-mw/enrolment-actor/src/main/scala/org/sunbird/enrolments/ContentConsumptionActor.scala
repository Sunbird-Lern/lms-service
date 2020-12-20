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

class ContentConsumptionActor @Inject() extends BaseEnrolmentActor {
    private val mapper = new ObjectMapper
    private var cassandraOperation = ServiceFactory.getInstance
    private var pushTokafkaEnabled: Boolean = true //TODO: to be removed once all are in scala
    private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
    val dateFormatter = ProjectUtil.getDateFormatter

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
                            val existingContents = getContentsConsumption(userId, courseId, contentIds, batchId, request.getRequestContext).groupBy(x => x.get("contentId").asInstanceOf[String]).map(e => e._1 -> e._2.toList.head).toMap
                            //added progress....
                            val cprogress = getProgress(entry._2)
                            cassandraOperation.batchInsert(request.getRequestContext, consumptionDBInfo.getKeySpace, "user_content_consumption_progress", cprogress)
                            
                            val contents:List[java.util.Map[String, AnyRef]] = entry._2.toList.map(inputContent => {
                                inputContent.remove("progressDetails")
                                val existingContent = existingContents.getOrElse(inputContent.get("contentId").asInstanceOf[String], new java.util.HashMap[String, AnyRef])
                                processContentConsumption(inputContent, existingContent, userId)
                            })
                            cassandraOperation.batchInsert(request.getRequestContext, consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, contents)
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

    def getContentsConsumption(userId: String, courseId : String, contentIds: java.util.List[String], batchId: String, requestContext: RequestContext):java.util.List[java.util.Map[String, AnyRef]] = {
        val filters = new java.util.HashMap[String, AnyRef]() {{
            put("userid", userId)
            put("courseid", courseId)
            put("batchid", batchId)
            if(CollectionUtils.isNotEmpty(contentIds))
                put("contentid", contentIds)
        }}
        val response = cassandraOperation.getRecords(requestContext, consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, filters, null)
        response.getResult.getOrDefault(JsonKey.RESPONSE, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    }
    
    //Added content progress
    def getContentsProgress(userId: String, courseId : String, contentIds: java.util.List[String], batchId: String, requestContext: RequestContext):java.util.List[java.util.Map[String, AnyRef]] = {
        val filters = new java.util.HashMap[String, AnyRef]() {{
            put("userid", userId)
            put("courseid", courseId)
            put("batchid", batchId)
            if(CollectionUtils.isNotEmpty(contentIds))
                put("contentid", contentIds)
        }}
        val response = cassandraOperation.getBlobAsText(requestContext, consumptionDBInfo.getKeySpace, "user_content_consumption_progress", filters, util.Arrays.asList("body") )
        response.getResult.getOrDefault(JsonKey.RESPONSE, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    }

    def getProgress(contents: List[java.util.Map[String, AnyRef]]) = {
        val data = new java.util.ArrayList[java.util.Map[String, AnyRef]](){{
            contents.map(obj => {
                val m = obj.get("progressDetails").asInstanceOf[java.util.Map[String, AnyRef]]
                if(m!=null)
                    add(Map("userId"->obj.get("userId"), "courseId"->obj.get("courseId"),"contentId"->obj.get("contentId"),"batchId"->obj.get("batchId"),
                        "body"->mapper.writeValueAsString(m)))
            })
        }}
        data
    }

    def processContentConsumption(inputContent: java.util.Map[String, AnyRef], existingContent: java.util.Map[String, AnyRef], userId: String) = {
        val inputStatus = inputContent.getOrDefault(JsonKey.STATUS, 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
        val updatedContent = new java.util.HashMap[String, AnyRef]()
        updatedContent.putAll(inputContent)
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
        val contentsConsumed = getContentsConsumption(userId, courseId, contentIds, batchId, request.getRequestContext)
        //added progress....
        val contentsProgress = getContentsProgress(userId, courseId, contentIds, batchId, request.getRequestContext)
        val progressMap = contentsProgress.map(data => (data.get(JsonKey.CONTENT_ID).asInstanceOf[String], data)).toMap
        
        val response = new Response
        if(CollectionUtils.isNotEmpty(contentsConsumed)) {
            val filteredContents = contentsConsumed.map(m => {
                ProjectUtil.removeUnwantedFields(m, JsonKey.DATE_TIME, JsonKey.USER_ID, JsonKey.ADDED_BY, JsonKey.LAST_UPDATED_TIME)
                m.put(JsonKey.COLLECTION_ID, m.getOrDefault(JsonKey.COURSE_ID, ""))

                //added progress starts....
                val progressDetails = progressMap.get(m.get(JsonKey.CONTENT_ID))
                if(progressDetails!=null){
                    m.put("progressDetails", mapper.readTree(progressDetails.get("body").asInstanceOf[String]))
                }

                //added progress.
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
}
