package org.sunbird.learner.actors.coursebatch.dao.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.CourseAssessmentDao;
import org.sunbird.learner.util.Util;

public class CourseAssessmentDaoImpl implements CourseAssessmentDao {

  private static final String ASSESSMENT_AGGREGATOR = "assessment_aggregator";
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();

  @Override
  public List<Map<String, Object>> fetchCourseBatchUserAssessments(
      String courseId, String batchId, String userId) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    primaryKey.put(JsonKey.USER_ID, batchId);
    Response courseAssessmentResult =
        cassandraOperation.getRecordById(
            Util.COURSE_KEY_SPACE_NAME, ASSESSMENT_AGGREGATOR, primaryKey);
    List<Map<String, Object>> courseAssessmentList =
        (List<Map<String, Object>>) courseAssessmentResult.get(JsonKey.RESPONSE);
    return courseAssessmentList;
  }

  @Override
  public Set<String> fetchFilteredAssessmentsCourseBatchUsers(
      String courseId, String batchId, Predicate<Map<String, Object>> filter) {
    Map<String, Object> primaryKey = new HashMap<>();
    primaryKey.put(JsonKey.COURSE_ID, courseId);
    primaryKey.put(JsonKey.BATCH_ID, batchId);
    Response courseAssessmentResult =
        cassandraOperation.getRecordById(
            Util.COURSE_KEY_SPACE_NAME, ASSESSMENT_AGGREGATOR, primaryKey);
    List<Map<String, Object>> courseAssessmentList =
        (List<Map<String, Object>>) courseAssessmentResult.get(JsonKey.RESPONSE);
    Set<String> userIds = new HashSet<>();
    if (CollectionUtils.isEmpty(courseAssessmentList)) {
      return userIds;
    }
    return courseAssessmentList
        .stream()
        .filter(filter)
        .map(courseAssessment -> (String) courseAssessment.get(JsonKey.USER_ID))
        .collect(Collectors.toSet());
  }
}
