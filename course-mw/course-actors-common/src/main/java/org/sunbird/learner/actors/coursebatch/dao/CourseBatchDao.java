package org.sunbird.learner.actors.coursebatch.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.course.batch.CourseBatch;

public interface CourseBatchDao {

  /**
   * Create course batch.
   *
   * @param requestContext
   * @param courseBatch Course batch information to be created
   * @return Response containing identifier of created course batch
   */
  Response create(RequestContext requestContext, CourseBatch courseBatch);

  /**
   * Update course batch.
   *
   * @param courseBatchMap Course batch information to be updated
   * @return Response containing status of course batch update
   */
  Response update(RequestContext requestContext, String courseId, String batchId, Map<String, Object> courseBatchMap);

  /**
   * Read course batch for given identifier.
   *
   * @param courseBatchId Course batch identifier
   * @return Course batch information
   */
  CourseBatch readById(String courseId, String batchId, RequestContext requestContext);

  Map<String, Object> getCourseBatch(RequestContext requestContext, String courseId, String batchId);

  /**
   * Delete specified course batch.
   *
   * @param requestContext
   * @param courseBatchId Course batch identifier
   * @return Response containing status of course batch delete
   */
  Response delete(RequestContext requestContext, String courseBatchId);

  /**
   * Attaches a certificate template to course batch
   * @param requestContext
   * @param courseId
   * @param batchId
   * @param templateId
   * @param templateDetails
   */
  void addCertificateTemplateToCourseBatch(
          RequestContext requestContext, String courseId, String batchId, String templateId, Map<String, Object> templateDetails);

  /**
   * Removes an attached certificate template from course batch
   * @param requestContext
   * @param courseId
   * @param batchId
   * @param templateId
   */
  void removeCertificateTemplateFromCourseBatch(RequestContext requestContext, String courseId, String batchId, String templateId);

  /**
   * Get all user enrolments between given date range
   * @param requestContext
   * @param fromDate from date
   * @param toDate to date
   */
  List<Map<String, Object>> listBatchesBetweenDateRange(RequestContext requestContext, Date fromDate, Date toDate);

  /**
   * Get course batches for given courseId
   *
   * @param courseId the course id
   * @param requestContext the request context
   * @return the course batches
   */
  List<Map<String, Object>> readById(String courseId, RequestContext requestContext);
}
