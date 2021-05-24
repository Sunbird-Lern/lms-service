package org.sunbird.learner.actors.coursebatch;

import akka.actor.ActorRef;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.ProgressStatus;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.JsonUtil;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.models.course.batch.CourseBatch;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static org.sunbird.common.models.util.JsonKey.ID;
import static org.sunbird.common.models.util.JsonKey.PARTICIPANTS;

public class CourseBatchManagementActor extends BaseActor {

  private CourseBatchDao courseBatchDao = new CourseBatchDaoImpl();
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
  private UserCoursesService userCoursesService = new UserCoursesService();
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private String dateFormat = "yyyy-MM-dd";
  private List<String> validCourseStatus = Arrays.asList("Live", "Unlisted");
  private String timeZone = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE);

  @Inject
  @Named("course-batch-notification-actor")
  private ActorRef courseBatchNotificationActorRef;

  @Override
  public void onReceive(Request request) throws Throwable {

    Util.initializeContext(request, TelemetryEnvKey.BATCH, this.getClass().getName());

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
    CourseBatch courseBatch = JsonUtil.convert(request, CourseBatch.class);
    courseBatch.setStatus(setCourseBatchStatus(actorMessage.getRequestContext(), (String) request.get(JsonKey.START_DATE)));
    String courseId = (String) request.get(JsonKey.COURSE_ID);
    Map<String, Object> contentDetails = getContentDetails(actorMessage.getRequestContext(),courseId, headers);
    courseBatch.setCreatedDate(ProjectUtil.getTimeStamp());
    if(StringUtils.isBlank(courseBatch.getCreatedBy()))
    	courseBatch.setCreatedBy(requestedBy);
    validateContentOrg(actorMessage.getRequestContext(), courseBatch.getCreatedFor());
    validateMentors(courseBatch, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""), actorMessage.getRequestContext());
    courseBatch.setBatchId(courseBatchId);
    Response result = courseBatchDao.create(actorMessage.getRequestContext(), courseBatch);
    result.put(JsonKey.BATCH_ID, courseBatchId);

    Map<String, Object> esCourseMap = CourseBatchUtil.esCourseMapping(courseBatch, dateFormat);
    CourseBatchUtil.syncCourseBatchForeground(actorMessage.getRequestContext(),
        courseBatchId, esCourseMap);
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
      updateCollection(actorMessage.getRequestContext(), esCourseMap, contentDetails);
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
    Map<String, String> headers =
            (Map<String, String>) actorMessage.getContext().get(JsonKey.HEADER);
    String requestedBy = (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY);

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
    CourseBatch oldBatch =
        courseBatchDao.readById((String) request.get(JsonKey.COURSE_ID), batchId, actorMessage.getRequestContext());
    CourseBatch courseBatch = getUpdateCourseBatch(actorMessage.getRequestContext(), request, oldBatch);
    courseBatch.setUpdatedDate(ProjectUtil.getTimeStamp());
    Map<String, Object> contentDetails = getContentDetails(actorMessage.getRequestContext(),courseBatch.getCourseId(), headers);
    validateUserPermission(courseBatch, requestedBy);
    validateContentOrg(actorMessage.getRequestContext(), courseBatch.getCreatedFor());
    validateMentors(courseBatch, (String) actorMessage.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, ""), actorMessage.getRequestContext());
    participantsMap = getMentorLists(participantsMap, oldBatch, courseBatch);
    Map<String, Object> courseBatchMap = CourseBatchUtil.cassandraCourseMapping(courseBatch, dateFormat);
    Response result =
        courseBatchDao.update(actorMessage.getRequestContext(), (String) request.get(JsonKey.COURSE_ID), batchId, courseBatchMap);
    CourseBatch updatedCourseObject = mapESFieldsToObject(courseBatch);
    sender().tell(result, self());
    Map<String, Object> esCourseMap = CourseBatchUtil.esCourseMapping(updatedCourseObject, dateFormat);

    CourseBatchUtil.syncCourseBatchForeground(actorMessage.getRequestContext(), batchId, esCourseMap);

    targetObject =
        TelemetryUtil.generateTargetObject(batchId, TelemetryEnvKey.BATCH, JsonKey.UPDATE, null);

    Map<String, String> rollUp = new HashMap<>();
    rollUp.put("l1", courseBatch.getCourseId());
    TelemetryUtil.addTargetObjectRollUp(rollUp, targetObject);
    TelemetryUtil.telemetryProcessingCall(courseBatchMap, targetObject, correlatedObject, actorMessage.getContext());
    updateCollection(actorMessage.getRequestContext(), esCourseMap, contentDetails);
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

  @SuppressWarnings("unchecked")
  private CourseBatch getUpdateCourseBatch(RequestContext requestContext, Map<String, Object> request, CourseBatch oldBatch) throws Exception {
    CourseBatch courseBatch = JsonUtil.deserialize(JsonUtil.serialize(oldBatch), CourseBatch.class);
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


  private int setCourseBatchStatus(RequestContext requestContext, String startDate) {
    try {
      SimpleDateFormat dateFormatter = ProjectUtil.getDateFormatter(dateFormat);
      dateFormatter.setTimeZone(TimeZone.getTimeZone(timeZone));
      Date todayDate = dateFormatter.parse(dateFormatter.format(new Date()));
      Date requestedStartDate = dateFormatter.parse(startDate);
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

  private void validateMentors(CourseBatch courseBatch, String authToken, RequestContext requestContext) {
    List<String> mentors = courseBatch.getMentors();
    if (CollectionUtils.isNotEmpty(mentors)) {
      mentors = mentors.stream().distinct().collect(Collectors.toList());
      courseBatch.setMentors(mentors);
      String batchCreatorRootOrgId = getRootOrg(courseBatch.getCreatedBy(), authToken);
      List<Map<String, Object>> mentorDetailList = userOrgService.getUsersByIds(mentors, authToken);
      logger.info(requestContext, "CourseBatchManagementActor::validateMentors::mentorDetailList : " + mentorDetailList);
      if (CollectionUtils.isNotEmpty(mentorDetailList)) {
        Map<String, Map<String, Object>> mentorDetails =
                mentorDetailList.stream().collect(Collectors.toMap(map -> (String) map.get(JsonKey.ID), map -> map));

        for (String mentorId : mentors) {
          Map<String, Object> result = mentorDetails.getOrDefault(mentorId, new HashMap<>());
          if (MapUtils.isEmpty(result) || Optional.ofNullable((Boolean) result.getOrDefault(JsonKey.IS_DELETED, false)).orElse(false)) {
            throw new ProjectCommonException(
                    ResponseCode.invalidUserId.getErrorCode(),
                    ResponseCode.invalidUserId.getErrorMessage(),
                    ResponseCode.CLIENT_ERROR.getResponseCode());
          } else {
            String mentorRootOrgId = getRootOrgFromUserMap(result);
            if (StringUtils.isEmpty(batchCreatorRootOrgId) || !batchCreatorRootOrgId.equals(mentorRootOrgId)) {
              throw new ProjectCommonException(
                      ResponseCode.userNotAssociatedToRootOrg.getErrorCode(),
                      ResponseCode.userNotAssociatedToRootOrg.getErrorMessage(),
                      ResponseCode.CLIENT_ERROR.getResponseCode(),
                      mentorId);
            }
          }
        }
      } else {
        logger.info(requestContext, "Invalid mentors for batchId: " + courseBatch.getBatchId() + ", mentors: " + mentors);
        throw new ProjectCommonException(
                ResponseCode.invalidUserId.getErrorCode(),
                ResponseCode.invalidUserId.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
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
  private void updateCourseBatchDate(RequestContext requestContext, CourseBatch courseBatch, Map<String, Object> req) throws Exception {
    Map<String, Object> courseBatchMap = CourseBatchUtil.cassandraCourseMapping(courseBatch, dateFormat);
    Date todayDate = getDate(requestContext, null, null);
    Date dbBatchStartDate = getDate(requestContext, JsonKey.START_DATE, courseBatchMap);
    Date dbBatchEndDate = getDate(requestContext, JsonKey.END_DATE,  courseBatchMap);
    Date dbEnrollmentEndDate = getDate(requestContext, JsonKey.ENROLLMENT_END_DATE, courseBatchMap);
    Date requestedStartDate = getDate(requestContext, JsonKey.START_DATE, req);
    Date requestedEndDate = getDate(requestContext, JsonKey.END_DATE, req);
    Date requestedEnrollmentEndDate = getDate(requestContext, JsonKey.ENROLLMENT_END_DATE, req);

    // After deprecating the text date remove below
    dbBatchStartDate = dbBatchStartDate == null ? getDate(requestContext, JsonKey.OLD_START_DATE, courseBatchMap) : dbBatchStartDate;
    dbBatchEndDate = dbBatchEndDate == null ? getDate(requestContext, JsonKey.OLD_END_DATE, courseBatchMap) : dbBatchEndDate;
    dbEnrollmentEndDate = dbEnrollmentEndDate == null ? getDate(requestContext, JsonKey.OLD_ENROLLMENT_END_DATE, courseBatchMap) : dbEnrollmentEndDate;

    validateUpdateBatchStartDate(requestedStartDate);
    validateBatchStartAndEndDate(
        dbBatchStartDate, dbBatchEndDate, requestedStartDate, requestedEndDate, todayDate);
    
    /* Update the batch to In-Progress for below conditions
    * 1. StartDate is greater than or equal to today's date
    * 2. EndDate can be either NULL or 
    *     EndDate can be greater than or equal to today's date or
    *     EndDate can be greater than or equal to existing EndDate
    * */
    Boolean batchStarted = (null != requestedStartDate && todayDate.compareTo(requestedStartDate) >=0)
            && ((null == requestedEndDate) 
                || (null != requestedEndDate && null == dbBatchEndDate && todayDate.compareTo(requestedEndDate) <= 0) 
                || (null != requestedEndDate && null != dbBatchEndDate && requestedEndDate.compareTo(dbBatchEndDate) >=0));
    
    if(batchStarted)
      courseBatch.setStatus(ProgressStatus.STARTED.getValue());
    
    validateBatchEnrollmentEndDate(
        dbBatchStartDate,
        dbBatchEndDate,
        dbEnrollmentEndDate,
        requestedStartDate,
        requestedEndDate,
        requestedEnrollmentEndDate,
        todayDate);
    courseBatch.setStartDate( 
            null != requestedStartDate
                    ? requestedStartDate
                    : courseBatch.getStartDate());
    courseBatch.setEndDate( 
             null != requestedEndDate
                    ? requestedEndDate
                    : courseBatch.getEndDate());
    courseBatch.setEnrollmentEndDate(
            null != requestedEnrollmentEndDate
                    ? requestedEnrollmentEndDate
                    : courseBatch.getEnrollmentEndDate());
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
    if(null == startDate) {
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
    Date startDate = null != requestedStartDate ? requestedStartDate : existingStartDate;
    Date endDate = null != requestedEndDate ? requestedEndDate : existingEndDate;

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
    
    if(null != existingEndDate && null != requestedEndDate 
            && requestedEndDate.before(existingEndDate) 
            && (null != existingEndDate && existingEndDate.before(todayDate))) {
        throw new ProjectCommonException(
                ResponseCode.courseBatchEndDateError.getErrorCode(),
                ResponseCode.courseBatchEndDateError.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private Date getDate(RequestContext requestContext, String key, Map<String, Object> map) {
    try {
      SimpleDateFormat format = ProjectUtil.getDateFormatter(dateFormat);
      format.setTimeZone(TimeZone.getTimeZone(timeZone));
      if (MapUtils.isEmpty(map)) {
        return format.parse(format.format(new Date()));
      } else {
        if (map.get(key) != null) {
          Date d;
          if (map.get(key) instanceof Date) {
            d = format.parse(format.format(map.get(key)));
          } else {
            d = format.parse((String) map.get(key));
          }
          if (key.equalsIgnoreCase(JsonKey.END_DATE) || key.equalsIgnoreCase(JsonKey.ENROLLMENT_END_DATE) ||
                  key.equalsIgnoreCase(JsonKey.OLD_END_DATE) || key.equalsIgnoreCase(JsonKey.OLD_ENROLLMENT_END_DATE)) {
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
    Map<String, Object> ekStepContent = ContentUtil.getContent(courseId, Arrays.asList("status", "batches"));
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

  private CourseBatch mapESFieldsToObject(CourseBatch courseBatch) {
    Map<String, Object> certificateTemplates = courseBatch.getCertTemplates();
    if(MapUtils.isNotEmpty(certificateTemplates)) {
      certificateTemplates
              .entrySet()
              .stream()
              .forEach(
                      cert_template ->
                              certificateTemplates.put(
                                      cert_template.getKey(), mapToObject((Map<String, Object>) cert_template.getValue())));
      courseBatch.setCertTemplates(certificateTemplates);
    }
    return courseBatch;
  }

  private Map<String, Object> mapToObject(Map<String, Object> template) {
    try {
      template.put(
              JsonKey.CRITERIA,
              JsonUtil.deserialize((String) template.get(JsonKey.CRITERIA), Map.class));
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.SIGNATORY_LIST))) {
        template.put(
                CourseJsonKey.SIGNATORY_LIST,
                JsonUtil.deserialize((String) template.get(CourseJsonKey.SIGNATORY_LIST), ArrayList.class));
      }
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.ISSUER))) {
        template.put(
                CourseJsonKey.ISSUER,
                JsonUtil.deserialize((String) template.get(CourseJsonKey.ISSUER), Map.class));
      }
      if(StringUtils.isNotEmpty((String)template.get(CourseJsonKey.NOTIFY_TEMPLATE))) {
        template.put(
                CourseJsonKey.NOTIFY_TEMPLATE,
                JsonUtil.deserialize((String) template.get(CourseJsonKey.NOTIFY_TEMPLATE), Map.class));
      }
    } catch (Exception ex) {
      logger.error(null, "CourseBatchCertificateActor:mapToObject Exception occurred with error message ==", ex);
    }
    return template;
  }

  private void updateCollection(RequestContext requestContext, Map<String, Object> courseBatch, Map<String, Object> contentDetails) {
    List<Map<String, Object>> batches = (List<Map<String, Object>>) contentDetails.getOrDefault("batches", new ArrayList<>());
    Map<String, Object> data =  new HashMap<>();
    data.put("batchId", courseBatch.getOrDefault(JsonKey.BATCH_ID, ""));
    data.put("name", courseBatch.getOrDefault(JsonKey.NAME, ""));
    data.put("createdFor", courseBatch.getOrDefault(JsonKey.COURSE_CREATED_FOR, new ArrayList<>()));
    data.put("startDate", courseBatch.getOrDefault(JsonKey.START_DATE, ""));
    data.put("endDate", courseBatch.getOrDefault(JsonKey.END_DATE, null));
    data.put("enrollmentType", courseBatch.getOrDefault(JsonKey.ENROLLMENT_TYPE, ""));
    data.put("status", courseBatch.getOrDefault(JsonKey.STATUS, ""));
    data.put("enrollmentEndDate", getEnrollmentEndDate((String) courseBatch.getOrDefault(JsonKey.ENROLLMENT_END_DATE, null), (String) courseBatch.getOrDefault(JsonKey.END_DATE, null)));
    batches.removeIf(map -> StringUtils.equalsIgnoreCase((String) courseBatch.getOrDefault(JsonKey.BATCH_ID, ""), (String) map.get("batchId")));
    batches.add(data);
    ContentUtil.updateCollection(requestContext, (String) courseBatch.getOrDefault(JsonKey.COURSE_ID, ""), new HashMap<String, Object>() {{ put("batches", batches);}});
  }

  private Object getEnrollmentEndDate(String enrollmentEndDate, String endDate) {
    SimpleDateFormat dateFormatter = ProjectUtil.getDateFormatter(dateFormat);
    dateFormatter.setTimeZone(TimeZone.getTimeZone(timeZone));
    return Optional.ofNullable(enrollmentEndDate).map(x -> x).orElse(Optional.ofNullable(endDate).map(y ->{
      Calendar cal = Calendar.getInstance();
      try {
        cal.setTime(dateFormatter.parse(y));
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return dateFormatter.format(cal.getTime());
      } catch (ParseException e) {
        return null;
      }
    }).orElse(null));
  }

}
