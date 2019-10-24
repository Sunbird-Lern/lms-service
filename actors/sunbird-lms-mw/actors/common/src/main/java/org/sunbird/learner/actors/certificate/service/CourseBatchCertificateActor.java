/** */
package org.sunbird.learner.actors.certificate.service;

import java.util.Map;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.Util;

@ActorConfig(
  tasks = {"addCertificateToCourseBatch", "removeCertificateFromCourseBatch"},
  asyncTasks = {}
)
public class CourseBatchCertificateActor extends BaseActor {

  private CourseBatchDao courseBatchDao = new CourseBatchDaoImpl();

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    ExecutionContext.setRequestId(request.getRequestId());

    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "addCertificateToCourseBatch":
        addCertificateTemplateToCourseBatch(request);
        break;
      case "removeCertificateFromCourseBatch":
        removeCertificateTemplateFromCourseBatch(request);
        break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
    }
  }

  private void addCertificateTemplateToCourseBatch(Request request) {
    Map<String, Object> batchRequest =
        (Map<String, Object>) request.getRequest().get(JsonKey.BATCH);
    final String batchId = (String) batchRequest.get(JsonKey.BATCH_ID);
    final String courseId = (String) batchRequest.get(JsonKey.COURSE_ID);
    CourseBatchUtil.validateCourseBatch(courseId, batchId);
    Map<String, Object> template = (Map<String, Object>) batchRequest.get(CourseJsonKey.TEMPLATE);
    String templateId = (String) template.get(JsonKey.IDENTIFIER);
    validateTemplateDetails(templateId, template);
    courseBatchDao.addCertificateTemplateToCourseBatch(courseId, batchId, templateId, template);
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }

  private void removeCertificateTemplateFromCourseBatch(Request request) {
    Map<String, Object> batchRequest =
        (Map<String, Object>) request.getRequest().get(JsonKey.BATCH);
    final String batchId = (String) batchRequest.get(JsonKey.BATCH_ID);
    final String courseId = (String) batchRequest.get(JsonKey.COURSE_ID);
    CourseBatchUtil.validateCourseBatch(courseId, batchId);
    Map<String, Object> template = (Map<String, Object>) batchRequest.get(CourseJsonKey.TEMPLATE);
    String templateId = (String) template.get(JsonKey.IDENTIFIER);
    courseBatchDao.removeCertificateTemplateFromCourseBatch(courseId, batchId, templateId);
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }

  private void validateTemplateDetails(String templateId, Map<String, Object> template) {}
}
