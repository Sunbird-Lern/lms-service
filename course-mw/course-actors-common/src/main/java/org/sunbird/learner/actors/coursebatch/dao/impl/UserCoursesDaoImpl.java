package org.sunbird.learner.actors.coursebatch.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.courses.UserCourses;

public class UserCoursesDaoImpl implements UserCoursesDao {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  static UserCoursesDao userCoursesDao;
  private static final String KEYSPACE_NAME =
      Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB).getKeySpace();
  private static final String TABLE_NAME =
      Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB).getTableName();
  private static final String USER_ENROLMENTS = "user_enrolments";
  public static UserCoursesDao getInstance() {
    if (userCoursesDao == null) {
      userCoursesDao = new UserCoursesDaoImpl();
    }
    return userCoursesDao;
  }
  
  @Override
  public UserCourses read(String batchId, String userId, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    primaryKey.put(JsonKey.USER_ID, userId);
    Response response = cassandraOperation.getRecordByIdentifier(KEYSPACE_NAME, TABLE_NAME, primaryKey, null, requestContext);
    List<Map<String, Object>> userCoursesList =
        (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    }
    try {
      return mapper.convertValue((Map<String, Object>) userCoursesList.get(0), UserCourses.class);
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return null;
  }


  @Override
  public Response update(String batchId, String userId, Map<String, Object> updateAttributes, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    primaryKey.put(JsonKey.USER_ID, userId);
    Map<String, Object> updateList = new HashMap<>();
    updateList.putAll(updateAttributes);
    updateList.remove(JsonKey.BATCH_ID);
    updateList.remove(JsonKey.USER_ID);
    return cassandraOperation.updateRecord(KEYSPACE_NAME, TABLE_NAME, updateList, primaryKey, requestContext);
  }

  @Override
  public List<String> getAllActiveUserOfBatch(String batchId, RequestContext requestContext) {
    return getBatchParticipants(batchId, true, requestContext);
  }

  @Override
  public Response batchInsert(List<Map<String, Object>> userCoursesDetails, RequestContext requestContext) {
    return cassandraOperation.batchInsert(KEYSPACE_NAME, USER_ENROLMENTS, userCoursesDetails, requestContext);
  }

  @Override
  public Response insert(Map<String, Object> userCoursesDetails, RequestContext requestContext) {
    return cassandraOperation.insertRecord(KEYSPACE_NAME, TABLE_NAME, userCoursesDetails, requestContext);
  }

  @Override
  public Response insertV2(Map<String, Object> userCoursesDetails, RequestContext requestContext) {
    return cassandraOperation.insertRecord(KEYSPACE_NAME, USER_ENROLMENTS, userCoursesDetails, requestContext);
  }

  @Override
  public Response updateV2(String userId, String courseId, String batchId,  Map<String, Object> updateAttributes, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Map<String, Object> updateList = new HashMap<>();
    updateList.putAll(updateAttributes);
    updateList.remove(JsonKey.BATCH_ID);
    updateList.remove(JsonKey.COURSE_ID);
    updateList.remove(JsonKey.USER_ID);
    return cassandraOperation.updateRecord(KEYSPACE_NAME, USER_ENROLMENTS, updateList, primaryKey, requestContext);
  }

  @Override
  public UserCourses read(String userId, String courseId, String batchId, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Response response = cassandraOperation.getRecordByIdentifier(KEYSPACE_NAME, USER_ENROLMENTS, primaryKey, null, requestContext);
    List<Map<String, Object>> userCoursesList =
            (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    }
    try {
      return mapper.convertValue((Map<String, Object>) userCoursesList.get(0), UserCourses.class);
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return null;
  }

  @Override
  public List<String> getBatchParticipants(String batchId, boolean active, RequestContext requestContext) {
    Map<String, Object> queryMap = new HashMap<>();
    queryMap.put(JsonKey.BATCH_ID, batchId);
    Response response =
        cassandraOperation.getRecords(
            KEYSPACE_NAME, USER_ENROLMENTS, queryMap, Arrays.asList(JsonKey.USER_ID, JsonKey.ACTIVE), requestContext);
    List<Map<String, Object>> userCoursesList =
        (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    }
    return userCoursesList
        .stream()
        .filter(userCourse -> (active == (boolean) userCourse.get(JsonKey.ACTIVE)))
        .map(userCourse -> (String) userCourse.get(JsonKey.USER_ID))
        .collect(Collectors.toList());
  }

  @Override
  public List<Map<String, Object>> listEnrolments(String userId, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    Response response = cassandraOperation.getRecordByIdentifier(KEYSPACE_NAME, USER_ENROLMENTS, primaryKey, null, requestContext);
    List<Map<String, Object>> userCoursesList = (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    } else {
      return userCoursesList;
    }
  }
}
