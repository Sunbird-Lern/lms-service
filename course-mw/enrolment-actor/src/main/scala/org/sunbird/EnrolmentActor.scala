package org.sunbird

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

import javax.inject.{Inject, Named}
import akka.actor.ActorRef
import org.sunbird.actor.base.BaseActor
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.util.{ActorOperations, JsonKey, ProjectUtil, PropertiesCache, TelemetryEnvKey}
import org.sunbird.common.models.util.ProjectUtil.EnrolmentType
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.{CourseBatchDao, UserCoursesDao}
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.telemetry.util.TelemetryUtil

class EnrolmentActor @Inject()(@Named("course-batch-notification-actor") courseBatchNotificationActorRef: ActorRef) extends BaseActor {
    
    val courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()
    val userCoursesDao: UserCoursesDao = new UserCoursesDaoImpl()

    override def onReceive(request: Request): Unit = {
        request.getOperation match {
            case "enroll" => enroll(request)
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
        //verifyRequestedBy(request)
        validateEnrolment(batchData, enrolmentData, true)
        val data: java.util.Map[String, AnyRef] = createUserEnrolmentMap(userId, courseId, batchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
        upsertEnrollment(userId, courseId, batchId, enrolmentData, data)
        sender().tell(successResponse(), self)
        notifyUser(userId, batchData, JsonKey.ADD)
        generateTelemetryAudit(userId, courseId, batchId, batchData, "user.enrol", JsonKey.CREATE, request.getContext)
    }

    def validateEnrolment(batchData: CourseBatch, enrolmentData: UserCourses, isEnrol: Boolean): Unit = {
        if(null == batchData) ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage)
        
        if(EnrolmentType.inviteOnly.getVal.equalsIgnoreCase(batchData.getEnrollmentType))
            ProjectCommonException.throwClientErrorException(ResponseCode.enrollmentTypeValidation, ResponseCode.enrollmentTypeValidation.getErrorMessage)
        
        if((2 == batchData.getStatus) || (null != batchData.getEndDate && LocalDateTime.now().isAfter(LocalDateTime.parse(batchData.getEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd")))))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
        
        if(isEnrol && null != batchData.getEnrollmentEndDate && LocalDateTime.now().isAfter(LocalDateTime.parse(batchData.getEnrollmentEndDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
            ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)
        
        if(null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
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
        val isNotifyUser = Option(PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED)).getOrElse("false").toBoolean
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
