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
import org.sunbird.learner.actors.eventAttendance.dao.impl.EventAttendanceDaoImpl
import org.sunbird.learner.actors.eventAttendance.dao.EventAttendanceDao
import org.sunbird.learner.util._
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.models.event.attendance.EventAttendance
import org.sunbird.common.CassandraUtil
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.actors.event.EventContentUtil
import org.sunbird.telemetry.util.TelemetryUtil
import org.sunbird.provider.Provider
import org.sunbird.userorg.UserOrgServiceImpl

import java.sql.Timestamp
import java.text.{DateFormat, MessageFormat, SimpleDateFormat}
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.{Date, List, Map, Optional, UUID}
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
    var eventAttendanceDao: EventAttendanceDao = new EventAttendanceDaoImpl()
    val userCoursesService = new UserCoursesService
    var groupDao: GroupDaoImpl = new GroupDaoImpl()
    private val userOrgService = UserOrgServiceImpl.getInstance
    val isCacheEnabled = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")).toBoolean else true
    val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")).toInt else 60
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateFormatWithTime : DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    val dateFormat : DateFormat = new SimpleDateFormat("yyyy-MM-dd")
    private val mapper = new ObjectMapper


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
        Util.initializeContext(request, TelemetryEnvKey.BATCH, this.getClass.getName)

        request.getOperation match {
            case "enrol" => enroll(request)
            case "unenrol" => unEnroll(request)
            case "listEnrol" => list(request)
            case "getParticipantsForFixedBatch" => fetchParticipantsForFixedBatch(request)
            case "createAttendance" => createAttendance(request)
            case "getAttendance" => getAttendance(request)
            case "getRecording" => getRecording(request)
            case "getCourseSummary" => getCourseSummary(request)
            case "getEventSummary" => getEventSummary(request)
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
        logger.info(requestContext,"CourseEnrolmentActor :: getActiveEnrollments :: UserId = " + userId)
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = userCoursesDao.listEnrolments(requestContext, userId)
        logger.info(requestContext,"CourseEnrolmentActor :: getActiveEnrollments :: enrolments = " + enrolments)
        if (CollectionUtils.isNotEmpty(enrolments))
            enrolments.filter(e => e.getOrDefault(JsonKey.ACTIVE, false.asInstanceOf[AnyRef]).asInstanceOf[Boolean]).toList.asJava
        else
            new util.ArrayList[java.util.Map[String, AnyRef]]()
    }

    def addCourseDetails(activeEnrolments: java.util.List[java.util.Map[String, AnyRef]], courseIds: java.util.List[String] , request:Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val coursesList: java.util.List[java.util.Map[String, AnyRef]] = if (JsonKey.EVENT.equalsIgnoreCase(request.get(JsonKey.CONTENT_TYPE).asInstanceOf[String])) {
            val requestBody: String = prepareSearchRequest(courseIds, request, JsonKey.EVENT)
            val searchResult: java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getRequestContext, request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING, "").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
            searchResult.getOrDefault(JsonKey.EVENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        } else {
            val requestBody: String = prepareSearchRequest(courseIds, request, null)
            val searchResult: java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getRequestContext, request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING, "").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
            searchResult.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        }
        val coursesMap = if (CollectionUtils.isNotEmpty(coursesList)) {
            coursesList.map(ev => ev.get(JsonKey.IDENTIFIER).asInstanceOf[String] -> ev).toMap
        } else courseIds.map(c => c -> new util.HashMap[String, AnyRef]()).toMap
        
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

    def prepareSearchRequest(courseIds: java.util.List[String], request: Request, contentType: String): String = {
        val filters: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.IDENTIFIER, courseIds)
            put(JsonKey.STATUS, "Live")
            put(JsonKey.TRACKABLE_ENABLED, JsonKey.YES)
            if (JsonKey.EVENT.equalsIgnoreCase(contentType)) put(JsonKey.CONTENT_TYPE, JsonKey.EVENT_KEY)
            if (JsonKey.COURSE.equalsIgnoreCase(contentType)) put(JsonKey.CONTENT_TYPE, JsonKey.COURSE_KEY)
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

        if((2 == batchData.getStatus) || (null != batchData.getEndDate && new Date().after(batchData.getEndDate)))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)

        if(isEnrol && null != batchData.getEnrollmentEndDate && new Date().after(batchData.getEnrollmentEndDate))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)
        
        if(isEnrol && null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
        if(!isEnrol && (null == enrolmentData || !enrolmentData.isActive)) ProjectCommonException.throwClientErrorException(ResponseCode.userNotEnrolledCourse, ResponseCode.userNotEnrolledCourse.getErrorMessage)
        if(!isEnrol && ProjectUtil.ProgressStatus.COMPLETED.getValue == enrolmentData.getStatus) ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
    }

    def upsertEnrollment(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], isNew: Boolean, requestContext: RequestContext): Unit = {
        val dataMap = CassandraUtil.changeCassandraColumnMapping(data)
        if(isNew) {
            userCoursesDao.insertV2(requestContext, dataMap)
        } else {
            userCoursesDao.updateV2(requestContext, userId, courseId, batchId, dataMap)
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
                put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getTimeStamp)
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
        val request: java.util.Map[String, AnyRef] = scala.collection.immutable.Map[String, AnyRef](JsonKey.USER_ID -> userId, JsonKey.COURSE_ID -> courseId, JsonKey.BATCH_ID -> batchId, JsonKey.COURSE_ENROLL_DATE -> data.get(JsonKey.COURSE_ENROLL_DATE), JsonKey.ACTIVE -> data.get(JsonKey.ACTIVE)).asJava
        TelemetryUtil.telemetryProcessingCall(request, targetedObject, correlationObject, contextMap, "enrol")
    }

    def updateProgressData(enrolments: java.util.List[java.util.Map[String, AnyRef]], userId: String, courseIds: java.util.List[String], requestContext: RequestContext): util.List[java.util.Map[String, AnyRef]] = {
        enrolments.map(enrolment => {
            val leafNodesCount: Int = enrolment.getOrDefault("leafNodesCount", 0.asInstanceOf[AnyRef]).asInstanceOf[Int]
            val progress: Int = enrolment.getOrDefault("progress", 0.asInstanceOf[AnyRef]).asInstanceOf[Int]
            enrolment.put("status", getCompletionStatus(progress, leafNodesCount).asInstanceOf[AnyRef])
            enrolment.put("completionPercentage", getCompletionPerc(progress, leafNodesCount).asInstanceOf[AnyRef])
        })
        enrolments
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
        logger.info(request.getRequestContext,"CourseEnrolmentActor :: getEnrolmentList :: activeEnrolments = " + activeEnrolments)
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = {
            if (CollectionUtils.isNotEmpty(activeEnrolments)) {
              val courseIds: java.util.List[String] = activeEnrolments.map(e => e.getOrDefault(JsonKey.COURSE_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
                logger.info(request.getRequestContext,"CourseEnrolmentActor :: getEnrolmentList :: courseIds = " + courseIds)
                val enrolmentList: java.util.List[java.util.Map[String, AnyRef]] = addCourseDetails(activeEnrolments, courseIds, request)
                logger.info(request.getRequestContext,"CourseEnrolmentActor :: getEnrolmentList :: enrolmentList = " + enrolmentList)
                val updatedEnrolmentList = updateProgressData(enrolmentList, userId, courseIds, request.getRequestContext)
                logger.info(request.getRequestContext,"CourseEnrolmentActor :: getEnrolmentList :: updatedEnrolmentList = " + updatedEnrolmentList)
                addBatchDetails(updatedEnrolmentList, request)
            } else new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        }
        logger.info(request.getRequestContext,"CourseEnrolmentActor :: getEnrolmentList :: enrolments = " + enrolments)
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
        batch.setEnrollmentEndDate(LocalDate.now().plusDays(2).format(dateTimeFormatter).asInstanceOf[Date])
        batch
    }

    /**
     * Creates the attendance of the users enrolled in provided event and batch
     *
     * @param request the request
     */
    def createAttendance(request: Request): Unit = {
        val eventAttendanceInfo: util.Map[String, Any] = Provider.getAttendanceInfo(request)
        if (MapUtils.isNotEmpty(eventAttendanceInfo)) {
            val eventId = eventAttendanceInfo.get(JsonKey.EVENT_ID).asInstanceOf[String]
            val userId = eventAttendanceInfo.get(JsonKey.USER_ID).asInstanceOf[String]
            val joinedDateTimeStr = eventAttendanceInfo.get(JsonKey.JOINED_DATE_TIME).asInstanceOf[String]
            val leftDateTimeStr = eventAttendanceInfo.get(JsonKey.LEFT_DATE_TIME).asInstanceOf[String]
            val userCourse: UserCourses = if (null != userId && null != eventId) userCoursesDao.read(eventId, request.getRequestContext, userId) else null
            if (null != userCourse) {
                val batchId = userCourse.getBatchId
                // Set data to event attendance
                if (null != joinedDateTimeStr && JsonKey.ONLINE_PROVIDER_EVENT_USER_JOINED.equalsIgnoreCase(eventAttendanceInfo.get(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT).asInstanceOf[String])) { // Last Joined
                    val eventAttendance: EventAttendance = new EventAttendance
                    eventAttendance.setUserId(userId)
                    eventAttendance.setContentId(eventId)
                    eventAttendance.setBatchId(batchId)
                    val joinedDateTime: java.util.Date = stringToDateConverter(joinedDateTimeStr)
                    eventAttendance.setJoinedDateTime(joinedDateTime)
                    // Update user enroll in first joined
                    val enrolmentData: UserCourses = userCoursesDao.read(request.getRequestContext, userId, eventId, batchId)
                    val enrolmentDataMap: java.util.Map[String, AnyRef] = createUserEnrolmentMap(userId, eventId, batchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
                    enrolmentDataMap.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.COMPLETED.getValue.asInstanceOf[AnyRef])
                    enrolmentDataMap.put(JsonKey.COURSE_PROGRESS, 2.asInstanceOf[AnyRef]) // 2 : Attended
                    upsertEnrollment(userId, eventId, batchId, enrolmentDataMap, false, request.getRequestContext)
                    eventAttendance.setProvider(request.get(JsonKey.ONLINE_PROVIDER).asInstanceOf[String])
                    eventAttendance.setRole(eventAttendanceInfo.get(JsonKey.ROLE).asInstanceOf[String])
                    eventAttendance.setId(UUID.randomUUID())
                    upsertEventAttendance(eventAttendance, true, request.getRequestContext)
                }
                if (null != leftDateTimeStr && JsonKey.ONLINE_PROVIDER_EVENT_USER_LEFT.equalsIgnoreCase(eventAttendanceInfo.get(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT).asInstanceOf[String])) { // Last Left
                    val eventAttendanceResponseList: List[EventAttendance] = eventAttendanceDao.readById(request.getRequestContext, eventId, batchId, userId)
                    if (CollectionUtils.isNotEmpty(eventAttendanceResponseList)) {
                        if (eventAttendanceResponseList.exists(ea => null == ea.getLeftDateTime || ea.getLeftDateTime.before(ea.getJoinedDateTime))) {
                            val eventAttendanceResponse: EventAttendance = eventAttendanceResponseList.filter(ea => null == ea.getLeftDateTime || ea.getLeftDateTime.before(ea.getJoinedDateTime)).get(0)
                            if (null != eventAttendanceResponse) calculateDurationAndUpdate(eventAttendanceResponse, leftDateTimeStr, request.getRequestContext)
                        }
                    }
                }
            }
            if (null != leftDateTimeStr && JsonKey.ONLINE_PROVIDER_EVENT_MEETING_ENDED.equalsIgnoreCase(eventAttendanceInfo.get(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT).asInstanceOf[String])) { // Meeting Ended
                val eventAttendanceResponseList: List[EventAttendance] = eventAttendanceDao.readById(request.getRequestContext, eventId, null, null)
                if (CollectionUtils.isNotEmpty(eventAttendanceResponseList)) {
                    eventAttendanceResponseList.filter(ea => null == ea.getLeftDateTime || ea.getLeftDateTime.before(ea.getJoinedDateTime)).foreach {
                        eventAttendanceResponse => calculateDurationAndUpdate(eventAttendanceResponse, leftDateTimeStr, request.getRequestContext)
                    }
                }
            }
        }
        sender().tell(successResponse(), self)
    }

    /**
     * Calculates duration and updates the Event attendance
     *
     * @param eventAttendanceResponse the Event Attendance response
     * @param leftDateTimeStr         the left date and time string
     * @param requestContext          the requestContext
     */
    private def calculateDurationAndUpdate(eventAttendanceResponse: EventAttendance, leftDateTimeStr: String, requestContext: RequestContext): Unit = {
        val joinedDateTime = eventAttendanceResponse.getJoinedDateTime
        val leftDateTime = stringToDateConverter(leftDateTimeStr)
        eventAttendanceResponse.setLeftDateTime(leftDateTime)
        eventAttendanceResponse.setDuration(calculateDuration(joinedDateTime, leftDateTime))
        upsertEventAttendance(eventAttendanceResponse, false, requestContext)
    }

    /**
     * Calculates duration or difference between two given dates in seconds
     *
     * @param joinedDateTime the joined date time
     * @param leftDateTime the left date time
     * @return the duration
     */
    private def calculateDuration(joinedDateTime : Date, leftDateTime : Date): Long = {
        val duration = leftDateTime.getTime - joinedDateTime.getTime
        TimeUnit.MILLISECONDS.toSeconds(duration)
    }

    /**
     * Inserts or updates Event Attendance
     *
     * @param eventAttendance the event attendance
     * @param isNew           is event attendace new
     * @param requestContext  the request context
     */
    private def upsertEventAttendance(eventAttendance: EventAttendance, isNew: Boolean, requestContext: RequestContext): Unit = {
        var eventAttendanceMap = createEventAttendanceMap(eventAttendance)
        logger.info(requestContext, "CourseEnrolmentActor::createAttendance::eventAttendanceMap : " + eventAttendanceMap)
        eventAttendanceMap = CassandraUtil.changeCassandraColumnMapping(eventAttendanceMap)
        if (isNew) {
            eventAttendanceDao.create(requestContext, eventAttendanceMap)
        } else {
            eventAttendanceDao.update(requestContext, eventAttendance.getContentId, eventAttendance.getBatchId, eventAttendance.getUserId, eventAttendance.getId, eventAttendanceMap)
        }
    }

    /**
     * Creates Event Attendance map from EventAttendance object
     *
     * @param eventAttendance the event attendance
     * @return Event Attendance map
     */
    private def createEventAttendanceMap(eventAttendance: EventAttendance): java.util.Map[String, AnyRef] =
        new java.util.HashMap[String, AnyRef]() {
            {
                put(JsonKey.ID, eventAttendance.getId)
                put(JsonKey.USER_ID, eventAttendance.getUserId)
                put(JsonKey.CONTENT_ID, eventAttendance.getContentId)
                put(JsonKey.BATCH_ID, eventAttendance.getBatchId)
                if (null != eventAttendance) {
                    if (null != eventAttendance.getRole) put(JsonKey.ROLE, eventAttendance.getRole)
                    if (null != eventAttendance.getJoinedDateTime) put(JsonKey.JOINED_DATE_TIME, new Timestamp(eventAttendance.getJoinedDateTime.getTime))
                    if (null != eventAttendance.getLeftDateTime) put(JsonKey.LEFT_DATE_TIME, new Timestamp(eventAttendance.getLeftDateTime.getTime))
                    if (null != eventAttendance.getDuration) put(JsonKey.DURATION, eventAttendance.getDuration)
                    if (null != eventAttendance.getProvider) put(JsonKey.PROVIDER, eventAttendance.getProvider)
                }
            }
        }

    /**
     * Converts String to Date object.
     *
     * @param dateString the date in String format.
     * @return Date in java.util.Date format.
     */
    private def stringToDateConverter(dateString: String): java.util.Date = {
        dateFormatWithTime.parse(dateString)
    }

    /**
     * Gets the attendance of the users enrolled in provided event and batch
     *
     * @param request the request
     */
    def getAttendance(request: Request): Unit = {
        val contentId: String = request.get(JsonKey.CONTENT_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val userCourses: util.List[UserCourses] = userCoursesDao.read(contentId, batchId, request.getRequestContext)
        val activeUserCourses: util.List[UserCourses] = if (CollectionUtils.isNotEmpty(userCourses))
            userCourses.filter(userCourse => userCourse.isActive).toList.asJava
        else
            new util.ArrayList[UserCourses]()
        val userIds: List[String] = activeUserCourses.map { el => el.getUserId }.toList
        logger.info(request.getRequestContext, "CourseEnrolmentActor::getAttendance::userIds : " + userIds)
        val eventAttendanceMapList = new util.ArrayList[java.util.Map[String, Any]]()
        if (CollectionUtils.isNotEmpty(userIds)) {
            val userDetails: List[Map[String, Object]] = userOrgService.getUsersByIds(userIds)
            if (CollectionUtils.isNotEmpty(userDetails)) {
                userDetails.foreach { userDetail =>
                    val eventAttendanceMap = new util.HashMap[String, Any]
                    val userId = userDetail.get(JsonKey.USER_ID).asInstanceOf[String]
                    getUserData(userDetail, eventAttendanceMap)
                    getAttendanceData(contentId, batchId, userId, request.getRequestContext, eventAttendanceMap)
                    getUserEnrolmentData(userId, userCourses, eventAttendanceMap)
                    eventAttendanceMapList.add(eventAttendanceMap)
                }
            }
        }
        val response: Response = new Response()
        response.put(JsonKey.COUNT, eventAttendanceMapList.size.asInstanceOf[Integer])
        response.put(JsonKey.CONTENT, eventAttendanceMapList)
        sender().tell(response, self)
    }

    /**
     * Gets the user data
     *
     * @param userDetail         user meta data
     * @param eventAttendanceMap event attendance map
     * @return Event attendance map with the user details set
     */
    private def getUserData(userDetail: Map[String, Object], eventAttendanceMap: java.util.Map[String, Any]): java.util.Map[String, Any] = {
        eventAttendanceMap.put(JsonKey.USER_ID, userDetail.get(JsonKey.USER_ID).asInstanceOf[String])
        eventAttendanceMap.put(JsonKey.FULL_NAME, userDetail.getOrDefault(JsonKey.FIRST_NAME, "").asInstanceOf[String].concat(" ").concat(userDetail.getOrDefault(JsonKey.LAST_NAME, "").asInstanceOf[String]))
        eventAttendanceMap.put(JsonKey.EMAIL, userDetail.getOrDefault(JsonKey.EMAIL, "").asInstanceOf[String])
        eventAttendanceMap
    }

    /**
     * Gets the event attendance details
     *
     * @param contentId          the content id
     * @param batchId            the batch id
     * @param userId             the user id
     * @param requestContext     the request context
     * @param eventAttendanceMap the event attendance map
     * @return The event attendance map
     */
    private def getAttendanceData(contentId: String, batchId: String, userId: String, requestContext: RequestContext, eventAttendanceMap: java.util.Map[String, Any]): java.util.Map[String, Any] = {
        val eventAttendanceResponseList: List[EventAttendance] = eventAttendanceDao.readById(requestContext, contentId, batchId, userId)
        if (CollectionUtils.isNotEmpty(eventAttendanceResponseList)) {
            val leftJoinedHistoryList: util.List[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
            eventAttendanceResponseList.foreach { ea =>
                val leftJoinedHistory = new util.HashMap[String, Any]
                leftJoinedHistory.put(JsonKey.JOINED_DATE_TIME, ea.getJoinedDateTime)
                leftJoinedHistory.put(JsonKey.LEFT_DATE_TIME, ea.getLeftDateTime)
                leftJoinedHistory.put(JsonKey.DURATION, ea.getDuration)
                leftJoinedHistoryList.add(leftJoinedHistory)
            }
            mapper.setDateFormat(dateFormatWithTime)
            eventAttendanceMap.put(JsonKey.JOINED_LEFT_HISTORY, mapper.convertValue(leftJoinedHistoryList, classOf[util.List[util.Map[String, Object]]]))
            val eventAttendance = eventAttendanceResponseList.get(0)
            val totalDuration: Long = eventAttendanceResponseList.filter(ea => null != ea.getDuration && ea.getJoinedDateTime.before(ea.getLeftDateTime)).foldLeft(0L)((totalDuration, ea) => ea.getDuration + totalDuration)
            val joinedDateTimeList: java.util.List[Date] = eventAttendanceResponseList.filter(ea => null != ea.getJoinedDateTime).map(ea => ea.getJoinedDateTime).toList.asJava
            val joinedDateTime = if(CollectionUtils.isNotEmpty(joinedDateTimeList)) joinedDateTimeList.min else null
            val leftDateTimeList: java.util.List[Date] = eventAttendanceResponseList.filter(ea => null != ea.getLeftDateTime && ea.getJoinedDateTime.before(ea.getLeftDateTime)).map(ea => ea.getLeftDateTime).toList.asJava
            val leftDateTime = if (CollectionUtils.isNotEmpty(leftDateTimeList)) leftDateTimeList.max else null
            eventAttendance.setDuration(totalDuration)
            eventAttendance.setJoinedDateTime(joinedDateTime)
            eventAttendance.setLeftDateTime(leftDateTime)
            eventAttendanceMap.putAll(mapper.convertValue(eventAttendance, classOf[util.Map[String, Object]]))
        }
        eventAttendanceMap
    }

    /**
     * Gets the user enrolment data
     *
     * @param userId             the user id
     * @param userCourses        the list of user courses
     * @param eventAttendanceMap the event attendance map
     * @return The event attendance map
     */
    private def getUserEnrolmentData(userId: String, userCourses: util.List[UserCourses], eventAttendanceMap: java.util.Map[String, Any]): java.util.Map[String, Any] = {
        if (userCourses.exists(userCourse => userId == userCourse.getUserId)) {
            val userCourse: UserCourses = userCourses.filter(userCourses => userId == userCourses.getUserId).get(0)
            eventAttendanceMap.put(JsonKey.ENROLLED_DATE, dateFormat.format(userCourse.getEnrolledDate))
            eventAttendanceMap.put(JsonKey.STATUS, userCourse.getStatus)
        }
        eventAttendanceMap
    }

    /**
     * Gets the recording of the provided event
     *
     * @param request the request
     */
    def getRecording(request: Request): Unit = {
        val recordingInfo: util.Map[String, Any] = Provider.getRecordingInfo(request)
        if (MapUtils.isNotEmpty(recordingInfo)) {
            val eventId = recordingInfo.get(JsonKey.EVENT_ID).asInstanceOf[String]
            val event: util.Map[String, AnyRef] = EventContentUtil.readEvent(request, eventId)
            if (MapUtils.isNotEmpty(event)) {
                val onlineProviderData = event.get(JsonKey.ONLINE_PROVIDER_DATA).asInstanceOf[util.Map[String, AnyRef]]
                val existingRecordingUrlList = if (MapUtils.isNotEmpty(onlineProviderData)) onlineProviderData.get(JsonKey.RECORDINGS).asInstanceOf[util.List[util.Map[String, Any]]] else null
                if (CollectionUtils.isNotEmpty(existingRecordingUrlList)) {
                    existingRecordingUrlList.add(recordingInfo.get(JsonKey.RECORDING).asInstanceOf[util.Map[String, Any]])
                } else {
                    val newRecordingUrlList: util.List[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
                    newRecordingUrlList.add(recordingInfo.get(JsonKey.RECORDING).asInstanceOf[util.Map[String, Any]])
                    onlineProviderData.put(JsonKey.RECORDINGS, newRecordingUrlList)
                }
                logger.info(request.getRequestContext, "CourseEnrolmentActor::getRecording::eventRequest : " + event)
                event.remove("status")
                val response = EventContentUtil.postContent(request, SunbirdKey.CONTENT, "/content/v4/system/update/{identifier}", event, JsonKey.IDENTIFIER, eventId)
                logger.info(request.getRequestContext, "CourseEnrolmentActor::getRecording::eventResponse : " + response)
                if (null != response) {
                    if (response.getResponseCode.getResponseCode == ResponseCode.OK.getResponseCode) sender.tell(successResponse(), self)
                    else {
                        val message = formErrorDetailsMessage(response, "Event update failed ")
                        logger.info(request.getRequestContext, s"${ResponseCode.customServerError} : ${message}")
                    }
                }
                else {
                    logger.info(request.getRequestContext, ResponseCode.CLIENT_ERROR.name())
                }
            }
        }
        sender().tell(successResponse(), self)
    }

    /**
     * Forms the error details message
     *
     * @param response the response
     * @param message  the message string
     * @return the message string
     */
    private def formErrorDetailsMessage(response: Response, message: String): String = {
        val resultMap = Optional.ofNullable(response.getResult).orElse(new util.HashMap[String, AnyRef])
        if (MapUtils.isNotEmpty(resultMap)) {
            val obj = Optional.ofNullable(resultMap.get(SunbirdKey.TB_MESSAGES)).orElse("")
            return if (obj.isInstanceOf[util.List[_]]) message.concat(obj.asInstanceOf[List[String]].stream.collect(Collectors.joining(";")))
            else if (StringUtils.isNotEmpty(response.getParams.getErrmsg)) message.concat(response.getParams.getErrmsg)
            else message.concat(String.valueOf(obj))
        }
        message
    }

    /**
     * Gets the Course summary
     *
     * @param request the request
     */
    def getCourseSummary(request: Request): Unit = {
        val requestBody: String = prepareSearchRequest(new util.ArrayList[String](), request, JsonKey.COURSE)
        val searchResult: java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getRequestContext, request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING, "").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
        val coursesList: java.util.List[java.util.Map[String, AnyRef]] = searchResult.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val courseSummaryResponseList: util.List[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
        if (CollectionUtils.isNotEmpty(coursesList)) {
            coursesList.foreach { course =>
                val courseSummaryResponse: java.util.Map[String, Any] = new java.util.HashMap[String, Any]()
                val courseId = course.get(JsonKey.IDENTIFIER).asInstanceOf[String]
                courseSummaryResponse.put(JsonKey.IDENTIFIER, courseId)
                courseSummaryResponse.put(JsonKey.NAME, course.get(JsonKey.NAME).asInstanceOf[String])
                courseSummaryResponse.put(JsonKey.SE_BOARDS, course.get(JsonKey.SE_BOARDS).asInstanceOf[util.List[String]])
                courseSummaryResponse.put(JsonKey.SE_GRADE_LEVELS, course.get(JsonKey.SE_GRADE_LEVELS).asInstanceOf[util.List[String]])
                courseSummaryResponse.put(JsonKey.SE_MEDIUMS, course.get(JsonKey.SE_MEDIUMS).asInstanceOf[util.List[String]])
                courseSummaryResponse.put(JsonKey.SE_SUBJECTS, course.get(JsonKey.SE_SUBJECTS).asInstanceOf[util.List[String]])
                courseSummaryResponse.put(JsonKey.PRIMARY_CATEGORY, course.get(JsonKey.PRIMARY_CATEGORY).asInstanceOf[String])
                val batches: java.util.List[java.util.Map[String, Any]] = course.get(JsonKey.BATCHES).asInstanceOf[util.List[java.util.Map[String, Any]]]
                if (CollectionUtils.isNotEmpty(batches)) {
                    batches.foreach { batch =>
                        val batchId = batch.get(JsonKey.BATCH_ID).asInstanceOf[String]
                        courseSummaryResponse.put(JsonKey.BATCH_ID, batchId)
                        courseSummaryResponse.put(JsonKey.NAME, batch.get(JsonKey.NAME).asInstanceOf[String])
                        courseSummaryResponse.put(JsonKey.START_DATE, batch.get(JsonKey.START_DATE).asInstanceOf[String])
                        courseSummaryResponse.put(JsonKey.END_DATE, batch.get(JsonKey.END_DATE).asInstanceOf[String])
                        if (null != courseId && null != batchId) {
                            val userCourses: util.List[UserCourses] = userCoursesDao.read(courseId, batchId, request.getRequestContext)
                            val activeUserCourses: util.List[UserCourses] = if (CollectionUtils.isNotEmpty(userCourses))
                                userCourses.filter(userCourse => userCourse.isActive).toList.asJava
                            else {
                                new util.ArrayList[UserCourses]()
                            }
                            courseSummaryResponse.put(JsonKey.TOTAL_ENROLLED, activeUserCourses.size())
                            courseSummaryResponse.put(JsonKey.TOTAL_COMPLETED, activeUserCourses.filter(activeUserCourse => ProjectUtil.ProgressStatus.COMPLETED.getValue == activeUserCourse.getStatus).toList.asJava.size())
                        }
                        courseSummaryResponseList.add(courseSummaryResponse)
                    }
                } else courseSummaryResponseList.add(courseSummaryResponse)
            }
        }
        val response: Response = new Response()
        response.put(JsonKey.COUNT, courseSummaryResponseList.size.asInstanceOf[Integer])
        response.put(JsonKey.CONTENT, courseSummaryResponseList)
        sender().tell(response, self)
    }

    /**
     * Gets the Event summary
     *
     * @param request the request
     */
    def getEventSummary(request: Request): Unit = {
        val requestBody: String = prepareSearchRequest(new util.ArrayList[String](), request, JsonKey.EVENT)
        val searchResult: java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getRequestContext, request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING, "").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
        val eventsList: java.util.List[java.util.Map[String, AnyRef]] = searchResult.getOrDefault(JsonKey.EVENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val eventSummaryResponseList: util.List[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
        if (CollectionUtils.isNotEmpty(eventsList)) {
            eventsList.foreach { event =>
                val eventSummaryResponse: java.util.Map[String, Any] = new java.util.HashMap[String, Any]()
                val eventId = event.get(JsonKey.IDENTIFIER).asInstanceOf[String]
                eventSummaryResponse.put(JsonKey.IDENTIFIER, eventId)
                eventSummaryResponse.put(JsonKey.NAME, event.get(JsonKey.NAME).asInstanceOf[String])
                eventSummaryResponse.put(JsonKey.BOARD, event.get(JsonKey.BOARD).asInstanceOf[String])
                eventSummaryResponse.put(JsonKey.GRADE_LEVEL, event.get(JsonKey.GRADE_LEVEL).asInstanceOf[String])
                eventSummaryResponse.put(JsonKey.MEDIUM, event.get(JsonKey.MEDIUM).asInstanceOf[String])
                eventSummaryResponse.put(JsonKey.SUBJECT, event.get(JsonKey.SUBJECT).asInstanceOf[String])
                eventSummaryResponse.put(JsonKey.CATEGORY, event.get(JsonKey.PRIMARY_CATEGORY).asInstanceOf[util.List[String]])
                val batches: util.List[java.util.Map[String, AnyRef]] = courseBatchDao.readById(eventId, request.getRequestContext)
                if (CollectionUtils.isNotEmpty(batches)) {
                    batches.foreach { batch =>
                        val batchId = batch.get(JsonKey.BATCH_ID).asInstanceOf[String]
                        eventSummaryResponse.put(JsonKey.BATCH_ID, batchId)
                        eventSummaryResponse.put(JsonKey.NAME, batch.get(JsonKey.NAME).asInstanceOf[String])
                        eventSummaryResponse.put(JsonKey.START_DATE, if (null != batch.get(JsonKey.START_DATE).asInstanceOf[Date]) dateFormat.format(batch.get(JsonKey.START_DATE).asInstanceOf[Date]) else null)
                        eventSummaryResponse.put(JsonKey.END_DATE, if (null != batch.get(JsonKey.END_DATE).asInstanceOf[Date]) dateFormat.format(batch.get(JsonKey.END_DATE).asInstanceOf[Date]) else null)
                        if (null != eventId && null != batchId) {
                            val userCourses: util.List[UserCourses] = userCoursesDao.read(eventId, batchId, request.getRequestContext)
                            val activeUserCourses: util.List[UserCourses] = if (CollectionUtils.isNotEmpty(userCourses))
                                userCourses.filter(userCourse => userCourse.isActive).toList.asJava
                            else {
                                new util.ArrayList[UserCourses]()
                            }
                            eventSummaryResponse.put(JsonKey.TOTAL_ENROLLED, activeUserCourses.size())
                            eventSummaryResponse.put(JsonKey.TOTAL_COMPLETED, activeUserCourses.filter(activeUserCourse => ProjectUtil.ProgressStatus.COMPLETED.getValue == activeUserCourse.getStatus).toList.asJava.size())
                        }
                        eventSummaryResponseList.add(eventSummaryResponse)
                    }
                } else eventSummaryResponseList.add(eventSummaryResponse)
            }
        }
        val response: Response = new Response()
        response.put(JsonKey.COUNT, eventSummaryResponseList.size.asInstanceOf[Integer])
        response.put(JsonKey.CONTENT, eventSummaryResponseList)
        sender().tell(response, self)
    }
}


