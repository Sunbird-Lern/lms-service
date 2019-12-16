package org.sunbird.learner.actors.coursebatch;

import static org.sunbird.common.models.util.JsonKey.ID;
import static org.sunbird.common.models.util.JsonKey.PARTICIPANTS;
import static org.sunbird.common.models.util.ProjectLogger.log;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.ProjectUtil.ProgressStatus;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.CourseBatchSchedulerUtil;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.models.course.batch.CourseBatch;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

public class CourseBatchManagementActor extends BaseActor {

  private CourseBatchDao courseBatchDao = new CourseBatchDaoImpl();
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
  private UserCoursesService userCoursesService = new UserCoursesService();
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  @Inject
  @Named("course-batch-notification-actor")
  private ActorRef courseBatchNotificationActorRef;

  @Override
  public void onReceive(Request request) throws Throwable {

    DATE_FORMAT.setTimeZone(
            TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));

    Util.initializeContext(request, TelemetryEnvKey.BATCH);
    ExecutionContext.setRequestId(request.getRequestId());

    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "createBatch":
        createCourseBatch(request);
        break;
      case "updateBatch":
        updateCourseBatch(request);
        break;
      case "getBatch":
        getCourseBatch(request);
        break;
      case "addUserBatch":
        addUserCourseBatch(request);
        break;
      case "removeUserFromBatch":
        removeUserCourseBatch(request);
        break;
      case "getParticipants":
        getParticipants(request);
        break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
    }
  }

  private void createCourseBatch(Request actorMessage) {
    Map<String, Object> request = actorMessage.getRequest();
    Map<String, Object> targetObject;
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    String courseBatchId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    Map<String, String> headers =
        (Map<String, String>) actorMessage.getContext().get(JsonKey.HEADER);
    String requestedBy = (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY);

    if (Util.isNotNull(request.get(JsonKey.PARTICIPANTS))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidRequestParameter,
          ProjectUtil.formatMessage(
              ResponseCode.invalidRequestParameter.getErrorMessage(), PARTICIPANTS));
    }
    CourseBatch courseBatch = new ObjectMapper().convertValue(request, CourseBatch.class);
    courseBatch.setStatus(setCourseBatchStatus((String) request.get(JsonKey.START_DATE)));
    String courseId = (String) request.get(JsonKey.COURSE_ID);
    Map<String, Object> contentDetails = getContentDetails(courseId, headers);
    courseBatch.setContentDetails(contentDetails, requestedBy);
    validateContentOrg(courseBatch.getCreatedFor());
    validateMentors(courseBatch);
    courseBatch.setBatchId(courseBatchId);
    Response result = courseBatchDao.create(courseBatch);
    result.put(JsonKey.BATCH_ID, courseBatchId);

    CourseBatchUtil.syncCourseBatchForeground(
        courseBatchId, new ObjectMapper().convertValue(courseBatch, Map.class));
    sender().tell(result, self());

    targetObject =
        TelemetryUtil.generateTargetObject(
            courseBatchId, TelemetryEnvKey.BATCH, JsonKey.CREATE, null);
    TelemetryUtil.generateCorrelatedObject(
        (String) request.get(JsonKey.COURSE_ID), JsonKey.COURSE, null, correlatedObject);

    Map<String, String> rollUp = new HashMap<>();
    rollUp.put("l1", (String) request.get(JsonKey.COURSE_ID));
    TelemetryUtil.addTargetObjectRollUp(rollUp, targetObject);
    TelemetryUtil.telemetryProcessingCall(request, targetObject, correlatedObject);

    updateBatchCount(courseBatch);
    if (courseNotificationActive()) {
      batchOperationNotifier(courseBatch, null);
    }
  }

  private boolean courseNotificationActive() {
    ProjectLogger.log(
        "CourseBatchManagementActor: courseNotificationActive: "
            + Boolean.parseBoolean(
                PropertiesCache.getInstance()
                    .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED)),
        LoggerEnum.INFO.name());
    return Boolean.parseBoolean(
        PropertiesCache.getInstance()
            .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED));
  }

  private void batchOperationNotifier(
      CourseBatch courseBatch, Map<String, Object> participantMentorMap) {
    ProjectLogger.log(
        "CourseBatchManagementActor: batchoperationNotifier called", LoggerEnum.INFO.name());
    Request batchNotification = new Request();
    batchNotification.setOperation(ActorOperations.COURSE_BATCH_NOTIFICATION.getValue());
    Map<String, Object> batchNotificationMap = new HashMap<>();
    if (participantMentorMap != null) {
      batchNotificationMap.put(JsonKey.UPDATE, true);
      batchNotificationMap.put(
          JsonKey.ADDED_MENTORS, participantMentorMap.get(JsonKey.ADDED_MENTORS));
      batchNotificationMap.put(
          JsonKey.REMOVED_MENTORS, participantMentorMap.get(JsonKey.REMOVED_MENTORS));
      batchNotificationMap.put(
          JsonKey.ADDED_PARTICIPANTS, participantMentorMap.get(JsonKey.ADDED_PARTICIPANTS));
      batchNotificationMap.put(
          JsonKey.REMOVED_PARTICIPANTS, participantMentorMap.get(JsonKey.REMOVED_PARTICIPANTS));

    } else {
      batchNotificationMap.put(JsonKey.OPERATION_TYPE, JsonKey.ADD);
      batchNotificationMap.put(JsonKey.ADDED_MENTORS, courseBatch.getMentors());
    }
    batchNotificationMap.put(JsonKey.COURSE_BATCH, courseBatch);
    batchNotification.setRequest(batchNotificationMap);
    courseBatchNotificationActorRef.tell(batchNotification, getSelf());
  }

  @SuppressWarnings("unchecked")
  private void updateCourseBatch(Request actorMessage) {
    Map<String, Object> targetObject = null;
    Map<String, Object> participantsMap = new HashMap<>();

    List<Map<String, Object>> correlatedObject = new ArrayList<>();

    Map<String, Object> request = actorMessage.getRequest();
    if (Util.isNotNull(request.get(JsonKey.PARTICIPANTS))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidRequestParameter,
          ProjectUtil.formatMessage(
              ResponseCode.invalidRequestParameter.getErrorMessage(), PARTICIPANTS));
    }
    String batchId =
        request.containsKey(JsonKey.BATCH_ID)
            ? (String) request.get(JsonKey.BATCH_ID)
            : (String) request.get(JsonKey.ID);
    String requestedBy = (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY);
    CourseBatch oldBatch =
        courseBatchDao.readById((String) request.get(JsonKey.COURSE_ID), batchId);
    CourseBatch courseBatch = getUpdateCourseBatch(request);
    courseBatch.setUpdatedDate(ProjectUtil.getFormattedDate());
    checkBatchStatus(courseBatch);
    validateUserPermission(courseBatch, requestedBy);
    validateContentOrg(courseBatch.getCreatedFor());
    validateMentors(courseBatch);
    getMentorLists(participantsMap, oldBatch, courseBatch);
    Map<String, Object> courseBatchMap = new ObjectMapper().convertValue(courseBatch, Map.class);
    Response result =
        courseBatchDao.update((String) request.get(JsonKey.COURSE_ID), batchId, courseBatchMap);
    sender().tell(result, self());

    CourseBatchUtil.syncCourseBatchForeground(batchId, courseBatchMap);

    targetObject =
        TelemetryUtil.generateTargetObject(batchId, TelemetryEnvKey.BATCH, JsonKey.UPDATE, null);

    Map<String, String> rollUp = new HashMap<>();
    rollUp.put("l1", courseBatch.getCourseId());
    TelemetryUtil.addTargetObjectRollUp(rollUp, targetObject);
    TelemetryUtil.telemetryProcessingCall(courseBatchMap, targetObject, correlatedObject);

    if (courseNotificationActive()) {
      batchOperationNotifier(courseBatch, participantsMap);
    }
  }

  private Map<String, Object> getMentorLists(
      Map<String, Object> participantsMap, CourseBatch prevBatch, CourseBatch newBatch) {
    List<String> prevMentors = prevBatch.getMentors();
    List<String> removedMentors = prevBatch.getMentors();
    List<String> addedMentors = newBatch.getMentors();

    if (addedMentors == null) {
      addedMentors = new ArrayList<>();
    }
    if (prevMentors == null) {
      prevMentors = new ArrayList<>();
      removedMentors = new ArrayList<>();
    }

    removedMentors.removeAll(addedMentors);
    addedMentors.removeAll(prevMentors);

    participantsMap.put(JsonKey.REMOVED_MENTORS, removedMentors);
    participantsMap.put(JsonKey.ADDED_MENTORS, addedMentors);

    return participantsMap;
  }

  private void checkBatchStatus(CourseBatch courseBatch) {
    ProjectLogger.log(
        "CourseBatchManagementActor:checkBatchStatus batch staus is :" + courseBatch.getStatus(),
        LoggerEnum.INFO.name());
    if (ProjectUtil.ProgressStatus.COMPLETED.getValue() == courseBatch.getStatus()) {
      throw new ProjectCommonException(
          ResponseCode.courseBatchEndDateError.getErrorCode(),
          ResponseCode.courseBatchEndDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  @SuppressWarnings("unchecked")
  private CourseBatch getUpdateCourseBatch(Map<String, Object> request) {
    CourseBatch courseBatch =
        courseBatchDao.readById(
            (String) request.get(JsonKey.COURSE_ID), (String) request.get(JsonKey.ID));

    courseBatch.setEnrollmentType(
        getEnrollmentType(
            (String) request.get(JsonKey.ENROLLMENT_TYPE), courseBatch.getEnrollmentType()));
    courseBatch.setCreatedFor(
        getUpdatedCreatedFor(
            (List<String>) request.get(JsonKey.COURSE_CREATED_FOR),
            courseBatch.getCreatedFor()));

    if (request.containsKey(JsonKey.NAME)) courseBatch.setName((String) request.get(JsonKey.NAME));

    if (request.containsKey(JsonKey.DESCRIPTION))
      courseBatch.setDescription((String) request.get(JsonKey.DESCRIPTION));

    if (request.containsKey(JsonKey.MENTORS))
      courseBatch.setMentors((List<String>) request.get(JsonKey.MENTORS));

    updateCourseBatchDate(courseBatch, request);

    return courseBatch;
  }

  private String getEnrollmentType(String requestEnrollmentType, String dbEnrollmentType) {
    if (requestEnrollmentType != null) return requestEnrollmentType;
    return dbEnrollmentType;
  }

  @SuppressWarnings("unchecked")
  private void addUserCourseBatch(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Response response = new Response();

    Map<String, Object> targetObject = null;
    List<Map<String, Object>> correlatedObject = new ArrayList<>();

    String batchId = (String) req.get(JsonKey.BATCH_ID);
    TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, null, correlatedObject);
    Map<String, Object> courseBatchObject = getValidatedCourseBatch(batchId);
    String batchCreator = (String) courseBatchObject.get(JsonKey.CREATED_BY);
    String batchCreatorRootOrgId = getRootOrg(batchCreator);
    List<String> participants =
        userCoursesService.getEnrolledUserFromBatch(
            (String) courseBatchObject.get(JsonKey.BATCH_ID));
    CourseBatch courseBatch = new ObjectMapper().convertValue(courseBatchObject, CourseBatch.class);
    List<String> userIds = (List<String>) req.get(JsonKey.USER_IDs);
    if (participants == null) {
      participants = new ArrayList<>();
    }
    Map<String, String> participantWithRootOrgIds = getRootOrgForMultipleUsers(userIds);
    List<String> addedParticipants = new ArrayList<>();
    for (String userId : userIds) {
      if (!(participants.contains(userId))) {
        if (!participantWithRootOrgIds.containsKey(userId)
            || (!batchCreatorRootOrgId.equals(participantWithRootOrgIds.get(userId)))) {
          response.put(
              userId,
              MessageFormat.format(
                  ResponseCode.userNotAssociatedToRootOrg.getErrorMessage(), userId));
          continue;
        }
        addedParticipants.add(userId);

      } else {
        response.getResult().put(userId, JsonKey.SUCCESS);
      }
    }

    userCoursesService.enroll(
        batchId, (String) courseBatchObject.get(JsonKey.COURSE_ID), addedParticipants);
    for (String userId : addedParticipants) {
      participants.add(userId);
      response.getResult().put(userId, JsonKey.SUCCESS);

      targetObject =
          TelemetryUtil.generateTargetObject(userId, TelemetryEnvKey.USER, JsonKey.UPDATE, null);
      correlatedObject = new ArrayList<>();
      TelemetryUtil.generateCorrelatedObject(
          batchId, TelemetryEnvKey.BATCH, null, correlatedObject);
      TelemetryUtil.telemetryProcessingCall(req, targetObject, correlatedObject);
    }
    sender().tell(response, self());
    if (courseNotificationActive()) {
      Map<String, Object> participantMentorMap = new HashMap<>();
      participantMentorMap.put(JsonKey.ADDED_PARTICIPANTS, addedParticipants);
      batchOperationNotifier(courseBatch, participantMentorMap);
    }
  }

  @SuppressWarnings("unchecked")
  private void removeUserCourseBatch(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Response response = new Response();

    Map<String, Object> targetObject = null;
    List<Map<String, Object>> correlatedObject = new ArrayList<>();

    String batchId = (String) req.get(JsonKey.BATCH_ID);
    TelemetryUtil.generateCorrelatedObject(batchId, TelemetryEnvKey.BATCH, null, correlatedObject);
    Map<String, Object> courseBatchObject = getValidatedCourseBatch(batchId);
    List<String> participants =
        userCoursesService.getEnrolledUserFromBatch(
            (String) courseBatchObject.get(JsonKey.BATCH_ID));
    CourseBatch courseBatch = new ObjectMapper().convertValue(courseBatchObject, CourseBatch.class);
    List<String> userIds = (List<String>) req.get(JsonKey.USER_IDs);
    if (participants == null) {
      participants = new ArrayList<>();
    }
    List<String> participantsList =
        CollectionUtils.isEmpty(participants) ? new ArrayList<>() : participants;
    List<String> removedParticipants = new ArrayList<>();
    userIds.forEach(
        id -> {
          if (!participantsList.contains(id)) {
            response.getResult().put(id, ResponseCode.userNotEnrolledCourse.getErrorMessage());
          } else {
            try {
              userCoursesService.unenroll(batchId, id);
              removedParticipants.add(id);
              response.getResult().put(id, JsonKey.SUCCESS);
            } catch (ProjectCommonException ex) {
              response.getResult().put(id, ex.getMessage());
            }
          }
        });

    for (String userId : removedParticipants) {
      targetObject =
          TelemetryUtil.generateTargetObject(userId, TelemetryEnvKey.USER, JsonKey.REMOVE, null);
      correlatedObject = new ArrayList<>();
      TelemetryUtil.generateCorrelatedObject(
          batchId, TelemetryEnvKey.BATCH, null, correlatedObject);
      TelemetryUtil.telemetryProcessingCall(req, targetObject, correlatedObject);
    }
    sender().tell(response, self());
    if (courseNotificationActive()) {
      Map<String, Object> participantMentorMap = new HashMap<>();
      participantMentorMap.put(JsonKey.REMOVED_PARTICIPANTS, removedParticipants);
      batchOperationNotifier(courseBatch, participantMentorMap);
    }
  }

  private Map<String, Object> getValidatedCourseBatch(String batchId) {
    Future<Map<String, Object>> resultF =
        esService.getDataByIdentifier(ProjectUtil.EsType.courseBatch.getTypeName(), batchId);
    Map<String, Object> courseBatchObject =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);

    if (ProjectUtil.isNull(courseBatchObject.get(JsonKey.ENROLLMENT_TYPE))
        || !((String) courseBatchObject.get(JsonKey.ENROLLMENT_TYPE))
            .equalsIgnoreCase(JsonKey.INVITE_ONLY)) {
      throw new ProjectCommonException(
          ResponseCode.enrollmentTypeValidation.getErrorCode(),
          ResponseCode.enrollmentTypeValidation.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (CollectionUtils.isEmpty((List) courseBatchObject.get(JsonKey.COURSE_CREATED_FOR))) {
      throw new ProjectCommonException(
          ResponseCode.courseCreatedForIsNull.getErrorCode(),
          ResponseCode.courseCreatedForIsNull.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    String batchCreator = (String) courseBatchObject.get(JsonKey.CREATED_BY);
    if (StringUtils.isBlank(batchCreator)) {
      throw new ProjectCommonException(
          ResponseCode.invalidCourseCreatorId.getErrorCode(),
          ResponseCode.invalidCourseCreatorId.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    return courseBatchObject;
  }

  private void getCourseBatch(Request actorMessage) {
    Future<Map<String, Object>> resultF =
        esService.getDataByIdentifier(
            ProjectUtil.EsType.courseBatch.getTypeName(),
            (String) actorMessage.getContext().get(JsonKey.BATCH_ID));
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    Response response = new Response();
    response.put(JsonKey.RESPONSE, result);
    sender().tell(response, self());
  }

  private int setCourseBatchStatus(String startDate) {
    try {
      Date todayDate = DATE_FORMAT.parse(DATE_FORMAT.format(new Date()));
      Date requestedStartDate = DATE_FORMAT.parse(startDate);
      ProjectLogger.log(
          "CourseBatchManagementActor:setCourseBatchStatus: todayDate="
              + todayDate
              + ", requestedStartDate="
              + requestedStartDate,
          LoggerEnum.INFO);
      if (todayDate.compareTo(requestedStartDate) == 0) {
        return ProgressStatus.STARTED.getValue();
      } else {
        return ProgressStatus.NOT_STARTED.getValue();
      }
    } catch (ParseException e) {
      ProjectLogger.log(
          "CourseBatchManagementActor:setCourseBatchStatus: Exception occurred with error message = "
              + e.getMessage(),
          e);
    }
    return ProgressStatus.NOT_STARTED.getValue();
  }

  private void validateMentors(CourseBatch courseBatch) {
    List<String> mentors = courseBatch.getMentors();
    if (CollectionUtils.isNotEmpty(mentors)) {
      String batchCreatorRootOrgId = getRootOrg(courseBatch.getCreatedBy());
      List<Map<String, Object>> mentorDetailList = userOrgService.getUsersByIds(mentors);
      Map<String, Map<String, Object>> mentorDetails =
          mentorDetailList
              .stream()
              .collect(Collectors.toMap(map -> (String) map.get(JsonKey.ID), map -> map));
      for (String userId : mentors) {
        Map<String, Object> result = mentorDetails.get(userId);
        String mentorRootOrgId = getRootOrgFromUserMap(result);
        if (!batchCreatorRootOrgId.equals(mentorRootOrgId)) {
          throw new ProjectCommonException(
              ResponseCode.userNotAssociatedToRootOrg.getErrorCode(),
              ResponseCode.userNotAssociatedToRootOrg.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode(),
              userId);
        }
        if ((ProjectUtil.isNull(result))
            || (ProjectUtil.isNotNull(result) && result.isEmpty())
            || (ProjectUtil.isNotNull(result)
                && result.containsKey(JsonKey.IS_DELETED)
                && ProjectUtil.isNotNull(result.get(JsonKey.IS_DELETED))
                && (Boolean) result.get(JsonKey.IS_DELETED))) {
          throw new ProjectCommonException(
              ResponseCode.invalidUserId.getErrorCode(),
              ResponseCode.invalidUserId.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
        }
      }
    }
  }

  private List<String> getUpdatedCreatedFor(
      List<String> createdFor,List<String> dbValueCreatedFor) {
    if (createdFor != null) {
      for (String orgId : createdFor) {
        if (!dbValueCreatedFor.contains(orgId) && !isOrgValid(orgId)) {
          throw new ProjectCommonException(
              ResponseCode.invalidOrgId.getErrorCode(),
              ResponseCode.invalidOrgId.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
        }
      }
      return createdFor;
    }
    return dbValueCreatedFor;
  }

  @SuppressWarnings("unchecked")
  private void updateCourseBatchDate(CourseBatch courseBatch, Map<String, Object> req) {
    Map<String, Object> courseBatchMap = new ObjectMapper().convertValue(courseBatch, Map.class);
    Date todayDate = getDate(null, DATE_FORMAT, null);
    Date dbBatchStartDate = getDate(JsonKey.START_DATE, DATE_FORMAT, courseBatchMap);
    Date dbBatchEndDate = getDate(JsonKey.END_DATE, DATE_FORMAT, courseBatchMap);
    Date dbEnrollmentEndDate = getDate(JsonKey.ENROLLMENT_END_DATE, DATE_FORMAT, courseBatchMap);
    Date requestedStartDate = getDate(JsonKey.START_DATE, DATE_FORMAT, req);
    Date requestedEndDate = getDate(JsonKey.END_DATE, DATE_FORMAT, req);
    Date requestedEnrollmentEndDate = getDate(JsonKey.ENROLLMENT_END_DATE, DATE_FORMAT, req);

    validateUpdateBatchStartDate(requestedStartDate);
    validateBatchStartAndEndDate(
        dbBatchStartDate, dbBatchEndDate, requestedStartDate, requestedEndDate, todayDate);
    if (null != requestedStartDate && null != todayDate && todayDate.equals(requestedStartDate)) {
      courseBatch.setStatus(ProgressStatus.STARTED.getValue());
      CourseBatchSchedulerUtil.updateCourseBatchDbStatus(req, true);
    }
    validateBatchEnrollmentEndDate(
        dbBatchStartDate,
        dbBatchEndDate,
        dbEnrollmentEndDate,
        requestedStartDate,
        requestedEndDate,
        requestedEnrollmentEndDate,
        todayDate);
    courseBatch.setStartDate(
        requestedStartDate != null
            ? (String) req.get(JsonKey.START_DATE)
            : courseBatch.getStartDate());
    courseBatch.setEndDate((String) req.get(JsonKey.END_DATE));
    courseBatch.setEnrollmentEndDate((String) req.get(JsonKey.ENROLLMENT_END_DATE));
  }

  private void validateUserPermission(CourseBatch courseBatch, String requestedBy) {
    List<String> canUpdateList = new ArrayList<>();
    canUpdateList.add(courseBatch.getCreatedBy());
    if (CollectionUtils.isNotEmpty(courseBatch.getMentors())) {
      canUpdateList.addAll(courseBatch.getMentors());
    }
    if (!canUpdateList.contains(requestedBy)) {
      throw new ProjectCommonException(
          ResponseCode.unAuthorized.getErrorCode(),
          ResponseCode.unAuthorized.getErrorMessage(),
          ResponseCode.UNAUTHORIZED.getResponseCode());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getRootOrgForMultipleUsers(List<String> userIds) {

    List<Map<String, Object>> userlist = userOrgService.getUsersByIds(userIds);
    Map<String, String> userWithRootOrgs = new HashMap<>();
    if (CollectionUtils.isNotEmpty(userlist)) {
      userlist.forEach(
          user -> {
            String rootOrg = getRootOrgFromUserMap(user);
            userWithRootOrgs.put((String) user.get(JsonKey.ID), rootOrg);
          });
    }
    return userWithRootOrgs;
  }

  private String getRootOrg(String batchCreator) {

    Map<String, Object> userInfo = userOrgService.getUserById(batchCreator);
    return getRootOrgFromUserMap(userInfo);
  }

  @SuppressWarnings("unchecked")
  private String getRootOrgFromUserMap(Map<String, Object> userInfo) {
    String rootOrg = (String) userInfo.get(JsonKey.ROOT_ORG_ID);
    Map<String, Object> registeredOrgInfo =
        (Map<String, Object>) userInfo.get(JsonKey.REGISTERED_ORG);
    if (MapUtils.isNotEmpty(registeredOrgInfo) && null != registeredOrgInfo.get(JsonKey.IS_ROOT_ORG)
            && (Boolean) registeredOrgInfo.get(JsonKey.IS_ROOT_ORG)) {
        rootOrg = (String) registeredOrgInfo.get(JsonKey.ID);
    }
    return rootOrg;
  }

  private void validateUpdateBatchStartDate(Date startDate) {
    if (startDate != null) {
      try {
        DATE_FORMAT.format(startDate);
      } catch (Exception e) {
        throw new ProjectCommonException(
            ResponseCode.dateFormatError.getErrorCode(),
            ResponseCode.dateFormatError.getErrorMessage(),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
    } else {
      throw new ProjectCommonException(
          ResponseCode.courseBatchStartDateRequired.getErrorCode(),
          ResponseCode.courseBatchStartDateRequired.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private void validateBatchStartAndEndDate(
      Date existingStartDate,
      Date existingEndDate,
      Date requestedStartDate,
      Date requestedEndDate,
      Date todayDate) {
    Date startDate = requestedStartDate != null ? requestedStartDate : existingStartDate;
    Date endDate = requestedEndDate != null ? requestedEndDate : existingEndDate;
    ProjectLogger.log(
        "existingStartDate, existingEndDate, requestedStartDate, requestedEndDate, todaydate"
            + existingStartDate
            + ","
            + existingEndDate
            + ","
            + requestedStartDate
            + ","
            + requestedEndDate
            + ","
            + todayDate,
        LoggerEnum.INFO.name());

    if ((existingStartDate.before(todayDate) || existingStartDate.equals(todayDate))
        && !(existingStartDate.equals(requestedStartDate))) {
      throw new ProjectCommonException(
          ResponseCode.invalidBatchStartDateError.getErrorCode(),
          ResponseCode.invalidBatchStartDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    if ((null !=requestedStartDate && requestedStartDate.before(todayDate)) && !requestedStartDate.equals(existingStartDate)) {
      throw new ProjectCommonException(
          ResponseCode.invalidBatchStartDateError.getErrorCode(),
          ResponseCode.invalidBatchStartDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    if (endDate != null && startDate.after(endDate)) {
      throw new ProjectCommonException(
          ResponseCode.invalidBatchEndDateError.getErrorCode(),
          ResponseCode.invalidBatchEndDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    if ((endDate != null && !endDate.after(todayDate))
        || (existingEndDate != null && !existingEndDate.after(todayDate))) {
      throw new ProjectCommonException(
          ResponseCode.courseBatchEndDateError.getErrorCode(),
          ResponseCode.courseBatchEndDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private Date getDate(String key, SimpleDateFormat format, Map<String, Object> map) {
    try {
      if (MapUtils.isEmpty(map)) {
        return format.parse(format.format(new Date()));
      } else {
        if (StringUtils.isNotBlank((String) map.get(key))) {
          Date d = format.parse((String) map.get(key));
          if (key.equals(JsonKey.END_DATE) || key.equals(JsonKey.ENROLLMENT_END_DATE)) {
            Calendar cal =
                Calendar.getInstance(
                    TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
            cal.setTime(d);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            return cal.getTime();
          }
          return d;
        } else {
          return null;
        }
      }
    } catch (ParseException e) {

      ProjectLogger.log(
          "CourseBatchManagementActor:getDate: Exception occurred with message = " + e.getMessage(),
          e);
    }
    return null;
  }

  private void validateBatchEnrollmentEndDate(
      Date existingStartDate,
      Date existingEndDate,
      Date existingEnrollmentEndDate,
      Date requestedStartDate,
      Date requestedEndDate,
      Date requestedEnrollmentEndDate,
      Date todayDate) {
    ProjectLogger.log(
        "existingStartDate, existingEndDate, existingEnrollmentEndDate, requestedStartDate, requestedEndDate, requestedEnrollmentEndDate, todayDate"
            + existingStartDate
            + ","
            + existingEndDate
            + ","
            + existingEnrollmentEndDate
            + ","
            + requestedStartDate
            + ","
            + requestedEndDate
            + ","
            + requestedEnrollmentEndDate
            + ","
            + todayDate,
        LoggerEnum.INFO.name());
    Date endDate = requestedEndDate != null ? requestedEndDate : existingEndDate;
    if (requestedEnrollmentEndDate != null
        && (requestedEnrollmentEndDate.before(requestedStartDate))) {
      throw new ProjectCommonException(
          ResponseCode.enrollmentEndDateStartError.getErrorCode(),
          ResponseCode.enrollmentEndDateStartError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (requestedEnrollmentEndDate != null
        && endDate != null
        && requestedEnrollmentEndDate.after(endDate)) {
      throw new ProjectCommonException(
          ResponseCode.enrollmentEndDateEndError.getErrorCode(),
          ResponseCode.enrollmentEndDateEndError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (requestedEnrollmentEndDate != null
        && !requestedEnrollmentEndDate.equals(existingEnrollmentEndDate)
        && requestedEnrollmentEndDate.before(todayDate)) {
      throw new ProjectCommonException(
          ResponseCode.enrollmentEndDateUpdateError.getErrorCode(),
          ResponseCode.enrollmentEndDateUpdateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private boolean isOrgValid(String orgId) {

    try {
      Map<String, Object> result = userOrgService.getOrganisationById(orgId);

      ProjectLogger.log(
          "CourseBatchManagementActor:isOrgValid: orgId = "
              + (MapUtils.isNotEmpty(result) ? result.get(ID) : null));

      return (MapUtils.isNotEmpty(result) && orgId.equals(result.get(ID)));

    } catch (Exception e) {
      log("Error while fetching OrgID : " + orgId, e);
    }
    return false;
  }

  private Map<String, Object> getContentDetails(String courseId, Map<String, String> headers) {
    Map<String, Object> ekStepContent =
        CourseEnrollmentActor.getCourseObjectFromEkStep(courseId, headers);
    if (null == ekStepContent || ekStepContent.size() == 0) {
      ProjectLogger.log(
          "CourseBatchManagementActor:getEkStepContent: Not found course for ID = " + courseId,
          LoggerEnum.INFO.name());
      throw new ProjectCommonException(
          ResponseCode.invalidCourseId.getErrorCode(),
          ResponseCode.invalidCourseId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return ekStepContent;
  }

  private void validateContentOrg(List<String> createdFor) {
    if (createdFor != null) {
      for (String orgId : createdFor) {
        if (!isOrgValid(orgId)) {
          throw new ProjectCommonException(
              ResponseCode.invalidOrgId.getErrorCode(),
              ResponseCode.invalidOrgId.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void updateBatchCount(CourseBatch courseBatch) {
    CourseBatchSchedulerUtil.doOperationInContentCourse(
        courseBatch.getCourseId(), true, courseBatch.getEnrollmentType());
  }

  private void getParticipants(Request actorMessage) {
    Map<String, Object> request =
        (Map<String, Object>) actorMessage.getRequest().get(JsonKey.BATCH);
    boolean active = true;
    if (null != request.get(JsonKey.ACTIVE)) {
      active = (boolean) request.get(JsonKey.ACTIVE);
    }
    String batchID = (String) request.get(JsonKey.BATCH_ID);
    List<String> participants = userCoursesService.getParticipantsList(batchID, active);

    if (CollectionUtils.isEmpty(participants)) {
      participants = new ArrayList<>();
    }

    Response response = new Response();
    Map<String, Object> result = new HashMap<String, Object>();
    result.put(JsonKey.COUNT, participants.size());
    result.put(JsonKey.PARTICIPANTS, participants);
    response.put(JsonKey.BATCH, result);
    sender().tell(response, self());
  }
}
