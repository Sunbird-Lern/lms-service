package org.sunbird.learner.actors.coursebatch.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
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
  private static final String USER_ENROLMENTS = Util.dbInfoMap.get(JsonKey.USER_ENROLMENTS_DB).getTableName();
  public static UserCoursesDao getInstance() {
    if (userCoursesDao == null) {
      userCoursesDao = new UserCoursesDaoImpl();
    }
    return userCoursesDao;
  }
  
  @Override
  public UserCourses read(RequestContext requestContext, String batchId, String userId) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    primaryKey.put(JsonKey.USER_ID, userId);
    Response response = cassandraOperation.getRecordByIdentifier(requestContext, KEYSPACE_NAME, TABLE_NAME, primaryKey, null);
    List<Map<String, Object>> userCoursesList =
        (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    }
    try {
      return mapper.convertValue((Map<String, Object>) userCoursesList.get(0), UserCourses.class);
    } catch (Exception e) {
    }
    return null;
  }


  @Override
  public Response update(RequestContext requestContext, String batchId, String userId, Map<String, Object> updateAttributes) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    primaryKey.put(JsonKey.USER_ID, userId);
    Map<String, Object> updateList = new HashMap<>();
    updateList.putAll(updateAttributes);
    updateList.remove(JsonKey.BATCH_ID);
    updateList.remove(JsonKey.USER_ID);
    return cassandraOperation.updateRecord(requestContext, KEYSPACE_NAME, TABLE_NAME, updateList, primaryKey);
  }

  @Override
  public List<String> getAllActiveUserOfBatch(RequestContext requestContext, String batchId) {
    return getBatchParticipants(requestContext, batchId, true);
  }

  @Override
  public Response batchInsert(RequestContext requestContext, List<Map<String, Object>> userCoursesDetails) {
    return cassandraOperation.batchInsert(requestContext, KEYSPACE_NAME, USER_ENROLMENTS, userCoursesDetails);
  }

  @Override
  public Response insert(RequestContext requestContext, Map<String, Object> userCoursesDetails) {
    return cassandraOperation.insertRecord(requestContext, KEYSPACE_NAME, TABLE_NAME, userCoursesDetails);
  }

  @Override
  public Response insertV2(RequestContext requestContext, Map<String, Object> userCoursesDetails) {
    return cassandraOperation.insertRecord(requestContext, KEYSPACE_NAME, USER_ENROLMENTS, userCoursesDetails);
  }

  @Override
  public Response updateV2(RequestContext requestContext, String userId, String courseId, String batchId, Map<String, Object> updateAttributes) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Map<String, Object> updateList = new HashMap<>();
    updateList.putAll(updateAttributes);
    updateList.remove(JsonKey.BATCH_ID_KEY);
    updateList.remove(JsonKey.COURSE_ID_KEY);
    updateList.remove(JsonKey.USER_ID_KEY);
    return cassandraOperation.updateRecord(requestContext, KEYSPACE_NAME, USER_ENROLMENTS, updateList, primaryKey);
  }

  @Override
  public UserCourses read(RequestContext requestContext, String userId, String courseId, String batchId) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Response response = cassandraOperation.getRecordByIdentifier(requestContext, KEYSPACE_NAME, USER_ENROLMENTS, primaryKey, null);
    List<Map<String, Object>> userCoursesList =
            (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    }
    try {
      return mapper.convertValue((Map<String, Object>) userCoursesList.get(0), UserCourses.class);
    } catch (Exception e) {
    }
    return null;
  }

  @Override
  public List<String> getBatchParticipants(RequestContext requestContext, String batchId, boolean active) {
    Map<String, Object> queryMap = new HashMap<>();
    queryMap.put(JsonKey.BATCH_ID, batchId);
    Response response =
            cassandraOperation.getRecordsByIndexedProperty(KEYSPACE_NAME, USER_ENROLMENTS, "batchid", batchId, requestContext);
        /*cassandraOperation.getRecords(
                requestContext, KEYSPACE_NAME, USER_ENROLMENTS, queryMap, Arrays.asList(JsonKey.USER_ID, JsonKey.ACTIVE));*/
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
  public List<Map<String, Object>> listEnrolments(RequestContext requestContext, String userId, List<String> courseIdList) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.USER_ID, userId);
    if(!CollectionUtils.isEmpty(courseIdList)){
      primaryKey.put(JsonKey.COURSE_ID_KEY, courseIdList);
    }
    Response response = cassandraOperation.getRecordByIdentifier(requestContext, KEYSPACE_NAME, USER_ENROLMENTS, primaryKey, null);
    List<Map<String, Object>> userCoursesList = (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(userCoursesList)) {
      return null;
    } else {
      return userCoursesList;
    }
  }
}
