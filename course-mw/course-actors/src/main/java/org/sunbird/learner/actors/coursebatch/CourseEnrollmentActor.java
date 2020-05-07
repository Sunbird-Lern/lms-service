package org.sunbird.learner.actors.coursebatch;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.ProjectUtil.EnrolmentType;
import org.sunbird.common.models.util.ProjectUtil.ProgressStatus;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.models.course.batch.CourseBatch;
import org.sunbird.models.user.courses.UserCourses;
import org.sunbird.telemetry.util.TelemetryUtil;
import scala.concurrent.Future;

import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

public class CourseEnrollmentActor extends BaseActor {

  private static String EKSTEP_COURSE_SEARCH_QUERY =
      "{\"request\": {\"filters\":{\"contentType\": CONTENT_TYPES_PLACEHOLDER, \"objectType\": [\"Content\"], \"identifier\": \"COURSE_ID_PLACEHOLDER\", \"status\": [\"Live\", \"Unlisted\"]},\"limit\": 1}}";

  private CourseBatchDao courseBatchDao = new CourseBatchDaoImpl();
  private UserCoursesDao userCourseDao = UserCoursesDaoImpl.getInstance();
  private static ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private ObjectMapper mapper = new ObjectMapper();

  @Inject
  @Named("course-batch-notification-actor")
  private ActorRef courseBatchNotificationActorRef;

  @Inject
  @Named("background-job-manager-actor")
  private ActorRef backgroundJobManagerActorRef;

  @Override
  public void onReceive(Request request) throws Throwable {

    ProjectLogger.log("CourseEnrollmentActor onReceive called");
    String operation = request.getOperation();

    Util.initializeContext(request, TelemetryEnvKey.BATCH);
    ExecutionContext.setRequestId(request.getRequestId());

    switch (operation) {
      case "enrollCourse":
        enrollCourseBatch(request);
        break;
      case "unenrollCourse":
        unenrollCourseBatch(request);
        break;
      default:
        onReceiveUnsupportedOperation("CourseEnrollmentActor");
    }
  }

  private void enrollCourseBatch(Request actorMessage) {
    ProjectLogger.log("enrollCourseClass called");
    Map<String, Object> requestMap = (Map<String, Object>) actorMessage.getRequest();
    Map<String, Object> courseMap = new HashMap<>();
    courseMap.put(JsonKey.COURSE_ID, requestMap.get(JsonKey.COURSE_ID));
    courseMap.put(JsonKey.BATCH_ID, requestMap.get(JsonKey.BATCH_ID));
    courseMap.put(JsonKey.USER_ID, requestMap.get(JsonKey.USER_ID));
    CourseBatch courseBatch =
        courseBatchDao.readById(
            (String) courseMap.get(JsonKey.COURSE_ID), (String) courseMap.get(JsonKey.BATCH_ID));
    checkUserEnrollementStatus(
        (String) courseMap.get(JsonKey.COURSE_ID), (String) courseMap.get(JsonKey.USER_ID));
    validateCourseBatch(
        courseBatch,
        courseMap,
        (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY),
        ActorOperations.ENROLL_COURSE.getValue());

    UserCourses userCourseResult =
        userCourseDao.read(
            (String) courseMap.get(JsonKey.BATCH_ID), (String) courseMap.get(JsonKey.USER_ID));

    if (!ProjectUtil.isNull(userCourseResult) && userCourseResult.isActive()) {
      ProjectLogger.log("User Already Enrolled Course ");
      ProjectCommonException.throwClientErrorException(
          ResponseCode.userAlreadyEnrolledCourse,
          ResponseCode.userAlreadyEnrolledCourse.getErrorMessage());
    }
    courseMap = createUserCourseMap(courseMap, courseBatch, userCourseResult);
    Response result = new Response();
    if (userCourseResult == null) {
      // user is doing enrollment first time
      userCourseDao.insert(courseMap);
    } else {
      // second time user is doing enrollment for same course batch
      userCourseDao.update(userCourseResult.getBatchId(), userCourseResult.getUserId(), courseMap);
    }
    result.put("response", "SUCCESS");
    sender().tell(result, self());
    if (userCourseResult == null) {
      courseMap.put(JsonKey.DATE_TIME, ProjectUtil.formatDate(new Timestamp(new Date().getTime())));
      updateUserCoursesToES(courseMap);

    } else {
      ProjectLogger.log(
          "CourseEnrollmentActor:enrollCourseBatch user is enrolling second time.",
          LoggerEnum.INFO.name());
      UserCoursesService.sync(
          courseMap,
          (String) courseMap.get(JsonKey.BATCH_ID),
          (String) courseMap.get(JsonKey.USER_ID));
    }
    if (courseNotificationActive()) {
      batchOperationNotifier(courseMap, courseBatch, JsonKey.ADD);
    }
    generateAndProcessTelemetryEvent(courseMap, "user.batch.course", JsonKey.CREATE);
  }

