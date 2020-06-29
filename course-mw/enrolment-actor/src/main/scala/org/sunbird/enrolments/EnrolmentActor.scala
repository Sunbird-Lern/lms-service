package org.sunbird.enrolments

import java.sql.Timestamp
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util
import java.util.Date

import akka.actor.ActorRef
import com.fasterxml.jackson.databind.ObjectMapper
import javax.inject.{Inject, Named}
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.apache.commons.lang3.StringUtils
import org.sunbird.actor.base.BaseActor
import org.sunbird.common.ElasticSearchHelper
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.factory.EsClientFactory
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil.EnrolmentType
import org.sunbird.common.models.util._
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.dto.SearchDTO
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.learner.actors.coursebatch.dao.{CourseBatchDao, UserCoursesDao}
import org.sunbird.learner.util.{ContentSearchUtil, Util}
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.telemetry.util.TelemetryUtil

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.concurrent.Future

class EnrolmentActor @Inject()(@Named("course-batch-notification-actor") courseBatchNotificationActorRef: ActorRef) extends BaseEnrolmentActor {
    
    val courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()
    val userCoursesDao: UserCoursesDao = new UserCoursesDaoImpl()

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
       // verifyRequestedBy(request)
        validateEnrolment(batchData, enrolmentData, true)
        val data: java.util.Map[String, AnyRef] = createUserEnrolmentMap(userId, courseId, batchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
        upsertEnrollment(userId, courseId, batchId, enrolmentData, data)
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, batchData, "user.enrol", JsonKey.CREATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.ADD)
    }
    
    
    def unEnroll(request:Request): Unit = {
        val courseId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
        val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val batchId: String = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
        val batchData: CourseBatch = courseBatchDao.readById(courseId, batchId)
        val enrolmentData: UserCourses = userCoursesDao.read(userId, courseId, batchId)
        validateEnrolment(batchData, enrolmentData, false)
        val data: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {{ put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue.asInstanceOf[AnyRef]) }}
        upsertEnrollment(userId,courseId, batchId, enrolmentData, data)
        sender().tell(successResponse(), self)
        generateTelemetryAudit(userId, courseId, batchId, batchData, "user.unenrol", JsonKey.UPDATE, request.getContext)
        notifyUser(userId, batchData, JsonKey.REMOVE)
    }

    def list(request: Request): Unit = {
        val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
        val activeEnrolments:java.util.List[java.util.Map[String, AnyRef]] = getActiveEnrollments(userId)
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = {
            if(CollectionUtils.isNotEmpty(activeEnrolments)) {
                val enrolmentList : java.util.List[java.util.Map[String, AnyRef]] = addCourseDetails(activeEnrolments, request)
                addBatchDetails(enrolmentList, request)
            } else new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        }
        val response: Response = new Response()
        response.put(JsonKey.COURSES, enrolments)
        sender().tell(response, self)
    }

    def getActiveEnrollments(userId: String): java.util.List[java.util.Map[String, AnyRef]] = {
        val enrolments: java.util.List[java.util.Map[String, AnyRef]] = userCoursesDao.listEnrolments(userId)
        enrolments.filter(e => e.getOrDefault(JsonKey.ACTIVE, false.asInstanceOf[AnyRef]).asInstanceOf[Boolean]).toList.asJava
    }

    def addCourseDetails(activeEnrolments: java.util.List[java.util.Map[String, AnyRef]], request:Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val courseIds: java.util.List[String] = activeEnrolments.map(e => e.getOrDefault(JsonKey.COURSE_ID, "").asInstanceOf[String]).distinct.filter(id => StringUtils.isNotBlank(id)).toList.asJava
        val requestBody: String =  prepareSearchRequest(courseIds, request)
        val searchResult:java.util.Map[String, AnyRef] = ContentSearchUtil.searchContentSync(request.getContext.getOrDefault(JsonKey.URL_QUERY_STRING,"").asInstanceOf[String], requestBody, request.get(JsonKey.HEADER).asInstanceOf[java.util.Map[String, String]])
        val coursesList: java.util.List[java.util.Map[String, AnyRef]] = searchResult.getOrDefault(JsonKey.CONTENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        val coursesMap = {
            if(CollectionUtils.isNotEmpty(coursesList)) {
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
        val filters: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {{
            put(JsonKey.IDENTIFIER, courseIds)
            put(JsonKey.CONTENT_TYPE, Array(JsonKey.COURSE))
            put(JsonKey.STATUS, "Live")
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
                enrolment
            }).toList.asJava
        } else
            enrolmentList
    }

    def searchBatchDetails(batchIds: java.util.List[String], request: Request): java.util.List[java.util.Map[String, AnyRef]] = {
        val batchDetails = if(StringUtils.isNotBlank(request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[String])) request.getContext.get(JsonKey.BATCH_DETAILS).asInstanceOf[String] else ""
        val requestedFields: java.util.List[String] = batchDetails.split(",").toList.asJava
        if(CollectionUtils.isNotEmpty(requestedFields)) {
            getBatches(new java.util.ArrayList[String](batchIds), requestedFields)
        } else {
            new java.util.ArrayList[util.Map[String, AnyRef]]()
        }
    }
    
    
    def validateEnrolment(batchData: CourseBatch, enrolmentData: UserCourses, isEnrol: Boolean): Unit = {
        if(null == batchData) ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage)
        
        if(EnrolmentType.inviteOnly.getVal.equalsIgnoreCase(batchData.getEnrollmentType))
            ProjectCommonException.throwClientErrorException(ResponseCode.enrollmentTypeValidation, ResponseCode.enrollmentTypeValidation.getErrorMessage)
        
        if((2 == batchData.getStatus) || (null != batchData.getEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay())))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
        
        if(isEnrol && null != batchData.getEnrollmentEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEnrollmentEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)
        
        if(isEnrol && null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
        if(!isEnrol && (null == enrolmentData || !enrolmentData.isActive)) ProjectCommonException.throwClientErrorException(ResponseCode.userNotEnrolledCourse, ResponseCode.userNotEnrolledCourse.getErrorMessage)
        if(!isEnrol && ProjectUtil.ProgressStatus.COMPLETED.getValue == enrolmentData.getStatus) ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
    }

    def verifyRequestedBy(request: Request) = {
        if(!request.get(JsonKey.USER_ID).asInstanceOf[String].equalsIgnoreCase(request.getContext.getOrDefault(JsonKey.REQUESTED_BY, "").asInstanceOf[String]))
            ProjectCommonException.throwUnauthorizedErrorException()
    }

    def upsertEnrollment(userId: String, courseId: String, batchId: String, enrolmentData: UserCourses, data: java.util.Map[String, AnyRef]): Unit = {
        if(null != enrolmentData) {
            userCoursesDao.updateV2(userId, courseId, batchId, data)
        } else {
            userCoursesDao.insertV2(data)
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

    def generateTelemetryAudit(userId: String, courseId: String, batchId: String, batchData: CourseBatch, correlation: String, state: String, context: java.util.Map[String, AnyRef]): Unit = {
        val targetedObject = TelemetryUtil.generateTargetObject(userId, JsonKey.USER, state, null)
        val correlationObject = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        TelemetryUtil.generateCorrelatedObject(courseId, JsonKey.COURSE, correlation, correlationObject)
        TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, "user.batch", correlationObject)
        val request: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef](){{
            put(JsonKey.USER_ID, userId)
            put(JsonKey.COURSE_ID, courseId)
            put(JsonKey.BATCH_ID, batchId)
        }}
        TelemetryUtil.telemetryProcessingCall(request, targetedObject, correlationObject, context)
    }
}
