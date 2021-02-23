package org.sunbird.enrolments

import akka.actor.ActorRef
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil.{EnrolmentType, ProgressStatus}
import org.sunbird.common.models.util._
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.learner.actors.coursebatch.dao.{CourseBatchDao, UserCoursesDao}
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.learner.util._
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.telemetry.util.TelemetryUtil

import java.sql.Timestamp
import java.text.MessageFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util
import java.util.Date
import javax.inject.{Inject, Named}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class CourseEnrolmentActor @Inject()(@Named("course-batch-notification-actor") courseBatchNotificationActorRef: ActorRef
                                    )(implicit val  cacheUtil: RedisCacheUtil ) extends BaseEnrolmentActor {

    /*
    The below variables are kept as var on testcase purpose.
    TODO: once all are moved to scala, this can be made as parameterised constructor
     */
    var courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()
    var userCoursesDao: UserCoursesDao = new UserCoursesDaoImpl()
    val userCoursesService = new UserCoursesService
    var groupDao: GroupDaoImpl = new GroupDaoImpl()
    val isCacheEnabled = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")).toBoolean else true
    val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")).toInt else 60
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override def preStart { println("Starting CourseEnrolmentActor") }

    override def postStop {
        cacheUtil.closePool()
        println("CourseEnrolmentActor stopped successfully")
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
        println(s"Restarting CourseEnrolmentActor: $message")
        reason.printStackTrace()
        super.preRestart(reason, message)
    }

    override def onReceive(request: Request): Unit = {
        Util.initializeContext(request, TelemetryEnvKey.BATCH)
        request.getOperation match {
            case "enrol" => enroll(request)
            case "unenrol" => unEnroll(request)
            case "listEnrol" => list(request)
            case "getParticipantsForFixedBatch" => fetchParticipantsForFixedBatch(request)
            case _ => ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData,
                ResponseCode.invalidRequestData.getErrorMessage)
        }
    }

    def enroll(request: Request): Unit = {
        val courseId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val isFixedBatch: Boolean = request.getRequest.containsKey(JsonKey.FIXED_BATCH_ID)
        val batchData: CourseBatch = getBatch(request.getRequestContext, courseId, batchId, isFixedBatch)
        val enrolmentData: UserCourses = userCoursesDao.read(request.getRequestContext, userId, courseId, batchId)
        validateEnrolment(batchData, enrolmentData, true)
        val data: java.util.Map[String, AnyRef] = createUserEnrolmentMap(userId, courseId, batchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
        upsertEnrollment(userId, courseId, batchId, data, (null == enrolmentData), request.getRequestContext)
        logger.info(request.getRequestContext, "CourseEnrolmentActor :: enroll :: Deleting redis for key " + getCacheKey(userId))
        cacheUtil.delete(getCacheKey(userId))
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, data, "enrol", JsonKey.CREATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.ADD)
    }

    def unEnroll(request:Request): Unit = {
        val courseId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val isFixedBatch: Boolean = request.getRequest.containsKey(JsonKey.FIXED_BATCH_ID)
        val batchData: CourseBatch = getBatch(request.getRequestContext, courseId, batchId, isFixedBatch)
        val enrolmentData: UserCourses = userCoursesDao.read(request.getRequestContext, userId, courseId, batchId)
        getUpdatedStatus(enrolmentData)
        validateEnrolment(batchData, enrolmentData, false)
        val data: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {{ put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue.asInstanceOf[AnyRef]) }}
        upsertEnrollment(userId,courseId, batchId, data, false, request.getRequestContext)
        logger.info(request.getRequestContext, "CourseEnrolmentActor :: unEnroll :: Deleting redis for key " + getCacheKey(userId))
        cacheUtil.delete(getCacheKey(userId))
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, data, "unenrol", JsonKey.UPDATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.REMOVE)
    }

    def fetchParticipantsForFixedBatch(request: Request): Unit = {
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val isFixedBatch: Boolean = request.getRequest.containsKey(JsonKey.FIXED_BATCH_ID)
        if (!isFixedBatch)
            ProjectCommonException.throwClientErrorException(ResponseCode.missingFixedBatchId, ResponseCode.missingFixedBatchId.getErrorMessage)
        var users: util.List[String] = userCoursesService.getParticipantsList(batchId, true, request.getRequestContext)
        if (users == null) users = new util.ArrayList()
        val response: Response = new Response
        val result = new util.HashMap[String, Object]
        result.put(JsonKey.COUNT, users.size.asInstanceOf[Integer])
        result.put(JsonKey.PARTICIPANTS, users)
        response.put(JsonKey.PARTICIPANTS, result)
        sender.tell(response, self)
    }

    def list(request: Request): Unit = {
        val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
        logger.info(request.getRequestContext,"CourseEnrolmentActor :: list :: UserId = " + userId)
        val response = if (isCacheEnabled && request.getContext.get("cache").asInstanceOf[Boolean])
            getCachedEnrolmentList(userId, () => getEnrolmentList(request, userId)) else getEnrolmentList(request, userId)
        sender().tell(response, self)
    }

    def getActiveEnrollments(userId: String, requestContext: RequestContext): java.util.List[java.util.Map[String, AnyRef]] = {
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = userCoursesDao.listEnrolments(requestContext, userId)
        if (CollectionUtils.isNotEmpty(enrolments))
            enrolments.filter(e => e.getOrDefault(JsonKey.ACTIVE, false.asInstanceOf[AnyRef]).asInstanceOf[Boolean]).toList.asJava
        else
            new util.ArrayList[java.util.Map[String, AnyRef]]()
    }

    def addCourseDetails(activeEnrolments: java.util.List[java.util.Map[String, AnyRef]], courseIds: java.util.List[String] , request:Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val requestBody: String =  prepareSearchRequest(courseIds, request)
        val searchResult:java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getRequestContext, request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING,"").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
        val coursesList: java.util.List[java.util.Map[String, AnyRef]] = searchResult.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val coursesMap = {
            if(CollectionUtils.isNotEmpty(coursesList)) {
                coursesList.map(ev => ev.get(JsonKey.IDENTIFIER).asInstanceOf[String] -> ev).toMap
            } else courseIds.map(c => c -> new util.HashMap[String, AnyRef]()).toMap
        }
        
        activeEnrolments.filter(enrolment => coursesMap.containsKey(enrolment.get(JsonKey.COURSE_ID))).map(enrolment => {
            val courseContent = coursesMap.get(enrolment.get(JsonKey.COURSE_ID))
            enrolment.put(JsonKey.COURSE_NAME, courseContent.get(JsonKey.NAME))
            enrolment.put(JsonKey.DESCRIPTION, courseContent.get(JsonKey.DESCRIPTION))
            enrolment.put(JsonKey.LEAF_NODE_COUNT, courseContent.get(JsonKey.LEAF_NODE_COUNT))
            enrolment.put(JsonKey.COURSE_LOGO_URL, courseContent.get(JsonKey.APP_ICON))
            enrolment.put(JsonKey.CONTENT_ID, enrolment.get(JsonKey.COURSE_ID))
            enrolment.put(JsonKey.COLLECTION_ID, enrolment.get(JsonKey.COURSE_ID))
            enrolment.put(JsonKey.CONTENT, courseContent)
            enrolment
        }).toList.asJava
    }

    def prepareSearchRequest(courseIds: java.util.List[String], request: Request): String = {
        val filters: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.IDENTIFIER, courseIds)
            put(JsonKey.STATUS, "Live")
            put(JsonKey.TRACKABLE_ENABLED, JsonKey.YES)
            putAll(request.getRequest.getOrDefault(JsonKey.FILTERS, new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]])
        }}
        val searchRequest:java.util.Map[String, java.util.Map[String, AnyRef]] = new java.util.HashMap[String, java.util.Map[String, AnyRef]]() {{
            put(JsonKey.REQUEST, new java.util.HashMap[String, AnyRef](){{
                put(JsonKey.FILTERS, filters)
                put(JsonKey.LIMIT, courseIds.size().asInstanceOf[AnyRef])
            }})
        }}
        new ObjectMapper().writeValueAsString(searchRequest)
    }

    def addBatchDetails(enrolmentList: util.List[util.Map[String, AnyRef]], request: Request): util.List[util.Map[String, AnyRef]] = {
        val batchIds:java.util.List[String] = enrolmentList.map(e => e.getOrDefault(JsonKey.BATCH_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
        val batchDetails = searchBatchDetails(batchIds, request)
        if(CollectionUtils.isNotEmpty(batchDetails)){
            val batchMap = batchDetails.map(b => b.get(JsonKey.BATCH_ID).asInstanceOf[String] -> b).toMap
            enrolmentList.map(enrolment => {
                enrolment.put(JsonKey.BATCH, batchMap.getOrElse(enrolment.get(JsonKey.BATCH_ID).asInstanceOf[String], new java.util.HashMap[String, AnyRef]()))
                //To Do : A temporary change to support updation of completed course remove in next release
                //                if (enrolment.get("progress").asInstanceOf[Integer] < enrolment.get("leafNodesCount").asInstanceOf[Integer]) {
                //                    enrolment.put("status", 1.asInstanceOf[Integer])
                //                    enrolment.put("completedOn", null)
                //                }
                enrolment
            }).toList.asJava
        } else
            enrolmentList
    }

    def searchBatchDetails(batchIds: java.util.List[String], request: Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val requestedFields: java.util.List[String] = if(null != request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[Array[String]]) request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[Array[String]](0).split(",").toList.asJava else new java.util.ArrayList[String]()
        if(CollectionUtils.isNotEmpty(requestedFields)) {
          val fields = new java.util.ArrayList[String]()
            fields.addAll(requestedFields)
            fields.add(JsonKey.BATCH_ID)
            fields.add(JsonKey.IDENTIFIER)
          getBatches(request.getRequestContext ,new java.util.ArrayList[String](batchIds), fields)
        } else {
            new java.util.ArrayList[util.Map[String, AnyRef]]()
        }
    }
    
    
    def validateEnrolment(batchData: CourseBatch, enrolmentData: UserCourses, isEnrol: Boolean): Unit = {
        if(null == batchData) ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage)
        
        if(EnrolmentType.inviteOnly.getVal.equalsIgnoreCase(batchData.getEnrollmentType))
            ProjectCommonException.throwClientErrorException(ResponseCode.enrollmentTypeValidation, ResponseCode.enrollmentTypeValidation.getErrorMessage)
        
        if((2 == batchData.getStatus) || (null != batchData.getEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEndDate, dateTimeFormatter).atTime(LocalTime.MAX))))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
        
        if(isEnrol && null != batchData.getEnrollmentEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEnrollmentEndDate, dateTimeFormatter).atTime(LocalTime.MAX)))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)
        
        if(isEnrol && null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
        if(!isEnrol && (null == enrolmentData || !enrolmentData.isActive)) ProjectCommonException.throwClientErrorException(ResponseCode.userNotEnrolledCourse, ResponseCode.userNotEnrolledCourse.getErrorMessage)
        if(!isEnrol && ProjectUtil.ProgressStatus.COMPLETED.getValue == enrolmentData.getStatus) ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
    }

    def upsertEnrollment(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], isNew: Boolean, requestContext: RequestContext): Unit = {
        if(isNew) {
            userCoursesDao.insertV2(requestContext, data)
        } else {
            userCoursesDao.updateV2(requestContext, userId, courseId, batchId, data)
        }
    }

    def createUserEnrolmentMap(userId: String, courseId: String, batchId: String, enrolmentData: UserCourses, requestedBy: String): java.util.Map[String, AnyRef] = 
        new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.USER_ID, userId)
            put(JsonKey.COURSE_ID, courseId)
            put(JsonKey.BATCH_ID, batchId)
            put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue.asInstanceOf[AnyRef])
            if (null == enrolmentData) {
                put(JsonKey.ADDED_BY, requestedBy)
                put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate)
                put(JsonKey.STATUS, ProjectUtil.ProgressStatus.NOT_STARTED.getValue.asInstanceOf[AnyRef])
                put(JsonKey.DATE_TIME, new Timestamp(new Date().getTime))
                put(JsonKey.COURSE_PROGRESS, 0.asInstanceOf[AnyRef])
            }
        }}

    def notifyUser(userId: String, batchData: CourseBatch, operationType: String): Unit = {
        val isNotifyUser = java.lang.Boolean.parseBoolean(PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED))
        if(isNotifyUser){
            val request = new Request()
            request.setOperation(ActorOperations.COURSE_BATCH_NOTIFICATION.getValue)
            request.put(JsonKey.USER_ID, userId)
            request.put(JsonKey.COURSE_BATCH, batchData)
            request.put(JsonKey.OPERATION_TYPE, operationType)
            courseBatchNotificationActorRef.tell(request, getSelf())
        }
    }

    def generateTelemetryAudit(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], correlation: String, state: String, context: java.util.Map[String, AnyRef]): Unit = {
        val contextMap = new java.util.HashMap[String, AnyRef]()
        contextMap.putAll(context)
        contextMap.put(JsonKey.ACTOR_ID, userId)
        contextMap.put(JsonKey.ACTOR_TYPE, "User")
        val targetedObject = TelemetryUtil.generateTargetObject(userId, JsonKey.USER, state, null)
        targetedObject.put(JsonKey.ROLLUP, new java.util.HashMap[String, AnyRef](){{put("l1", courseId)}})
        val correlationObject = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        TelemetryUtil.generateCorrelatedObject(courseId, JsonKey.COURSE, correlation, correlationObject)
        TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, "user.batch", correlationObject)
        val request: java.util.Map[String, AnyRef] = Map[String, AnyRef](JsonKey.USER_ID -> userId, JsonKey.COURSE_ID -> courseId, JsonKey.BATCH_ID -> batchId, JsonKey.COURSE_ENROLL_DATE -> data.get(JsonKey.COURSE_ENROLL_DATE), JsonKey.ACTIVE -> data.get(JsonKey.ACTIVE)).asJava
        TelemetryUtil.telemetryProcessingCall(request, targetedObject, correlationObject, contextMap, "enrol")
    }

    def updateProgressData(enrolments: java.util.List[java.util.Map[String, AnyRef]], userId: String, courseIds: java.util.List[String], requestContext: RequestContext): util.List[java.util.Map[String, AnyRef]] = {
        val enrolmentMap: Map[String, java.util.Map[String, AnyRef]] = enrolments.map(enrolment => enrolment.get(JsonKey.COURSE_ID).asInstanceOf[String] + "_" + enrolment.get(JsonKey.BATCH_ID).asInstanceOf[String] -> enrolment).toMap
        val response: Response = groupDao.readEntries("Course", java.util.Arrays.asList(userId), courseIds, requestContext)
        if (response.getResponseCode != ResponseCode.OK)
            ProjectCommonException.throwServerErrorException(ResponseCode.erroCallGrooupAPI, MessageFormat.format(ResponseCode.erroCallGrooupAPI.getErrorMessage()))
        val userActivityList: util.List[util.Map[String, AnyRef]] = response.getResult.getOrDefault("response", new util.ArrayList()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
        userActivityList.map(activity => {
            val completedCount: Int = activity.getOrDefault("agg", new util.HashMap[String, AnyRef]())
                .asInstanceOf[util.Map[String, AnyRef]].getOrDefault("completedCount", 0.asInstanceOf[AnyRef]).asInstanceOf[Int]
            val key = activity.getOrDefault("activity_id", "").asInstanceOf[String] + "_" + activity.getOrDefault("context_id", "").asInstanceOf[String].replaceAll("cb:", "")
            val enrolment = enrolmentMap.getOrDefault(key, new util.HashMap[String, AnyRef]())
            if(MapUtils.isNotEmpty(enrolment)) {
              val leafNodesCount: Int = enrolment.get("leafNodesCount").asInstanceOf[Int]
              enrolment.put("progress", completedCount.asInstanceOf[AnyRef])
              enrolment.put("status", getCompletionStatus(completedCount, leafNodesCount).asInstanceOf[AnyRef])
              enrolment.put("completionPercentage", getCompletionPerc(completedCount, leafNodesCount).asInstanceOf[AnyRef])
            }
        })
        enrolmentMap.values.toList.asJava
    }

    def getCompletionStatus(completedCount: Int, leafNodesCount: Int): Int = completedCount match {
        case 0 => 0
        case it if 1 until leafNodesCount contains it => 1
        case `leafNodesCount` => 2
        case _ => 2
    }

    def getCompletionPerc(completedCount: Int, leafNodesCount: Int): Int = completedCount match {
        case 0 => 0
        case it if 1 until leafNodesCount contains it => (completedCount * 100) / leafNodesCount
        case `leafNodesCount` => 100
        case _ => 100
    }

    def getCacheKey(userId: String) = s"$userId:user-enrolments"

    def getCachedEnrolmentList(userId: String, handleEmptyCache: () => Response): Response = {
        val key = getCacheKey(userId)
        val responseString = cacheUtil.get(key)
        if (StringUtils.isNotBlank(responseString)) {
            logger.info(null, "CourseEnrolmentActor :: getCachedEnrolmentList :: Entry in redis for key " + key)
            JsonUtil.deserialize(responseString, classOf[Response])
        } else {
            val response = handleEmptyCache()
            val responseString = JsonUtil.serialize(response)
            cacheUtil.set(key, responseString, ttl)
            response
        }
    }

    def getEnrolmentList(request: Request, userId: String): Response = {
        logger.info(request.getRequestContext,"CourseEnrolmentActor :: getCachedEnrolmentList :: fetching data from cassandra with userId " + userId)
        val activeEnrolments: java.util.List[java.util.Map[String, AnyRef]] = getActiveEnrollments( userId, request.getRequestContext)
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = {
            if (CollectionUtils.isNotEmpty(activeEnrolments)) {
              val courseIds: java.util.List[String] = activeEnrolments.map(e => e.getOrDefault(JsonKey.COURSE_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
                val enrolmentList: java.util.List[java.util.Map[String, AnyRef]] = addCourseDetails(activeEnrolments, courseIds, request)
                val updatedEnrolmentList = updateProgressData(enrolmentList, userId, courseIds, request.getRequestContext)
                addBatchDetails(updatedEnrolmentList, request)
            } else new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        }
        val resp: Response = new Response()
        resp.put(JsonKey.COURSES, enrolments)
        resp
    }
    // TODO: to be removed once all are in scala.
    def setDao(courseDao: CourseBatchDao, userDao: UserCoursesDao, groupDao: GroupDaoImpl) = {
        courseBatchDao = courseDao
        userCoursesDao = userDao
        this.groupDao = groupDao
        this
    }


    def getUpdatedStatus(enrolmentData: UserCourses) = {
        val query = "{\"request\": {\"filters\":{\"identifier\": \"" + enrolmentData.getCourseId +"\", \"status\": \"Live\"},\"fields\": [\"leafNodesCount\"],\"limit\": 1}}"
        val result = ContentUtil.searchContent(query, CourseBatchSchedulerUtil.headerMap)
        val contents = result.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val leafNodesCount = {if(CollectionUtils.isNotEmpty(contents)){
            contents.get(0).asInstanceOf[java.util.Map[String, AnyRef]].getOrDefault(JsonKey.LEAF_NODE_COUNT, 0.asInstanceOf[AnyRef]).asInstanceOf[Int]
        } else 0}
        enrolmentData.setStatus(getCompletionStatus(enrolmentData.getProgress, leafNodesCount))
    }

    private def getBatch(requestContext: RequestContext, courseId: String, batchId: String, isFixedBatch: Boolean) = {
        if (isFixedBatch) getFixedBatch(batchId, courseId) else courseBatchDao.readById(courseId, batchId, requestContext)
    }

    //Following default batch allows anyone to join and at any time. TODO: this likely needs a KP lookup to determine enrollment type
    private def getFixedBatch(batchId: String, courseId: String): CourseBatch = {
        val batch = new CourseBatch
        batch.setBatchId(batchId)
        batch.setCourseId(courseId)
        batch.setEnrollmentType(EnrolmentType.open.name())
        batch.setStatus(ProgressStatus.NOT_STARTED.getValue)
        batch.setEnrollmentEndDate(LocalDate.now().plusDays(2).format(dateTimeFormatter))
        batch
    }


}