  private boolean courseNotificationActive() {
    ProjectLogger.log(
        "CourseEnrollmentActor: courseNotificationActive: "
            + Boolean.parseBoolean(
                PropertiesCache.getInstance()
                    .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED)),
        LoggerEnum.INFO.name());
    return Boolean.parseBoolean(
        PropertiesCache.getInstance()
            .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED));
  }

  private Map<String, Object> createUserCourseMap(
      Map<String, Object> courseMap, CourseBatch courseBatchResult, UserCourses userCourseResult) {
    courseMap.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
    if (userCourseResult == null) {
      // this will create user batch data for new user
      Timestamp ts = new Timestamp(new Date().getTime());
      String addedBy = (String) courseMap.get(JsonKey.REQUESTED_BY);
      courseMap.put(JsonKey.ADDED_BY, addedBy);
      courseMap.put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate());
      courseMap.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.NOT_STARTED.getValue());
      courseMap.put(JsonKey.DATE_TIME, ts);
      courseMap.put(JsonKey.COURSE_PROGRESS, 0);
    }
    return courseMap;
  }

  private void unenrollCourseBatch(Request actorMessage) {
    ProjectLogger.log("unenrollCourseClass called");
    // objects of telemetry event...
    Map<String, Object> request = actorMessage.getRequest();
    request.remove(JsonKey.ID);
    CourseBatch courseBatch =
        courseBatchDao.readById(
            (String) request.get(JsonKey.COURSE_ID), (String) request.get(JsonKey.BATCH_ID));
    validateCourseBatch(
        courseBatch,
        request,
        (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY),
        ActorOperations.UNENROLL_COURSE.getValue());
    UserCourses userCourseResult =
        userCourseDao.read(
            (String) request.get(JsonKey.BATCH_ID), (String) request.get(JsonKey.USER_ID));
    UserCoursesService.validateUserUnenroll(userCourseResult);
    Response result = updateUserCourses(userCourseResult);
    sender().tell(result, self());
    generateAndProcessTelemetryEvent(request, "user.batch.course.unenroll", JsonKey.UPDATE);

    if (courseNotificationActive()) {
      batchOperationNotifier(request, courseBatch, JsonKey.REMOVE);
    }
  }

  private void batchOperationNotifier(
      Map<String, Object> request, CourseBatch courseBatchResult, String operationType) {
    ProjectLogger.log("CourseBatchEnrollment: batchOperationNotifier: ", LoggerEnum.INFO.name());
    Request batchNotification = new Request();
    batchNotification.setOperation(ActorOperations.COURSE_BATCH_NOTIFICATION.getValue());
    Map<String, Object> batchNotificationMap = new HashMap<>();
    batchNotificationMap.put(JsonKey.USER_ID, request.get(JsonKey.USER_ID));
    batchNotificationMap.put(JsonKey.COURSE_BATCH, courseBatchResult);
    batchNotificationMap.put(JsonKey.OPERATION_TYPE, operationType);
    batchNotification.setRequest(batchNotificationMap);
    courseBatchNotificationActorRef.tell(batchNotification, getSelf());
  }

  private void generateAndProcessTelemetryEvent(
      Map<String, Object> request, String corelation, String state) {
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) request.get(JsonKey.USER_ID), JsonKey.USER, state, null);
    TelemetryUtil.generateCorrelatedObject(
        (String) request.get(JsonKey.COURSE_ID), JsonKey.COURSE, corelation, correlatedObject);
    TelemetryUtil.generateCorrelatedObject(
        (String) request.get(JsonKey.BATCH_ID),
        TelemetryEnvKey.BATCH,
        "user.batch",
        correlatedObject);
    TelemetryUtil.telemetryProcessingCall(request, targetObject, correlatedObject);
  }

  private void updateUserCoursesToES(Map<String, Object> courseMap) {
    Request request = new Request();
    request.setOperation(ActorOperations.INSERT_USR_COURSES_INFO_ELASTIC.getValue());
    request.getRequest().put(JsonKey.USER_COURSES, courseMap);
    try {
      backgroundJobManagerActorRef.tell(request, getSelf());
    } catch (Exception ex) {
      ProjectLogger.log("Exception Occurred during saving user count to Es : ", ex);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getCourseObjectFromEkStep(
      String courseId, Map<String, String> headers) {
    ProjectLogger.log("Requested course id is ==" + courseId, LoggerEnum.INFO.name());
    if (!StringUtils.isBlank(courseId)) {
      try {
        String batchContentTypes = Arrays.stream(getConfigValue(JsonKey.SUNBIRD_BATCH_CONTENT_TYPES).split(",")).map(item -> "\"" + item + "\"").collect(Collectors.joining(", ","[","]"));
        String query = EKSTEP_COURSE_SEARCH_QUERY.replaceAll("COURSE_ID_PLACEHOLDER", courseId).replace("CONTENT_TYPES_PLACEHOLDER", batchContentTypes);
        Map<String, Object> result = ContentUtil.searchContent(query, headers);
        if (null != result && !result.isEmpty() && result.get(JsonKey.CONTENTS) != null) {
          return ((List<Map<String, Object>>) result.get(JsonKey.CONTENTS)).get(0);
          // return (Map<String, Object>) contentObject;
        } else {
          ProjectLogger.log(
              "CourseEnrollmentActor:getCourseObjectFromEkStep: Content not found for requested courseId "
                  + courseId,
              LoggerEnum.INFO.name());
        }
      } catch (Exception e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    return null;
  }

  /*
   * This method will validate courseBatch details before enrolling and
   * unenrolling
   *
   * @Params
   */
  private void validateCourseBatch(
      CourseBatch courseBatchDetails,
      Map<String, Object> request,
      String requestedBy,
      String actorOperation) {

    if (ProjectUtil.isNull(courseBatchDetails)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage());
    }
    verifyRequestedByAndThrowErrorIfNotMatch((String) request.get(JsonKey.USER_ID), requestedBy);
    if (EnrolmentType.inviteOnly.getVal().equals(courseBatchDetails.getEnrollmentType())) {
      ProjectLogger.log(
          "CourseEnrollmentActor validateCourseBatch self enrollment or unenrollment is not applicable for invite only batch.",
          LoggerEnum.INFO.name());
      ProjectCommonException.throwClientErrorException(
          ResponseCode.enrollmentTypeValidation,
          ResponseCode.enrollmentTypeValidation.getErrorMessage());
    }
    if (!((String) request.get(JsonKey.COURSE_ID)).equals(courseBatchDetails.getCourseId())) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidCourseBatchId, ResponseCode.invalidCourseBatchId.getErrorMessage());
    }
    try {
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
      Date todaydate = format.parse(format.format(new Date()));
      // there might be chance end date is not present
      Date courseBatchEndDate = null;
      Date courseBatchEnrollmentEndDate = null;
      if (StringUtils.isNotBlank(courseBatchDetails.getEndDate())) {
        courseBatchEndDate = format.parse(courseBatchDetails.getEndDate());
      }
      if (StringUtils.isNotBlank(courseBatchDetails.getEnrollmentEndDate())) {
        courseBatchEnrollmentEndDate = format.parse(courseBatchDetails.getEnrollmentEndDate());
      }
      if (ActorOperations.ENROLL_COURSE.getValue().equals(actorOperation)
          && courseBatchEnrollmentEndDate != null
          && courseBatchEnrollmentEndDate.before(todaydate)) {
        ProjectLogger.log(
            "CourseEnrollmentActor validateCourseBatch Enrollment Date has ended.",
            LoggerEnum.INFO.name());
        ProjectCommonException.throwClientErrorException(
            ResponseCode.courseBatchEnrollmentDateEnded,
            ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage());
      }
      if (ProgressStatus.COMPLETED.getValue() == courseBatchDetails.getStatus()
          || (courseBatchEndDate != null && courseBatchEndDate.before(todaydate))) {
        ProjectLogger.log(
            "CourseEnrollmentActor validateCourseBatch Course is completed already.",
            LoggerEnum.INFO.name());
        ProjectCommonException.throwClientErrorException(
            ResponseCode.courseBatchAlreadyCompleted,
            ResponseCode.courseBatchAlreadyCompleted.getErrorMessage());
      }
    } catch (ParseException e) {
      ProjectLogger.log("CourseEnrollmentActor validateCourseBatch ", e);
    }
  }

  private void verifyRequestedByAndThrowErrorIfNotMatch(String userId, String requestedBy) {
    if (!(userId.equals(requestedBy))) {
      ProjectCommonException.throwUnauthorizedErrorException();
    }
  }

  private Response updateUserCourses(UserCourses userCourses) {
    Map<String, Object> userCourseUpdateAttributes = new HashMap<>();
    userCourseUpdateAttributes.put(JsonKey.ACTIVE, false);
    Response result = new Response();
    userCourseDao.update(
        userCourses.getBatchId(), userCourses.getUserId(), userCourseUpdateAttributes);
    result.put("response", "SUCCESS");
    UserCoursesService.sync(
        userCourseUpdateAttributes, userCourses.getBatchId(), userCourses.getUserId());
    return result;
  }

  private void checkUserEnrollementStatus(String courseId, String userId) {
    Map<String, Object> userCoursefilter = new HashMap<>();
    userCoursefilter.put(JsonKey.USER_ID, userId);
    userCoursefilter.put(JsonKey.COURSE_ID, courseId);
    userCoursefilter.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
    List<Map<String, Object>> userCoursesList =
        searchFromES(
            ProjectUtil.EsType.usercourses.getTypeName(),
            userCoursefilter,
            Arrays.asList(JsonKey.BATCH_ID));
    if (CollectionUtils.isNotEmpty(userCoursesList)) {
      ProjectLogger.log("User Enrolled batches :" + userCoursesList, LoggerEnum.INFO);
      List<String> batchIds =
          userCoursesList
              .stream()
              .map(usercourse -> (String) usercourse.get(JsonKey.BATCH_ID))
              .collect(Collectors.toList());
      Map<String, Object> courseBatchfilter = new HashMap<>();
      courseBatchfilter.put(JsonKey.BATCH_ID, batchIds);
      courseBatchfilter.put(
          JsonKey.STATUS,
          Arrays.asList(ProgressStatus.NOT_STARTED.getValue(), ProgressStatus.STARTED.getValue()));
      courseBatchfilter.put(JsonKey.ENROLLMENT_TYPE, JsonKey.OPEN);
      List<Map<String, Object>> batchList =
          searchFromES(
              ProjectUtil.EsType.courseBatch.getTypeName(),
              courseBatchfilter,
              Arrays.asList(JsonKey.BATCH_ID));
      if (CollectionUtils.isNotEmpty(batchList)) {
        ProjectLogger.log(" User currently Enrolled for batches :" + batchList, LoggerEnum.INFO);
        ProjectCommonException.throwClientErrorException(
            ResponseCode.userAlreadyEnrolledCourse,
            ResponseCode.userAlreadyEnrolledCourse.getErrorMessage());
      }
    }
  }

  private List<Map<String, Object>> searchFromES(
      String index, Map<String, Object> filter, List<String> fields) {
    SearchDTO searchDto = new SearchDTO();
    if (CollectionUtils.isNotEmpty(fields)) {
      searchDto.setFields(fields);
    }
    searchDto.getAdditionalProperties().put(JsonKey.FILTERS, filter);
    List<Map<String, Object>> esContents = null;
    Future<Map<String, Object>> resultF = esService.search(searchDto, index);

    Map<String, Object> resultMap =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (MapUtils.isNotEmpty(resultMap)) {
      esContents = (List<Map<String, Object>>) resultMap.get(JsonKey.CONTENT);
    }
    return esContents;
  }
}
