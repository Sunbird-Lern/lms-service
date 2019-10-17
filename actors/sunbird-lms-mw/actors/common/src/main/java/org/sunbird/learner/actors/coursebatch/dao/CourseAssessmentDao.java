package org.sunbird.learner.actors.coursebatch.dao;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface CourseAssessmentDao {

  /**
   * Fetches all the assessments for a user in course batch
   *
   * @param courseId
   * @param batchId
   * @param userId
   * @return
   */
  List<Map<String, Object>> fetchCourseBatchUserAssessments(
      String courseId, String batchId, String userId);

  /**
   * Fetches filtered user with assessment for certain course and batch
   *
   * @param courseId
   * @param batchId
   * @param filter override test method for filtration logic
   * @return
   */
  Set<String> fetchFilteredAssessmentsCourseBatchUsers(
      String courseId, String batchId, Predicate<Map<String, Object>> filter);
}
