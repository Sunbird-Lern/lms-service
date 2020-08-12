package org.sunbird.learner.actors.coursebatch.dao;

import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.course.batch.CourseBatch;

public interface CourseBatchDao {

  /**
   * Create course batch.
   *
   * @param courseBatch Course batch information to be created
   * @param requestContext
   * @return Response containing identifier of created course batch
   */
  Response create(CourseBatch courseBatch, RequestContext requestContext);

  /**
   * Update course batch.
   *
   * @param courseBatchMap Course batch information to be updated
   * @return Response containing status of course batch update
   */
  Response update(String courseId, String batchId, Map<String, Object> courseBatchMap, RequestContext requestContext);

  /**
   * Read course batch for given identifier.
   *
   * @param courseBatchId Course batch identifier
   * @return Course batch information
   */
  CourseBatch readById(String courseId, String batchId, RequestContext requestContext);

  Map<String, Object> getCourseBatch(String courseId, String batchId, RequestContext requestContext);

  /**
   * Delete specified course batch.
   *
   * @param courseBatchId Course batch identifier
   * @param requestContext
   * @return Response containing status of course batch delete
   */
  Response delete(String courseBatchId, RequestContext requestContext);

  /**
   * Attaches a certificate template to course batch
   *  @param courseId
   * @param batchId
   * @param templateId
   * @param templateDetails
   * @param requestContext
   */
  void addCertificateTemplateToCourseBatch(
          String courseId, String batchId, String templateId, Map<String, Object> templateDetails, RequestContext requestContext);

  /**
   * Removes an attached certificate template from course batch
   *  @param courseId
   * @param batchId
   * @param templateId
   * @param requestContext
   */
  void removeCertificateTemplateFromCourseBatch(String courseId, String batchId, String templateId, RequestContext requestContext);
}
