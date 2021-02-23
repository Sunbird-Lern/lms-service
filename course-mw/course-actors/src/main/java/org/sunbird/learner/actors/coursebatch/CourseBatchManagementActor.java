package org.sunbird.learner.actors.coursebatch;

import static org.sunbird.common.models.util.JsonKey.ID;
import static org.sunbird.common.models.util.JsonKey.PARTICIPANTS;

import akka.actor.ActorRef;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.constants.InstructionEvent;
import org.sunbird.learner.util.ContentUtil;
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
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
  private List<String> validCourseStatus = Arrays.asList("Live", "Unlisted");
  private static final ObjectMapper mapper = new ObjectMapper();


  @Inject
  @Named("course-batch-notification-actor")
  private ActorRef courseBatchNotificationActorRef;

  static {
    DATE_FORMAT.setTimeZone(
        TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
  }

  @Override
  public void onReceive(Request request) throws Throwable {

    Util.initializeContext(request, TelemetryEnvKey.BATCH);
    Util.initializeRequestContext(request, this.getClass().getName());

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

  private void createCourseBatch(Request actorMessage) throws Throwable {
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
    courseBatch.setStatus(setCourseBatchStatus(actorMessage.getRequestContext(), (String) request.get(JsonKey.START_DATE)));
    String courseId = (String) request.get(JsonKey.COURSE_ID);
    Map<String, Object> contentDetails = getContentDetails(actorMessage.getRequestContext(),courseId, headers);
    courseBatch.setCreatedDate(ProjectUtil.getFormattedDate());
    if(StringUtils.isBlank(courseBatch.getCreatedBy()))
    	courseBatch.setCreatedBy(requestedBy);
    validateContentOrg(actorMessage.getRequestContext(), courseBatch.getCreatedFor());
    validateMentors(courseBatch, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""));
    courseBatch.setBatchId(courseBatchId);
    Response result = courseBatchDao.create(actorMessage.getRequestContext(), courseBatch);
    result.put(JsonKey.BATCH_ID, courseBatchId);

    CourseBatchUtil.syncCourseBatchForeground(actorMessage.getRequestContext(),
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
    TelemetryUtil.telemetryProcessingCall(request, targetObject, correlatedObject, actorMessage.getContext());

  //  updateBatchCount(courseBatch);
      //Generate Instruction event. Send courseId for batch
      pushInstructionEvent(actorMessage.getRequestContext(), courseId,courseBatchId);
    if (courseNotificationActive()) {
      batchOperationNotifier(actorMessage, courseBatch, null);
    }
  }

  private boolean courseNotificationActive() {
    return Boolean.parseBoolean(
        PropertiesCache.getInstance()
            .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED));
  }

  private void batchOperationNotifier(Request actorMessage, CourseBatch courseBatch, Map<String, Object> participantMentorMap) {
    logger.debug(actorMessage.getRequestContext(), "CourseBatchManagementActor: batchoperationNotifier called");
    Request batchNotification = new Request(actorMessage.getRequestContext());
    batchNotification.getContext().putAll(actorMessage.getContext());
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
  private void updateCourseBatch(Request actorMessage) throws Exception {
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
        courseBatchDao.readById((String) request.get(JsonKey.COURSE_ID), batchId, actorMessage.getRequestContext());
    CourseBatch courseBatch = getUpdateCourseBatch(actorMessage.getRequestContext(), request);
    courseBatch.setUpdatedDate(ProjectUtil.getFormattedDate());
    checkBatchStatus(courseBatch);
    validateUserPermission(courseBatch, requestedBy);
    validateContentOrg(actorMessage.getRequestContext(), courseBatch.getCreatedFor());
    validateMentors(courseBatch, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""));
    participantsMap = getMentorLists(participantsMap, oldBatch, courseBatch);
    Map<String, Object> courseBatchMap = new ObjectMapper().convertValue(courseBatch, Map.class);
    Response result =
        courseBatchDao.update(actorMessage.getRequestContext(), (String) request.get(JsonKey.COURSE_ID), batchId, courseBatchMap);
    Map<String, Object> updatedCourseObject = mapESFieldsToObject(courseBatchMap);
    sender().tell(result, self());

    CourseBatchUtil.syncCourseBatchForeground(actorMessage.getRequestContext(), batchId, updatedCourseObject);

    targetObject =
        TelemetryUtil.generateTargetObject(batchId, TelemetryEnvKey.BATCH, JsonKey.UPDATE, null);

    Map<String, String> rollUp = new HashMap<>();
    rollUp.put("l1", courseBatch.getCourseId());
    TelemetryUtil.addTargetObjectRollUp(rollUp, targetObject);
    TelemetryUtil.telemetryProcessingCall(courseBatchMap, targetObject, correlatedObject, actorMessage.getContext());
    pushInstructionEvent(actorMessage.getRequestContext(), (String) request.get(JsonKey.COURSE_ID),batchId);
    if (courseNotificationActive()) {
      batchOperationNotifier(actorMessage, courseBatch, participantsMap);
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
    if (ProjectUtil.ProgressStatus.COMPLETED.getValue() == courseBatch.getStatus()) {
      throw new ProjectCommonException(
          ResponseCode.courseBatchEndDateError.getErrorCode(),
          ResponseCode.courseBatchEndDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  @SuppressWarnings("unchecked")
  private CourseBatch getUpdateCourseBatch(RequestContext requestContext, Map<String, Object> request) {
    CourseBatch courseBatch =
        courseBatchDao.readById(
            (String) request.get(JsonKey.COURSE_ID), (String) request.get(JsonKey.ID), requestContext);

    courseBatch.setEnrollmentType(
        getEnrollmentType(
            (String) request.get(JsonKey.ENROLLMENT_TYPE), courseBatch.getEnrollmentType()));
    courseBatch.setCreatedFor(
        getUpdatedCreatedFor(requestContext, 
            (List<String>) request.get(JsonKey.COURSE_CREATED_FOR),
            courseBatch.getEnrollmentType(),
            courseBatch.getCreatedFor()));

    if (request.containsKey(JsonKey.NAME)) courseBatch.setName((String) request.get(JsonKey.NAME));

    if (request.containsKey(JsonKey.DESCRIPTION))
      courseBatch.setDescription((String) request.get(JsonKey.DESCRIPTION));

    if (request.containsKey(JsonKey.MENTORS))
      courseBatch.setMentors((List<String>) request.get(JsonKey.MENTORS));

    updateCourseBatchDate(requestContext, courseBatch, request);

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
    Map<String, Object> courseBatchObject = getValidatedCourseBatch(actorMessage.getRequestContext(), batchId);
    String batchCreator = (String) courseBatchObject.get(JsonKey.CREATED_BY);
    String batchCreatorRootOrgId = getRootOrg(batchCreator, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""));
    List<String> participants =
        userCoursesService.getEnrolledUserFromBatch(
                actorMessage.getRequestContext(), (String) courseBatchObject.get(JsonKey.BATCH_ID));
    CourseBatch courseBatch = new ObjectMapper().convertValue(courseBatchObject, CourseBatch.class);
    List<String> userIds = (List<String>) req.get(JsonKey.USER_IDs);
    if (participants == null) {
      participants = new ArrayList<>();
    }
    Map<String, String> participantWithRootOrgIds = getRootOrgForMultipleUsers(userIds, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""));
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
            actorMessage.getRequestContext(), batchId, (String) courseBatchObject.get(JsonKey.COURSE_ID), addedParticipants);
    for (String userId : addedParticipants) {
      participants.add(userId);
      response.getResult().put(userId, JsonKey.SUCCESS);

      targetObject =
          TelemetryUtil.generateTargetObject(userId, TelemetryEnvKey.USER, JsonKey.UPDATE, null);
      correlatedObject = new ArrayList<>();
      TelemetryUtil.generateCorrelatedObject(
          batchId, TelemetryEnvKey.BATCH, null, correlatedObject);
      TelemetryUtil.telemetryProcessingCall(req, targetObject, correlatedObject, actorMessage.getContext());
    }
    sender().tell(response, self());
    if (courseNotificationActive()) {
      Map<String, Object> participantMentorMap = new HashMap<>();
      participantMentorMap.put(JsonKey.ADDED_PARTICIPANTS, addedParticipants);
      batchOperationNotifier(actorMessage, courseBatch, participantMentorMap);
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
    Map<String, Object> courseBatchObject = getValidatedCourseBatch(actorMessage.getRequestContext(), batchId);
    List<String> participants =
        userCoursesService.getEnrolledUserFromBatch(
                actorMessage.getRequestContext(), (String) courseBatchObject.get(JsonKey.BATCH_ID));
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
              userCoursesService.unenroll(actorMessage.getRequestContext(), batchId, id);
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
      TelemetryUtil.telemetryProcessingCall(req, targetObject, correlatedObject, actorMessage.getContext());
    }
    sender().tell(response, self());
    if (courseNotificationActive()) {
      Map<String, Object> participantMentorMap = new HashMap<>();
      participantMentorMap.put(JsonKey.REMOVED_PARTICIPANTS, removedParticipants);
      batchOperationNotifier(actorMessage, courseBatch, participantMentorMap);
    }
  }

  private Map<String, Object> getValidatedCourseBatch(RequestContext requestContext, String batchId) {
    Future<Map<String, Object>> resultF =
        esService.getDataByIdentifier(requestContext, ProjectUtil.EsType.courseBatch.getTypeName(), batchId);
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
                actorMessage.getRequestContext(), ProjectUtil.EsType.courseBatch.getTypeName(),
            (String) actorMessage.getContext().get(JsonKey.BATCH_ID));
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (result.containsKey(JsonKey.COURSE_ID))
      result.put(JsonKey.COLLECTION_ID, result.getOrDefault(JsonKey.COURSE_ID, ""));
    Response response = new Response();
    response.put(JsonKey.RESPONSE, result);
    sender().tell(response, self());
  }

    private void pushInstructionEvent(RequestContext requestContext, String courseId, String batchId)
            throws Exception {
        Map<String, Object> data = new HashMap<>();

        data.put(
                CourseJsonKey.ACTOR,
                new HashMap<String, Object>() {
                    {
                        put(JsonKey.ID, InstructionEvent.COURSE_BATCH_UPDATE.getActorId());
                        put(JsonKey.TYPE, InstructionEvent.COURSE_BATCH_UPDATE.getActorType());
                    }
                });

        data.put(
                CourseJsonKey.OBJECT,
                new HashMap<String, Object>() {
                    {
                        put(JsonKey.ID, courseId + CourseJsonKey.UNDERSCORE + batchId);
                        put(JsonKey.TYPE, InstructionEvent.COURSE_BATCH_UPDATE.getType());
                    }
                });

        data.put(CourseJsonKey.ACTION, InstructionEvent.COURSE_BATCH_UPDATE.getAction());

        data.put(
                CourseJsonKey.E_DATA,
                new HashMap<String, Object>() {
                    {
                        put(JsonKey.COURSE_ID, courseId);
                        put(JsonKey.BATCH_ID, batchId);
                        put(CourseJsonKey.ACTION, InstructionEvent.COURSE_BATCH_UPDATE.getAction());
                        put(CourseJsonKey.ITERATION, 1);
                    }
                });
        String topic = ProjectUtil.getConfigValue("kafka_topics_instruction");
        logger.info(requestContext, "CourseBatchManagementctor: pushInstructionEvent :Event Data "
                + data+" and Topic "+topic);
        InstructionEventGenerator.pushInstructionEvent(batchId, topic, data);
    }

  private int setCourseBatchStatus(RequestContext requestContext, String startDate) {
    try {
      Date todayDate = DATE_FORMAT.parse(DATE_FORMAT.format(new Date()));
      Date requestedStartDate = DATE_FORMAT.parse(startDate);
      logger.info(requestContext, "CourseBatchManagementActor:setCourseBatchStatus: todayDate="
              + todayDate + ", requestedStartDate=" + requestedStartDate);
      if (todayDate.compareTo(requestedStartDate) == 0) {
        return ProgressStatus.STARTED.getValue();
      } else {
        return ProgressStatus.NOT_STARTED.getValue();
      }
    } catch (ParseException e) {
      logger.error(requestContext, "CourseBatchManagementActor:setCourseBatchStatus: Exception occurred with error message = " + e.getMessage(), e);
    }
    return ProgressStatus.NOT_STARTED.getValue();
  }

  private void validateMentors(CourseBatch courseBatch, String authToken) {
    List<String> mentors = courseBatch.getMentors();
    if (CollectionUtils.isNotEmpty(mentors)) {
      String batchCreatorRootOrgId = getRootOrg(courseBatch.getCreatedBy(), authToken);
      List<Map<String, Object>> mentorDetailList = userOrgService.getUsersByIds(mentors, authToken);
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
          RequestContext requestContext, List<String> createdFor, String enrolmentType, List<String> dbValueCreatedFor) {
    if (createdFor != null) {
      for (String orgId : createdFor) {
        if (!dbValueCreatedFor.contains(orgId) && !isOrgValid(requestContext, orgId)) {
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
  private void updateCourseBatchDate(RequestContext requestContext, CourseBatch courseBatch, Map<String, Object> req) {
    Map<String, Object> courseBatchMap = new ObjectMapper().convertValue(courseBatch, Map.class);
    Date todayDate = getDate(requestContext, null, DATE_FORMAT, null);
    Date dbBatchStartDate = getDate(requestContext, JsonKey.START_DATE, DATE_FORMAT, courseBatchMap);
    Date dbBatchEndDate = getDate(requestContext, JsonKey.END_DATE, DATE_FORMAT, courseBatchMap);
    Date dbEnrollmentEndDate = getDate(requestContext, JsonKey.ENROLLMENT_END_DATE, DATE_FORMAT, courseBatchMap);
    Date requestedStartDate = getDate(requestContext, JsonKey.START_DATE, DATE_FORMAT, req);
    Date requestedEndDate = getDate(requestContext, JsonKey.END_DATE, DATE_FORMAT, req);
    Date requestedEnrollmentEndDate = getDate(requestContext, JsonKey.ENROLLMENT_END_DATE, DATE_FORMAT, req);

    validateUpdateBatchStartDate(requestedStartDate);
    validateBatchStartAndEndDate(
        dbBatchStartDate, dbBatchEndDate, requestedStartDate, requestedEndDate, todayDate);
    if (null != requestedStartDate && todayDate.equals(requestedStartDate)) {
      courseBatch.setStatus(ProgressStatus.STARTED.getValue());
      CourseBatchSchedulerUtil.updateCourseBatchDbStatus(req, true, requestContext);
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
  private Map<String, String> getRootOrgForMultipleUsers(List<String> userIds, String authToken) {

    List<Map<String, Object>> userlist = userOrgService.getUsersByIds(userIds, authToken);
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

  private String getRootOrg(String batchCreator, String authToken) {

    Map<String, Object> userInfo = userOrgService.getUserById(batchCreator, authToken);
    return getRootOrgFromUserMap(userInfo);
  }

  @SuppressWarnings("unchecked")
  private String getRootOrgFromUserMap(Map<String, Object> userInfo) {
    String rootOrg = (String) userInfo.get(JsonKey.ROOT_ORG_ID);
    Map<String, Object> registeredOrgInfo =
        (Map<String, Object>) userInfo.get(JsonKey.REGISTERED_ORG);
    if (registeredOrgInfo != null && !registeredOrgInfo.isEmpty()) {
      if (null != registeredOrgInfo.get(JsonKey.IS_ROOT_ORG)
          && (Boolean) registeredOrgInfo.get(JsonKey.IS_ROOT_ORG)) {
        rootOrg = (String) registeredOrgInfo.get(JsonKey.ID);
      }
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

    if ((existingStartDate.before(todayDate) || existingStartDate.equals(todayDate))
        && !(existingStartDate.equals(requestedStartDate))) {
      throw new ProjectCommonException(
          ResponseCode.invalidBatchStartDateError.getErrorCode(),
          ResponseCode.invalidBatchStartDateError.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    if ((requestedStartDate.before(todayDate)) && !requestedStartDate.equals(existingStartDate)) {
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

  private Date getDate(RequestContext requestContext, String key, SimpleDateFormat format, Map<String, Object> map) {
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
      logger.error(requestContext, "CourseBatchManagementActor:getDate: Exception occurred with message = " + e.getMessage(), e);
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

  private boolean isOrgValid(RequestContext requestContext, String orgId) {

    try {
      Map<String, Object> result = userOrgService.getOrganisationById(orgId);
      logger.debug(requestContext, "CourseBatchManagementActor:isOrgValid: orgId = "
              + (MapUtils.isNotEmpty(result) ? result.get(ID) : null));
      return ((MapUtils.isNotEmpty(result) && orgId.equals(result.get(ID))));
    } catch (Exception e) {
      logger.error(requestContext, "Error while fetching OrgID : " + orgId, e);
    }
    return false;
  }

  private Map<String, Object> getContentDetails(RequestContext requestContext, String courseId, Map<String, String> headers) {
    Map<String, Object> ekStepContent = ContentUtil.getContent(courseId);
    logger.info(requestContext, "CourseBatchManagementActor:getEkStepContent: courseId: " + courseId, null,
            ekStepContent);
    String status = (String) ((Map<String, Object>)ekStepContent.getOrDefault("content", new HashMap<>())).getOrDefault("status", "");
    if (null == ekStepContent ||
            ekStepContent.size() == 0 ||
            !validCourseStatus.contains(status)) {
      logger.info(requestContext, "CourseBatchManagementActor:getEkStepContent: Not found course for ID = " + courseId);
      throw new ProjectCommonException(
          ResponseCode.invalidCourseId.getErrorCode(),
          ResponseCode.invalidCourseId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return (Map<String, Object>)ekStepContent.getOrDefault("content", new HashMap<>());
  }

  private void validateContentOrg(RequestContext requestContext, List<String> createdFor) {
    if (createdFor != null) {
      for (String orgId : createdFor) {
        if (!isOrgValid(requestContext, orgId)) {
          throw new ProjectCommonException(
              ResponseCode.invalidOrgId.getErrorCode(),
              ResponseCode.invalidOrgId.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
        }
      }
    }
  }

  private void getParticipants(Request actorMessage) {
    Map<String, Object> request =
        (Map<String, Object>) actorMessage.getRequest().get(JsonKey.BATCH);
    boolean active = true;
    if (null != request.get(JsonKey.ACTIVE)) {
      active = (boolean) request.get(JsonKey.ACTIVE);
    }
    String batchID = (String) request.get(JsonKey.BATCH_ID);
    List<String> participants = userCoursesService.getParticipantsList(batchID, active, actorMessage.getRequestContext());

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

  private Map<String, Object> mapESFieldsToObject(Map<String, Object> courseBatch) {
    Map<String, Map<String, Object>> certificateTemplates =
            (Map<String, Map<String, Object>>)
                    courseBatch.get(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN);
    if(MapUtils.isNotEmpty(certificateTemplates)) {
      certificateTemplates
              .entrySet()
              .stream()
              .forEach(
                      cert_template ->
                              certificateTemplates.put(
                                      cert_template.getKey(), mapToObject(cert_template.getValue())));
      courseBatch.put(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN, certificateTemplates);
    }
    return courseBatch;
  }

  private Map<String, Object> mapToObject(Map<String, Object> template) {
    try {
      template.put(
              JsonKey.CRITERIA,
              mapper.readValue(
                      (String) template.get(JsonKey.CRITERIA),
                      new TypeReference<HashMap<String, Object>>() {}));
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.SIGNATORY_LIST))) {
        template.put(
                CourseJsonKey.SIGNATORY_LIST,
                mapper.readValue(
                        (String) template.get(CourseJsonKey.SIGNATORY_LIST),
                        new TypeReference<List<Object>>() {
                        }));
      }
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.ISSUER))) {
        template.put(
                CourseJsonKey.ISSUER,
                mapper.readValue(
                        (String) template.get(CourseJsonKey.ISSUER),
                        new TypeReference<HashMap<String, Object>>() {
                        }));
      }
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.NOTIFY_TEMPLATE))) {
        template.put(
                CourseJsonKey.NOTIFY_TEMPLATE,
                mapper.readValue(
                        (String) template.get(CourseJsonKey.NOTIFY_TEMPLATE),
                        new TypeReference<HashMap<String, Object>>() {
                        }));
      }
    } catch (Exception ex) {
      logger.error(null, "CourseBatchCertificateActor:mapToObject Exception occurred with error message ==", ex);
    }
    return template;
  }
}
