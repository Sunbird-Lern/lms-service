package org.sunbird.learner.actors.bulkupload;

import akka.actor.ActorRef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.courses.UserCourses;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

/**
 * This actor will handle bulk upload operation .
 *
 * @author Amit Kumar
 */
public class BulkUploadBackGroundJobActor extends BaseActor {

  private String processId = "";
  private final Util.DbInfo bulkDb = Util.dbInfoMap.get(JsonKey.BULK_OP_DB);
  private final CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  private static ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private UserCoursesDao userCourseDao = UserCoursesDaoImpl.getInstance();
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

  @Inject
  @Named("background-job-manager-actor")
  private ActorRef backgroundJobManagerActorRef;

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER, this.getClass().getName());
    if (request.getOperation().equalsIgnoreCase(ActorOperations.PROCESS_BULK_UPLOAD.getValue())) {
      process(request);
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void process(Request actorMessage) {
    processId = (String) actorMessage.get(JsonKey.PROCESS_ID);
    Map<String, Object> dataMap = getBulkData(actorMessage.getRequestContext(), processId);
    logger.info(actorMessage.getRequestContext(), "process started in BulkUploadBackGroundJobActor : " + processId);
    int status = (int) dataMap.get(JsonKey.STATUS);
    if (!(status == (ProjectUtil.BulkProcessStatus.COMPLETED.getValue())
        || status == (ProjectUtil.BulkProcessStatus.INTERRUPT.getValue()))) {
      TypeReference<List<Map<String, Object>>> mapType =
          new TypeReference<List<Map<String, Object>>>() {};
      List<Map<String, Object>> jsonList = null;
      try {
        jsonList = mapper.readValue((String) dataMap.get(JsonKey.DATA), mapType);
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while converting json String to List in BulkUploadBackGroundJobActor : ", e);
      }
      if (((String) dataMap.get(JsonKey.OBJECT_TYPE)).equalsIgnoreCase(JsonKey.BATCH_LEARNER_ENROL)
          || ((String) dataMap.get(JsonKey.OBJECT_TYPE))
              .equalsIgnoreCase(JsonKey.BATCH_LEARNER_UNENROL)) {
        processBatchEnrollment(actorMessage.getRequestContext(), jsonList, processId, (String) dataMap.get(JsonKey.OBJECT_TYPE), actorMessage.getContext());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void processBatchEnrollment(
          RequestContext requestContext, List<Map<String, Object>> jsonList, String processId, String objectType, Map<String, Object> context) {
    // update status from NEW to INProgress
    updateStatusForProcessing(requestContext, processId);
    List<Map<String, Object>> successResultList = new ArrayList<>();
    List<Map<String, Object>> failureResultList = new ArrayList<>();

    Map<String, Object> successListMap = null;
    Map<String, Object> failureListMap = null;
    for (Map<String, Object> batchMap : jsonList) {
      successListMap = new HashMap<>();
      failureListMap = new HashMap<>();
      Map<String, Object> tempFailList = new HashMap<>();
      Map<String, Object> tempSuccessList = new HashMap<>();

      String batchId = (String) batchMap.get(JsonKey.BATCH_ID);
      Future<Map<String, Object>> resultF =
          esService.getDataByIdentifier(requestContext, ProjectUtil.EsType.courseBatch.getTypeName(), batchId);
      Map<String, Object> courseBatchObject =
          (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
      String msg = validateBatchInfo(courseBatchObject);
      if (msg.equals(JsonKey.SUCCESS)) {
        try {
          List<String> userList =
              new ArrayList<>(
                  Arrays.asList((((String) batchMap.get(JsonKey.USER_IDs)).split(","))));
          if (JsonKey.BATCH_LEARNER_ENROL.equalsIgnoreCase(objectType)) {
            validateBatchUserListAndAdd(
                    requestContext, courseBatchObject, batchId, userList, tempFailList, tempSuccessList, context);
          } else if (JsonKey.BATCH_LEARNER_UNENROL.equalsIgnoreCase(objectType)) {
            validateBatchUserListAndRemove(
                    requestContext, courseBatchObject, batchId, userList, tempFailList, tempSuccessList);
          }
          failureListMap.put(batchId, tempFailList.get(JsonKey.FAILURE_RESULT));
          successListMap.put(batchId, tempSuccessList.get(JsonKey.SUCCESS_RESULT));
        } catch (Exception ex) {
          logger.error(requestContext, "Exception Occurred while bulk enrollment : batchId=" + batchId, ex);
          batchMap.put(JsonKey.ERROR_MSG, ex.getMessage());
          failureResultList.add(batchMap);
        }
      } else {
        batchMap.put(JsonKey.ERROR_MSG, msg);
        failureResultList.add(batchMap);
      }
      if (!successListMap.isEmpty()) {
        successResultList.add(successListMap);
      }
      if (!failureListMap.isEmpty()) {
        failureResultList.add(failureListMap);
      }
    }

    // Insert record to BulkDb table
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.ID, processId);
    map.put(JsonKey.SUCCESS_RESULT, ProjectUtil.convertMapToJsonString(successResultList));
    map.put(JsonKey.FAILURE_RESULT, ProjectUtil.convertMapToJsonString(failureResultList));
    map.put(JsonKey.PROCESS_END_TIME, ProjectUtil.getFormattedDate());
    map.put(JsonKey.STATUS, ProjectUtil.BulkProcessStatus.COMPLETED.getValue());
    try {
      cassandraOperation.updateRecord(requestContext, bulkDb.getKeySpace(), bulkDb.getTableName(), map);
    } catch (Exception e) {
      logger.error(requestContext, "Exception Occurred while updating bulk_upload_process in BulkUploadBackGroundJobActor : ", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void validateBatchUserListAndAdd(
          RequestContext requestContext, Map<String, Object> courseBatchObject,
          String batchId,
          List<String> userIds,
          Map<String, Object> failList,
          Map<String, Object> successList, Map<String, Object> context) {
    List<Map<String, Object>> failedUserList = new ArrayList<>();
    List<Map<String, Object>> passedUserList = new ArrayList<>();

    Map<String, Object> map = null;
    List<String> createdFor = (List<String>) courseBatchObject.get(JsonKey.COURSE_CREATED_FOR);
    List<Map<String, Object>> userDetails = userOrgService.getUsersByIds(userIds, (String) context.getOrDefault(JsonKey.X_AUTH_TOKEN, ""));
    logger.info(requestContext, "BulkUploadBackGroundJobActor::validateBatchUserListAndAdd::userDetails : " + userDetails);
    Map<String, String> userToRootOrg =
        userDetails
            .stream()
            .collect(
                Collectors.toMap(
                    user -> (String) user.get(JsonKey.ID), user -> getRootOrgFromUserMap(user)));
    // check whether can update user or not
    for (String userId : userIds) {
      if (!userToRootOrg.containsKey(userId) || !createdFor.contains(userToRootOrg.get(userId))) {
        map = new HashMap<>();
        map.put(userId, ResponseCode.userNotAssociatedToOrg.getErrorMessage());
        failedUserList.add(map);
        continue;
      }
      UserCourses userCourses = userCourseDao.read(requestContext, batchId, userId);
      if (userCourses != null) {
        if (!userCourses.isActive()) {
          Map<String, Object> updateAttributes = new HashMap<>();
          updateAttributes.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
          updateAttributes.put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate());
          userCourseDao.update(requestContext, batchId, userId, updateAttributes);
          String id = UserCoursesService.generateUserCourseESId(batchId, userId);
          esService.update(requestContext, EsType.usercourses.getTypeName(), id, updateAttributes);
        }
      } else {
        addUserCourses(
                requestContext, batchId,
            (String) courseBatchObject.get(JsonKey.COURSE_ID),
            userId,
            (Map<String, String>) (courseBatchObject.get(JsonKey.COURSE_ADDITIONAL_INFO)), context);
      }
      map = new HashMap<>();
      map.put(userId, JsonKey.SUCCESS);
      passedUserList.add(map);
    }

    successList.put(JsonKey.SUCCESS_RESULT, passedUserList);
    failList.put(JsonKey.FAILURE_RESULT, failedUserList);
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

  private Boolean addUserCourses(
          RequestContext requestContext, String batchId, String courseId, String userId, Map<String, String> additionalCourseInfo, Map<String, Object> context) {

    Util.DbInfo courseEnrollmentdbInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB);

    Boolean flag = false;
    Timestamp ts = new Timestamp(new Date().getTime());
    Map<String, Object> userCourses = new HashMap<>();
    userCourses.put(JsonKey.BATCH_ID, batchId);
    userCourses.put(JsonKey.USER_ID, userId);
    userCourses.put(JsonKey.COURSE_ID, courseId);
    userCourses.put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate());
    userCourses.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
    userCourses.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.NOT_STARTED.getValue());
    userCourses.put(JsonKey.DATE_TIME, ts);
    userCourses.put(JsonKey.COURSE_PROGRESS, 0);
    try {
      cassandraOperation.insertRecord(
              requestContext, courseEnrollmentdbInfo.getKeySpace(), courseEnrollmentdbInfo.getTableName(), userCourses);
      userCourses.put(JsonKey.DATE_TIME, ProjectUtil.formatDate(ts));
      String id = UserCoursesService.generateUserCourseESId(batchId, userId);
      userCourses.put(JsonKey.ID, id);
      userCourses.put(JsonKey.IDENTIFIER, id);
      insertUserCoursesToES(requestContext, userCourses);
      flag = true;
      Map<String, Object> targetObject =
          TelemetryUtil.generateTargetObject(userId, JsonKey.USER, JsonKey.UPDATE, null);
      List<Map<String, Object>> correlatedObject = new ArrayList<>();
      TelemetryUtil.generateCorrelatedObject(batchId, JsonKey.BATCH, null, correlatedObject);
      TelemetryUtil.telemetryProcessingCall(userCourses, targetObject, correlatedObject, context);
    } catch (Exception ex) {
      logger.error(requestContext, "INSERT RECORD TO USER COURSES EXCEPTION ", ex);
      flag = false;
    }
    return flag;
  }

  private void insertUserCoursesToES(RequestContext requestContext, Map<String, Object> courseMap) {
    Request request = new Request(requestContext);
    request.setOperation(ActorOperations.INSERT_USR_COURSES_INFO_ELASTIC.getValue());
    request.getRequest().put(JsonKey.USER_COURSES, courseMap);
    try {
      backgroundJobManagerActorRef.tell(request, getSelf());
    } catch (Exception ex) {
      logger.error(requestContext, "Exception Occurred during saving user count to Es : ", ex);
    }
  }

  private void updateUserCoursesToES(RequestContext requestContext, Map<String, Object> courseMap) {
    Request request = new Request(requestContext);
    request.setOperation(ActorOperations.UPDATE_USR_COURSES_INFO_ELASTIC.getValue());
    request.getRequest().put(JsonKey.USER_COURSES, courseMap);
    try {
      backgroundJobManagerActorRef.tell(request, getSelf());
    } catch (Exception ex) {
      logger.error(requestContext, "Exception Occurred during saving user count to Es : ", ex);
    }
  }

  @SuppressWarnings("unchecked")
  private void validateBatchUserListAndRemove(
          RequestContext requestContext, Map<String, Object> courseBatchObject,
          String batchId,
          List<String> userIds,
          Map<String, Object> failList,
          Map<String, Object> successList) {
    if (CollectionUtils.isEmpty(userIds)) {
      return;
    }
    List<Map<String, Object>> failedUserList = new ArrayList<>();
    List<Map<String, Object>> passedUserList = new ArrayList<>();
    for (String userId : userIds) {
      try {
        UserCourses userCourses = userCourseDao.read(requestContext, batchId, userId);
        logger.info(requestContext, "userId=" + userId + ", batchId=" + batchId
                + ", unenrolling with " + userCourses.isActive());
        if (userCourses == null || !userCourses.isActive()) {
          Map<String, Object> map = new HashMap<>();
          map.put(userId, ResponseCode.userNotEnrolledCourse.getErrorMessage());
          failedUserList.add(map);
        } else if (userCourses.getStatus() == ProjectUtil.ProgressStatus.COMPLETED.getValue()) {
          Map<String, Object> map = new HashMap<>();
          map.put(userId, ResponseCode.userAlreadyCompletedCourse.getErrorMessage());
          failedUserList.add(map);
        } else {
          Map<String, Object> updateAttributes = new HashMap<>();
          Timestamp ts = new Timestamp(new Date().getTime());
          updateAttributes.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue());
          updateAttributes.put(JsonKey.DATE_TIME, ts);
          logger.info(requestContext, "userId=" + userId + ", batchId=" + batchId + ", unenrolling");
          userCourseDao.update(requestContext, batchId, userId, updateAttributes);
          userCourses.setActive(false);
          Map<String, Object> map = new HashMap<>();
          map.put(userId, JsonKey.SUCCESS);
          passedUserList.add(map);
          Map<String, Object> userCoursesMap =
              mapper.convertValue(userCourses, new TypeReference<Map<String, Object>>() {});
          if (userCoursesMap.containsKey(JsonKey.COMPLETED_ON)) {
            userCoursesMap.put(
                JsonKey.COMPLETED_ON,
                ProjectUtil.formatDate((Date) userCoursesMap.get(JsonKey.COMPLETED_ON)));
          }
          userCoursesMap.put(JsonKey.DATE_TIME, ProjectUtil.formatDate(ts));
          String id = UserCoursesService.generateUserCourseESId(batchId, userId);
          userCoursesMap.put(JsonKey.ID, id);
          userCoursesMap.put(JsonKey.IDENTIFIER, id);
          updateUserCoursesToES(requestContext, userCoursesMap);
        }
      } catch (Exception ex) {
        logger.error(requestContext, "validateBatchUserListAndRemove Exception Occurred while removing bulk user : "
                        + userId, ex);
        Map<String, Object> map = new HashMap<>();
        map.put(userId, ex.getMessage());
        failedUserList.add(map);
      }
    }
    successList.put(JsonKey.SUCCESS_RESULT, passedUserList);
    failList.put(JsonKey.FAILURE_RESULT, failedUserList);
  }

  @SuppressWarnings("unchecked")
  private String validateBatchInfo(Map<String, Object> courseBatchObject) {

    if ((MapUtils.isEmpty(courseBatchObject))) {
      return ResponseCode.invalidCourseBatchId.getErrorMessage();
    }
    // check whether coursebbatch type is invite only or not ...
    if (ProjectUtil.isNull(courseBatchObject.get(JsonKey.ENROLLMENT_TYPE))
        || !((String) courseBatchObject.get(JsonKey.ENROLLMENT_TYPE))
            .equalsIgnoreCase(JsonKey.INVITE_ONLY)) {
      return ResponseCode.enrollmentTypeValidation.getErrorMessage();
    }
    if (ProjectUtil.isNull(courseBatchObject.get(JsonKey.COURSE_CREATED_FOR))
        || ((List) courseBatchObject.get(JsonKey.COURSE_CREATED_FOR)).isEmpty()) {
      return ResponseCode.courseCreatedForIsNull.getErrorMessage();
    }
    return JsonKey.SUCCESS;
  }

  private void updateStatusForProcessing(RequestContext requestContext, String processId) {
    // Update status to BulkDb table
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.ID, processId);
    map.put(JsonKey.STATUS, ProjectUtil.BulkProcessStatus.IN_PROGRESS.getValue());
    try {
      cassandraOperation.updateRecord(requestContext, bulkDb.getKeySpace(), bulkDb.getTableName(), map);
    } catch (Exception e) {
      logger.error(requestContext, "Exception Occurred while updating bulk_upload_process in BulkUploadBackGroundJobActor : ", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getBulkData(RequestContext requestContext, String processId) {
    try {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.ID, processId);
      map.put(JsonKey.PROCESS_START_TIME, ProjectUtil.getFormattedDate());
      map.put(JsonKey.STATUS, ProjectUtil.BulkProcessStatus.IN_PROGRESS.getValue());
      cassandraOperation.updateRecord(requestContext, bulkDb.getKeySpace(), bulkDb.getTableName(), map);
    } catch (Exception ex) {
      logger.error(requestContext, "Exception occurred while updating status to bulk_upload_process "
                      + "table in BulkUploadBackGroundJobActor.", ex);
    }
    Response res =
        cassandraOperation.getRecordByIdentifier(requestContext, bulkDb.getKeySpace(), bulkDb.getTableName(), processId, null);
    return (((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).get(0));
  }
}
