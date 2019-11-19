package org.sunbird.learner.actors.coursebatch.dao;

import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.models.course.batch.CourseBatch;

public interface CourseBatchDao {

  /**
   * Create course batch.
   *
   * @param courseBatch Course batch information to be created
   * @return Response containing identifier of created course batch
   */
  Response create(CourseBatch courseBatch);

  /**
   * Update course batch.
   *
   * @param courseBatchMap Course batch information to be updated
   * @return Response containing status of course batch update
   */
  Response update(String courseId, String batchId, Map<String, Object> courseBatchMap);

  /**
   * Read course batch for given identifier.
   *
   * @param courseBatchId Course batch identifier
   * @return Course batch information
   */
  CourseBatch readById(String courseId, String batchId);

  Map<String, Object> getCourseBatch(String courseId, String batchId);

  /**
   * Delete specified course batch.
   *
   * @param courseBatchId Course batch identifier
   * @return Response containing status of course batch delete
   */
  Response delete(String courseBatchId);

  /**
   * Attaches a certificate template to course batch
   *
   * @param courseId
   * @param batchId
   * @param templateId
   * @param templateDetails
   */
  void addCertificateTemplateToCourseBatch(
      String courseId, String batchId, String templateId, Map<String, Object> templateDetails);

  /**
   * Removes an attached certificate template from course batch
   *
   * @param courseId
   * @param batchId
   * @param templateId
   */
  void removeCertificateTemplateFromCourseBatch(String courseId, String batchId, String templateId);
}
