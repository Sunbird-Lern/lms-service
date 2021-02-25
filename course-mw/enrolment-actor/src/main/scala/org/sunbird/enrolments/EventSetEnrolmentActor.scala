package org.sunbird.enrolments

import akka.actor.ActorRef
import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.Common
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.util.ProjectUtil.{EnrolmentType, ProgressStatus}
import org.sunbird.common.models.util._
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.learner.actors.coursebatch.dao.{CourseBatchDao, UserCoursesDao}
import org.sunbird.learner.actors.event.EventContentUtil
import org.sunbird.learner.util._
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.telemetry.util.TelemetryUtil

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util
import java.util.Date
import javax.inject.{Inject, Named}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class EventSetEnrolmentActor @Inject()(@Named("course-batch-notification-actor") courseBatchNotificationActorRef: ActorRef
                                      )(implicit val cacheUtil: RedisCacheUtil) extends BaseEnrolmentActor {

  var courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()
  var userCoursesDao: UserCoursesDao = new UserCoursesDaoImpl()
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val mapper = new ObjectMapper

  override def preStart {
    println("Starting EventSetEnrolmentActor")
  }

  override def postStop {
    cacheUtil.closePool()
    println("EventSetEnrolmentActor stopped successfully")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    println(s"Restarting EventSetEnrolmentActor: $message")
    reason.printStackTrace()
    super.preRestart(reason, message)
  }

  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH)
    request.getOperation match {
      case "enrol" => enroll(request)
      case "unenrol" => unEnroll(request)
      case _ => ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData,
        ResponseCode.invalidRequestData.getErrorMessage)
    }
  }

  def enroll(request: Request): Unit = {
    val eventSetId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
    val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val fixedBatchId: String = request.get(JsonKey.FIXED_BATCH_ID).asInstanceOf[String]
    val eventIds: util.List[String] = EventContentUtil.getChildEventIds(request, eventSetId)
    eventIds.foreach(eventId => {
      val childBatchId = Common.formBatchIdForFixedBatchId(eventId, fixedBatchId)
      val batchData: CourseBatch = getBatch(request.getRequestContext, eventId, childBatchId, true)
      val enrolmentData: UserCourses = userCoursesDao.read(request.getRequestContext, userId, eventId, childBatchId)
      validateEnrolment(batchData, enrolmentData, true)
      val data: util.Map[String, AnyRef] = createUserEnrolmentMap(userId, eventId, childBatchId, enrolmentData, request.getContext.getOrDefault(JsonKey.REQUEST_ID, "").asInstanceOf[String])
      upsertEnrollment(userId, eventId, childBatchId, data, (null == enrolmentData), request.getRequestContext)
      generateTelemetryAudit(userId, eventSetId, childBatchId, data, "enrol", JsonKey.CREATE, request.getContext)
      notifyUser(userId, batchData, JsonKey.ADD)
    })
    cacheUtil.delete(getCacheKey(userId))
    sender().tell(successResponse(), self)
  }

  def unEnroll(request: Request): Unit = {
    val eventSetId: String = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
    val userId: String = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val fixedBatchId: String = request.get(JsonKey.FIXED_BATCH_ID).asInstanceOf[String]
    val eventIds: util.List[String] = EventContentUtil.getChildEventIds(request, eventSetId)

    eventIds.foreach(eventId => {
      val childBatchId = Common.formBatchIdForFixedBatchId(eventId, fixedBatchId)
      val batchData: CourseBatch = getBatch(request.getRequestContext, eventId, childBatchId, true)
      val enrolmentData: UserCourses = userCoursesDao.read(request.getRequestContext, userId, eventId, childBatchId)
      validateEnrolment(batchData, enrolmentData, false)
      val data: java.util.Map[String, AnyRef] = new java.util.HashMap[String, AnyRef]() {
        {
          put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue.asInstanceOf[AnyRef])
        }
      }
      upsertEnrollment(userId, eventId, childBatchId, data, false, request.getRequestContext)
      generateTelemetryAudit(userId, eventId, childBatchId, data, "unenrol", JsonKey.UPDATE, request.getContext)
      notifyUser(userId, batchData, JsonKey.REMOVE)
    })
    logger.info(request.getRequestContext, "EventSetEnrolmentActor :: unEnroll :: Deleting redis for key " + getCacheKey(userId))
    cacheUtil.delete(getCacheKey(userId))
    sender().tell(successResponse(), self)
  }

  def validateEnrolment(batchData: CourseBatch, enrolmentData: UserCourses, isEnrol: Boolean): Unit = {
    if (null == batchData) ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage)

    if (EnrolmentType.inviteOnly.getVal.equalsIgnoreCase(batchData.getEnrollmentType))
      ProjectCommonException.throwClientErrorException(ResponseCode.enrollmentTypeValidation, ResponseCode.enrollmentTypeValidation.getErrorMessage)

    if ((2 == batchData.getStatus) || (null != batchData.getEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEndDate, dateTimeFormatter).atTime(LocalTime.MAX))))
      ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)

    if (isEnrol && null != batchData.getEnrollmentEndDate && LocalDateTime.now().isAfter(LocalDate.parse(batchData.getEnrollmentEndDate, dateTimeFormatter).atTime(LocalTime.MAX)))
      ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchEnrollmentDateEnded, ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage)

    if (isEnrol && null != enrolmentData && enrolmentData.isActive) ProjectCommonException.throwClientErrorException(ResponseCode.userAlreadyEnrolledCourse, ResponseCode.userAlreadyEnrolledCourse.getErrorMessage)
    if (!isEnrol && (null == enrolmentData || !enrolmentData.isActive)) ProjectCommonException.throwClientErrorException(ResponseCode.userNotEnrolledCourse, ResponseCode.userNotEnrolledCourse.getErrorMessage)
    if (!isEnrol && ProjectUtil.ProgressStatus.COMPLETED.getValue == enrolmentData.getStatus) ProjectCommonException.throwClientErrorException(ResponseCode.courseBatchAlreadyCompleted, ResponseCode.courseBatchAlreadyCompleted.getErrorMessage)
  }

  def upsertEnrollment(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], isNew: Boolean, requestContext: RequestContext): Unit = {
    if (isNew) {
      userCoursesDao.insertV2(requestContext, data)
    } else {
      userCoursesDao.updateV2(requestContext, userId, courseId, batchId, data)
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

  def generateTelemetryAudit(userId: String, courseId: String, batchId: String, data: java.util.Map[String, AnyRef], correlation: String, state: String, context: java.util.Map[String, AnyRef]): Unit = {
    val contextMap = new java.util.HashMap[String, AnyRef]()
    contextMap.putAll(context)
    contextMap.put(JsonKey.ACTOR_ID, userId)
    contextMap.put(JsonKey.ACTOR_TYPE, "User")
    val targetedObject = TelemetryUtil.generateTargetObject(userId, JsonKey.USER, state, null)
    targetedObject.put(JsonKey.ROLLUP, new java.util.HashMap[String, AnyRef]() {
      {
        put("l1", courseId)
      }
    })
    val correlationObject = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    TelemetryUtil.generateCorrelatedObject(courseId, JsonKey.COURSE, correlation, correlationObject)
    TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, "user.batch", correlationObject)
    val request: java.util.Map[String, AnyRef] = Map[String, AnyRef](JsonKey.USER_ID -> userId, JsonKey.COURSE_ID -> courseId, JsonKey.BATCH_ID -> batchId, JsonKey.COURSE_ENROLL_DATE -> data.get(JsonKey.COURSE_ENROLL_DATE), JsonKey.ACTIVE -> data.get(JsonKey.ACTIVE)).asJava
    TelemetryUtil.telemetryProcessingCall(request, targetedObject, correlationObject, contextMap, "enrol")
  }

  def getCacheKey(userId: String) = s"$userId:user-enrolments"

  private def getBatch(requestContext: RequestContext, courseId: String, batchId: String, isFixedBatch: Boolean) = {
    if (isFixedBatch) getFixedBatch(batchId, courseId) else courseBatchDao.readById(courseId, batchId, requestContext)
  }

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

