package org.sunbird.learner.actors.coursebatch.service;

import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.datasecurity.OneWayHashing;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.models.user.courses.UserCourses;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserCoursesService {
  private UserCoursesDao userCourseDao = UserCoursesDaoImpl.getInstance();
  private static ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  public static final String UNDERSCORE = "_";
  private LoggerUtil logger = new LoggerUtil(UserCoursesService.class);

  protected Integer CASSANDRA_BATCH_SIZE = getBatchSize(JsonKey.CASSANDRA_WRITE_BATCH_SIZE);

  public static String generateUserCourseESId(String batchId, String userId) {
    return batchId + UNDERSCORE + userId;
  }

  public void validateUserUnenroll(RequestContext requestContext, UserCourses userCourseResult) {
    if (userCourseResult == null || !userCourseResult.isActive()) {
      logger.info(requestContext, "UserCoursesService:validateUserUnenroll: User is not enrolled yet");
      throw new ProjectCommonException(
          ResponseCode.userNotEnrolledCourse.getErrorCode(),
          ResponseCode.userNotEnrolledCourse.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (userCourseResult.getStatus() == ProjectUtil.ProgressStatus.COMPLETED.getValue()) {
      logger.debug(requestContext, "UserCoursesService:validateUserUnenroll: User already completed the course");
      throw new ProjectCommonException(
          ResponseCode.userAlreadyCompletedCourse.getErrorCode(),
          ResponseCode.userAlreadyCompletedCourse.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  public static String getPrimaryKey(Map<String, Object> userCourseMap) {
    String userId = (String) userCourseMap.get(JsonKey.USER_ID);
    String courseId = (String) userCourseMap.get(JsonKey.COURSE_ID);
    String batchId = (String) userCourseMap.get(JsonKey.BATCH_ID);
    return getPrimaryKey(userId, courseId, batchId);
  }

  public static String getPrimaryKey(String userId, String courseId, String batchId) {
    return OneWayHashing.encryptVal(
        userId
            + JsonKey.PRIMARY_KEY_DELIMETER
            + courseId
            + JsonKey.PRIMARY_KEY_DELIMETER
            + batchId);
  }

  public void enroll(RequestContext requestContext, String batchId, String courseId, List<String> userIds) {
    Integer count = 0;

    List<Map<String, Object>> records = new ArrayList<>();
    Map<String, Object> userCoursesCommon = new HashMap<>();
    userCoursesCommon.put(JsonKey.BATCH_ID, batchId);
    userCoursesCommon.put(JsonKey.COURSE_ID, courseId);
    userCoursesCommon.put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate());
    userCoursesCommon.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
    userCoursesCommon.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.NOT_STARTED.getValue());
    userCoursesCommon.put(JsonKey.COURSE_PROGRESS, 0);

    for (String userId : userIds) {
      Map<String, Object> userCourses = new HashMap<>();
      userCourses.put(JsonKey.USER_ID, userId);
      userCourses.putAll(userCoursesCommon);

      count++;
      records.add(userCourses);
      if (count > CASSANDRA_BATCH_SIZE) {
        performBatchInsert(requestContext, records);
        syncUsersToES(requestContext, records);
        records.clear();
        count = 0;
      }
      if (count != 0) {
        performBatchInsert(requestContext, records);
        syncUsersToES(requestContext, records);
        records.clear();
        count = 0;
      }
    }
  }

  private void syncUsersToES(RequestContext requestContext, List<Map<String, Object>> records) {

    for (Map<String, Object> userCourses : records) {
      sync(requestContext,
          userCourses,
          (String) userCourses.get(JsonKey.BATCH_ID),
          (String) userCourses.get(JsonKey.USER_ID));
    }
  }

  protected void performBatchInsert(RequestContext requestContext, List<Map<String, Object>> records) {
    try {
      userCourseDao.batchInsert(requestContext, records);
    } catch (Exception ex) {
      logger.error(requestContext, "UserCoursesService:performBatchInsert: Performing retry due to exception = "
              + ex.getMessage(), ex);
      for (Map<String, Object> task : records) {
        try {
          userCourseDao.insertV2(requestContext, task);
        } catch (Exception exception) {
          logger.error(requestContext, "UserCoursesService:performBatchInsert: Exception occurred with error message = "
                          + exception.getMessage()
                          + " for ID = "
                          + task.get(JsonKey.ID),
                  exception);
        }
      }
    }
  }

  public void unenroll(RequestContext requestContext, String batchId, String userId) {
    UserCourses userCourses = userCourseDao.read(requestContext, batchId, userId);
    validateUserUnenroll(requestContext, userCourses);
    Map<String, Object> updateAttributes = new HashMap<>();
    updateAttributes.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.INACTIVE.getValue());
    userCourseDao.update(requestContext, userCourses.getBatchId(), userCourses.getUserId(), updateAttributes);
    sync(requestContext, updateAttributes, userCourses.getBatchId(), userCourses.getUserId());
  }

  public Map<String, Object> getActiveEnrollments(String userId) {
    Map<String, Object> filter = new HashMap<>();
    filter.put(JsonKey.USER_ID, userId);
    filter.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
    SearchDTO searchDto = new SearchDTO();
    searchDto.getAdditionalProperties().put(JsonKey.FILTERS, filter);
    Future<Map<String, Object>> resultF =
        esService.search(requestContext, searchDto, ProjectUtil.EsType.usercourses.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    return result;
  }

  public static void sync(RequestContext requestContext, Map<String, Object> courseMap, String batchId, String userId) {
    String id = generateUserCourseESId(batchId, userId);
    courseMap.put(JsonKey.ID, id);
    courseMap.put(JsonKey.IDENTIFIER, id);
    Future<Boolean> responseF =
        esService.upsert(requestContext, ProjectUtil.EsType.usercourses.getTypeName(), id, courseMap);
    boolean response = (boolean) ElasticSearchHelper.getResponseFromFuture(responseF);
  }

  public List<String> getEnrolledUserFromBatch(RequestContext requestContext, String batchId) {

    return userCourseDao.getAllActiveUserOfBatch(requestContext, batchId);
  }

  public Integer getBatchSize(String key) {
    Integer batchSize = ProjectUtil.DEFAULT_BATCH_SIZE;
    try {
      batchSize = Integer.parseInt(ProjectUtil.getConfigValue(key));
    } catch (Exception ex) {
    }
    return batchSize;
  }

  public List<String> getParticipantsList(String batchId, boolean active, RequestContext requestContext) {
    return userCourseDao.getBatchParticipants(requestContext, batchId, active);
  }
}
