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
  UserCourses read(String batchId, String userId, RequestContext requestContext);

  UserCourses read(String userId, String courseId, String batchId, RequestContext requestContext);

  /**
   * Create an entry for user courses information
   *
   * @param userCoursesDetails User courses information
   * @param requestContext
   */
  Response insert(Map<String, Object> userCoursesDetails, RequestContext requestContext);

  Response insertV2(Map<String, Object> userCoursesDetails, RequestContext requestContext);

  /**
   * Update user courses information
   *
   * @param updateAttributes Map containing user courses attributes which needs to be updated
   * @param requestContext
   */
  Response update(String batchId, String userId, Map<String, Object> updateAttributes, RequestContext requestContext);

  Response updateV2(String userId,String courseId, String batchId, Map<String, Object> updateAttributes, RequestContext requestContext);

  /**
   * Get all active participant IDs in given batch
   *
   * @param batchId Batch ID
   * @param requestContext
   */
  List<String> getAllActiveUserOfBatch(String batchId, RequestContext requestContext);

  /**
   * Add specified list of participants in given batch.
   *
   * @param userCoursesDetails List of participant details
   * @param requestContext
   */
  Response batchInsert(List<Map<String, Object>> userCoursesDetails, RequestContext requestContext);

  /**
   * Get all active participant IDs in given batch
   *  @param batchId Batch ID
   * @param active
   * @param requestContext
   */
  List<String> getBatchParticipants(String batchId, boolean active, RequestContext requestContext);
  
  
  List<Map<String, Object>> listEnrolments(String userId, RequestContext requestContext);
}
