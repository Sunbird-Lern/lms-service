package org.sunbird.learner.actors.coursebatch.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.CassandraUtil;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.RequestContext;
import org.sunbird.response.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.models.course.batch.CourseBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourseBatchDaoImpl implements CourseBatchDao {
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private Util.DbInfo courseBatchDb = Util.dbInfoMap.get(JsonKey.COURSE_BATCH_DB);
  // private static final CassandraPropertyReader propertiesCache =
  //         CassandraPropertyReader.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  private String dateFormat = "yyyy-MM-dd";


  @Override
  public Response create(RequestContext requestContext, CourseBatch courseBatch) {
    Map<String, Object> map = CourseBatchUtil.cassandraCourseMapping(courseBatch, dateFormat);
    map = CassandraUtil.changeCassandraColumnMapping(map);
    return cassandraOperation.insertRecord(
            courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), map, requestContext);
  }

  @Override
  public Response update(RequestContext requestContext, String courseId, String batchId, Map<String, Object> map) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Map<String, Object> attributeMap = new HashMap<>();
    attributeMap.putAll(map);
    attributeMap.remove(JsonKey.COURSE_ID);
    attributeMap.remove(JsonKey.BATCH_ID);
    attributeMap = CassandraUtil.changeCassandraColumnMapping(attributeMap);
    return cassandraOperation.updateRecord(
            courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), attributeMap, primaryKey, requestContext);
  }

  @Override
  public CourseBatch readById(String courseId, String batchId, RequestContext requestContext) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Response courseBatchResult =
        cassandraOperation.getRecordByIdentifier(
                courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), primaryKey, null, requestContext);
    List<Map<String, Object>> courseList =
        (List<Map<String, Object>>) courseBatchResult.get(JsonKey.RESPONSE);
    if (courseList.isEmpty()) {
      throw new ProjectCommonException(
          ResponseCode.invalidCourseBatchId.getErrorCode(),
          ResponseCode.invalidCourseBatchId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    } else {
      courseList.get(0).remove(JsonKey.PARTICIPANT);
      return mapper.convertValue(courseList.get(0), CourseBatch.class);
    }
  }

  @Override
  public Map<String, Object> getCourseBatch(RequestContext requestContext, String courseId, String batchId) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Response courseBatchResult =
        cassandraOperation.getRecordByIdentifier(
                courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), primaryKey, null, requestContext);
    List<Map<String, Object>> courseList =
        (List<Map<String, Object>>) courseBatchResult.get(JsonKey.RESPONSE);
    return courseList.get(0);
  }

  @Override
  public Response delete(RequestContext requestContext, String id) {
    return cassandraOperation.deleteRecord(
        courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), id, requestContext);
  }

  @Override
  public void addCertificateTemplateToCourseBatch(
          RequestContext requestContext, String courseId, String batchId, String templateId, Map<String, Object> templateDetails) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    cassandraOperation.updateAddMapRecord(
            courseBatchDb.getKeySpace(),
        courseBatchDb.getTableName(),
        primaryKey,
        CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN,
        templateId,
        templateDetails, requestContext);
  }

  @Override
  public void removeCertificateTemplateFromCourseBatch(
          RequestContext requestContext, String courseId, String batchId, String templateId) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    cassandraOperation.updateRemoveMapRecord(
            courseBatchDb.getKeySpace(),
        courseBatchDb.getTableName(),
        primaryKey,
        CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN,
        templateId, requestContext);
  }
}
