package org.sunbird.enrolments

import java.sql.Timestamp
import java.text.MessageFormat
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import java.util
import java.util.Date

import akka.actor.ActorRef
import com.fasterxml.jackson.databind.ObjectMapper
import javax.inject.{Inject, Named}
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.CacheFactory
import org.sunbird.cache.interfaces.Cache
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil.EnrolmentType
import org.sunbird.common.models.util._
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.learner.actors.coursebatch.dao.{CourseBatchDao, UserCoursesDao}
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.learner.util.{ContentSearchUtil, Util}
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.redis.RedisCache
import org.sunbird.telemetry.util.TelemetryUtil

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class CourseEnrolmentActor @Inject()(@Named("course-batch-notification-actor") courseBatchNotificationActorRef: ActorRef) extends BaseEnrolmentActor {

    /*
    The below variables are kept as var on testcase purpose.
    TODO: once all are moved to scala, this can be made as parameterised constructor
     */
    var courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()
    var userCoursesDao: UserCoursesDao = new UserCoursesDaoImpl()
    var groupDao: GroupDaoImpl = new GroupDaoImpl()

    val isCacheEnabled = if(StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_enable")).toBoolean else true
    val ttl: Long = if(StringUtils.isNotBlank(ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")))
        (ProjectUtil.getConfigValue("user_enrolments_response_cache_ttl")).toLong else 60
    var redisCache: Cache = CacheFactory.getInstance()
    val mapper: ObjectMapper = new ObjectMapper()



    override def onReceive(request: Request): Unit = {
        Util.initializeContext(request, TelemetryEnvKey.BATCH)
        request.getOperation match {
            case "enrol" => enroll(request)
            case "unenrol" => unEnroll(request)
            case "listEnrol" => list(request)
            case _ => ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData,
                ResponseCode.invalidRequestData.getErrorMessage)
        }
    }

    def enroll(request: Request): Unit = {
        val courseId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val batchData: CourseBatch = courseBatchDao.readById(courseId, batchId)
        val enrolmentData: UserCourses = userCoursesDao.read(userId, courseId, batchId)
        validateEnrolment(batchData, enrolmentData, true)
        val data: java.util.Map[String, AnyRef] = createUserEnrolmentMap(userId, courseId, batchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
        upsertEnrollment(userId, courseId, batchId, data, (null == enrolmentData))
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, batchData, "user.enrol", JsonKey.CREATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.ADD)
    }


    def unEnroll(request: Request): Unit = {
        val courseId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val batchData: CourseBatch = courseBatchDao.readById(courseId, batchId)
        val enrolmentData: UserCourses = userCoursesDao.read(userId, courseId, batchId)
        validateEnrolment(batchData, enrolmentData, false)
        val data: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {
            {
                put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue.asInstanceOf[AnyRef])
            }
        }
        upsertEnrollment(userId, courseId, batchId, data, false)
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, batchData, "user.unenrol", JsonKey.UPDATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.REMOVE)
    }

    def list(request: Request): Unit = {
        val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val response = if (isCacheEnabled && StringUtils.equalsIgnoreCase(request.getContext.get("queryParams")
            .asInstanceOf[util.Map[String, AnyRef]].get("cache").asInstanceOf[String], "false"))
            redisCache.get("user-enrolments", getCacheKey(userId))
        else {
            val activeEnrolments: java.util.List[java.util.Map[String, AnyRef]] = getActiveEnrollments(userId)
            val enrolments: java.util.List[java.util.Map[String, AnyRef]] = {
                if (CollectionUtils.isNotEmpty(activeEnrolments)) {
                    val enrolmentList: java.util.List[java.util.Map[String, AnyRef]] = addCourseDetails(activeEnrolments, request)
                    updateProgressData(enrolmentList, userId)
                    addBatchDetails(enrolmentList, request)
                } else new java.util.ArrayList[java.util.Map[String, AnyRef]]()
            }
            val resp: Response = new Response()
            resp.put(JsonKey.COURSES, enrolments)
            if (isCacheEnabled)
                setResponseToRedis(getCacheKey(userId), resp)
            resp
        }
        sender().tell(response, self)
    }

    def getActiveEnrollments(userId: String): java.util.List[java.util.Map[String, AnyRef]] = {
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = userCoursesDao.listEnrolments(userId)
        if (CollectionUtils.isNotEmpty(enrolments))
            enrolments.filter(e => e.getOrDefault(JsonKey.ACTIVE, false.asInstanceOf[AnyRef]).asInstanceOf[Boolean]).toList.asJava
        else
            new util.ArrayList[java.util.Map[String, AnyRef]]()
    }

    def addCourseDetails(activeEnrolments: java.util.List[java.util.Map[String, AnyRef]], request: Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val courseIds: java.util.List[String] = activeEnrolments.map(e => e.getOrDefault(JsonKey.COURSE_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
        val requestBody: String = prepareSearchRequest(courseIds, request)
        val searchResult: java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING, "").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
        val coursesList: java.util.List[java.util.Map[String, AnyRef]] = searchResult.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val coursesMap = {
            if (CollectionUtils.isNotEmpty(coursesList)) {
                coursesList.map(ev => ev.get(JsonKey.IDENTIFIER).asInstanceOf[String] -> ev).toMap
            } else Map()
        }

        activeEnrolments.filter(enrolment => coursesMap.containsKey(enrolment.get(JsonKey.COURSE_ID))).map(enrolment => {
            val courseContent = coursesMap.get(enrolment.get(JsonKey.COURSE_ID))
            enrolment.put(JsonKey.COURSE_NAME, courseContent.get(JsonKey.NAME))
            enrolment.put(JsonKey.DESCRIPTION, courseContent.get(JsonKey.DESCRIPTION))
            enrolment.put(JsonKey.LEAF_NODE_COUNT, courseContent.get(JsonKey.LEAF_NODE_COUNT))
            enrolment.put(JsonKey.COURSE_LOGO_URL, courseContent.get(JsonKey.APP_ICON))
            enrolment.put(JsonKey.CONTENT_ID, enrolment.get(JsonKey.COURSE_ID))
            enrolment.put(JsonKey.CONTENT, courseContent)
            enrolment
        }).toList.asJava
    }

    def prepareSearchRequest(courseIds: java.util.List[String], request: Request): String = {
        val filters: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {
            {
                put(JsonKey.IDENTIFIER, courseIds)
                put(JsonKey.CONTENT_TYPE, Array(JsonKey.COURSE))
                put(JsonKey.STATUS, "Live")
                putAll(request.getRequest.getOrDefault(JsonKey.FILTERS, new java.util.HashMap[String, AnyRef]).asInstanceOf[java.util.Map[String, AnyRef]])
            }
        }
        val searchRequest: java.util.Map[String, java.util.Map[String, AnyRef]] = new java.util.HashMap[String, java.util.Map[String, AnyRef]]() {
            {
                put(JsonKey.REQUEST, new java.util.HashMap[String, AnyRef]() {
                    {
                        put(JsonKey.FILTERS, filters)
                        put(JsonKey.LIMIT, courseIds.size().asInstanceOf[AnyRef])
                    }
                })
            }
        }
        new ObjectMapper().writeValueAsString(searchRequest)
    }

    def addBatchDetails(enrolmentList: util.List[util.Map[String, AnyRef]], request: Request): util.List[util.Map[String, AnyRef]] = {
        val batchIds: java.util.List[String] = enrolmentList.map(e => e.getOrDefault(JsonKey.BATCH_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
        val batchDetails = searchBatchDetails(batchIds, request)
        if (CollectionUtils.isNotEmpty(batchDetails)) {
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
        val requestedFields: java.util.List[String] = if (null != request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[Array[String]]) request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[Array[String]](0).split(",").toList.asJava else new java.util.ArrayList[String]()
        if (CollectionUtils.isNotEmpty(requestedFields)) {
            val fields = new java.util.ArrayList[String]()
            fields.addAll(requestedFields)
            fields.add(JsonKey.BATCH_ID)
            fields.add(JsonKey.IDENTIFIER)
            getBatches(new java.util.ArrayList[String](batchIds), fields)
        } else {
            new java.util.ArrayList[util.Map[String, AnyRef]]()
        }
    }


    def validateEnrolment(batchData: CourseBatch, enrolmentData: UserCourses, isEnrol: Boolean): Unit = {
        if (null == batchData) ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage)

        if (EnrolmentType.inviteOnly.getVal.equalsIgnoreCase(batchData.getEnrollmentType))
            ProjectCommonException.throwClientErrorException(ResponseCode.enrollmentTypeValidation, ResponseCode.enrollmentTypeValidation.getErrorMessage)

        if ((2 == batchData.getStatus) || (null != batchData.getEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay())))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)

        if (isEnrol && null != batchData.getEnrollmentEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEnrollmentEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)

        if (isEnrol && null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
        if (!isEnrol && (null == enrolmentData || !enrolmentData.isActive)) ProjectCommonException.throwClientErrorException(ResponseCode.userNotEnrolledCourse, ResponseCode.userNotEnrolledCourse.getErrorMessage)
        if (!isEnrol && ProjectUtil.ProgressStatus.COMPLETED.getValue == enrolmentData.getStatus) ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
    }

    def upsertEnrollment(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], isNew: Boolean): Unit = {
        if (isNew) {
            userCoursesDao.insertV2(data)
        } else {
            userCoursesDao.updateV2(userId, courseId, batchId, data)
        }
    }

    def createUserEnrolmentMap(userId: String, courseId: String, batchId: String, enrolmentData: UserCourses, requestedBy: String): java.util.Map[String, AnyRef] =
        new java.util.HashMap[String, AnyRef]() {
            {
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
            }
        }

    def notifyUser(userId: String, batchData: CourseBatch, operationType: String): Unit = {
        val isNotifyUser = java.lang.Boolean.parseBoolean(PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED))
        if (isNotifyUser) {
            val request = new Request()
            request.setOperation(ActorOperations.COURSE_BATCH_NOTIFICATION.getValue)
            request.put(JsonKey.USER_ID, userId)
            request.put(JsonKey.COURSE_BATCH, batchData)
            request.put(JsonKey.OPERATION_TYPE, operationType)
            courseBatchNotificationActorRef.tell(request, getSelf())
        }
    }

    def generateTelemetryAudit(userId: String, courseId: String, batchId: String, batchData: CourseBatch, correlation: String, state: String, context: java.util.Map[String, AnyRef]): Unit = {
        val targetedObject = TelemetryUtil.generateTargetObject(userId, JsonKey.USER, state, null)
        val correlationObject = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        TelemetryUtil.generateCorrelatedObject(courseId, JsonKey.COURSE, correlation, correlationObject)
        TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, "user.batch", correlationObject)
        val request: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {
            {
                put(JsonKey.USER_ID, userId)
                put(JsonKey.COURSE_ID, courseId)
                put(JsonKey.BATCH_ID, batchId)
            }
        }
        TelemetryUtil.telemetryProcessingCall(request, targetedObject, correlationObject, context)
    }

    def updateProgressData(enrolments: java.util.List[java.util.Map[String, AnyRef]], userId: String) = {
        enrolments.asScala.foreach(enrolment => {
            val userActivityDBResponse = groupDao.read(enrolment.get("courseId").asInstanceOf[String], "Course", java.util.Arrays.asList(userId))
            if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
                ProjectCommonException.throwServerErrorException(ResponseCode.erroCallGrooupAPI,
                    MessageFormat.format(ResponseCode.erroCallGrooupAPI.getErrorMessage()))
            val completedCount: Int = userActivityDBResponse.getResult
                .getOrDefault("response", new util.ArrayList[util.Map[String, AnyRef]]())
                .asInstanceOf[util.List[util.Map[String, AnyRef]]]
                .headOption.getOrElse(new util.HashMap[String, AnyRef]())
                .getOrDefault("agg", new util.HashMap[String, AnyRef]())
                .asInstanceOf[util.Map[String, AnyRef]]
                .getOrDefault("completedCount", 0.asInstanceOf[AnyRef]).asInstanceOf[Int]
            val leafNodesCount: Int = enrolment.get("leafNodesCount").asInstanceOf[Int]
            enrolment.put("progress", completedCount.asInstanceOf[AnyRef])
            enrolment.put("status", getCompletionStatus(completedCount, leafNodesCount).asInstanceOf[AnyRef])
            enrolment.put("completionPercentage", getCompletionPerc(completedCount, leafNodesCount).asInstanceOf[AnyRef])
        })
    }

    def getCompletionStatus(completedCount: Int, leafNodesCount: Int): Int = completedCount match {
        case 0 => 0
        case it if 1 until leafNodesCount contains it => 1
        case leafNodesCount => 2
        case _ => 2
    }

    def getCompletionPerc(completedCount: Int, leafNodesCount: Int): Int = completedCount match {
        case 0 => 0
        case it if 1 until leafNodesCount contains it => (completedCount / leafNodesCount) * 100
        case leafNodesCount => 100
        case _ => 100
    }

    def getCacheKey(userId: String) = {
        userId + ":user-enrolments"
    }

    def setResponseToRedis(key: String, response: Response) :Unit = {
        redisCache.put("user-enrolments", key, response)
        redisCache.setMapExpiry("user-enrolments", ttl)
    }

    def getResponseFromRedis(key: String): Response = {
        val responseString = redisCache.get("user-enrolments", key)
        mapper.readValue(responseString, classOf[Response])
    }

    // TODO: to be removed once all are in scala.
    def setDao(courseDao: CourseBatchDao, userDao: UserCoursesDao, groupDao: GroupDaoImpl, redisCache: RedisCache) = {
        courseBatchDao = courseDao
        userCoursesDao = userDao
        this.groupDao = groupDao
        this.redisCache = redisCache
        this
    }
}
