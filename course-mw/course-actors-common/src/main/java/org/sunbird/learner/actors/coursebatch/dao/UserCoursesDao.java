package org.sunbird.learner.actors.coursebatch.dao;

import java.util.List;
import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.user.courses.UserCourses;

public interface UserCoursesDao {

  /**
   * Get user courses information.
   *
   * @param batchId,userId user courses identifiers
   * @param requestContext
   * @return User courses information
   */
  UserCourses read(RequestContext requestContext, String batchId, String userId);

  UserCourses read(RequestContext requestContext, String userId, String courseId, String batchId);

  /**
   * Create an entry for user courses information
   *
   * @param requestContext
   * @param userCoursesDetails User courses information
   */
  Response insert(RequestContext requestContext, Map<String, Object> userCoursesDetails);

  Response insertV2(RequestContext requestContext, Map<String, Object> userCoursesDetails);

  /**
   * Update user courses information
   *
   * @param requestContext
   * @param updateAttributes Map containing user courses attributes which needs to be updated
   */
  Response update(RequestContext requestContext, String batchId, String userId, Map<String, Object> updateAttributes);

  Response updateV2(RequestContext requestContext, String userId, String courseId, String batchId, Map<String, Object> updateAttributes);

  /**
   * Get all active participant IDs in given batch
   *
   * @param requestContext
   * @param batchId Batch ID
   */
  List<String> getAllActiveUserOfBatch(RequestContext requestContext, String batchId);

  /**
   * Add specified list of participants in given batch.
   *
   * @param requestContext
   * @param userCoursesDetails List of participant details
   */
  Response batchInsert(RequestContext requestContext, List<Map<String, Object>> userCoursesDetails);

  /**
   * Get all active participant IDs in given batch
   * @param requestContext
   * @param batchId Batch ID
   * @param active
   */
  List<String> getBatchParticipants(RequestContext requestContext, String batchId, boolean active);
  
  
  List<Map<String, Object>> listEnrolments(RequestContext requestContext, String userId, List<String> courseIdList);
}
