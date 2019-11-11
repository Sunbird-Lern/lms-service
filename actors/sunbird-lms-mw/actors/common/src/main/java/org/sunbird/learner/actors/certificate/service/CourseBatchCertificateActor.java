/** */
package org.sunbird.learner.actors.certificate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.MapUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ActorConfig(
  tasks = {"addCertificateToCourseBatch", "removeCertificateFromCourseBatch"},
  asyncTasks = {}
)
public class CourseBatchCertificateActor extends BaseActor {

  private CourseBatchDao courseBatchDao = new CourseBatchDaoImpl();
  private ObjectMapper mapper = new ObjectMapper();

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
    Map<String,Object> batchresult = CourseBatchUtil.validateCourseBatch(courseId, batchId);
    Map<String, Object> template = (Map<String, Object>) batchRequest.get(CourseJsonKey.TEMPLATE);
    String templateId = (String) template.get(JsonKey.IDENTIFIER);
    validateTemplateDetails(templateId, template, batchresult);
    courseBatchDao.addCertificateTemplateToCourseBatch(courseId, batchId, templateId, template);
    Map<String,Object> courseBatch =mapESFieldsToObject(courseBatchDao.getCourseBatch(courseId,batchId));
    CourseBatchUtil.syncCourseBatchForeground(
            batchId,courseBatch);
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
    CourseBatchUtil.validateTemplate(templateId);
    courseBatchDao.removeCertificateTemplateFromCourseBatch(courseId, batchId, templateId);
    Map<String,Object> courseBatch =mapESFieldsToObject(courseBatchDao.getCourseBatch(courseId,batchId));
    CourseBatchUtil.syncCourseBatchForeground(
            batchId,courseBatch);
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }

  private void validateTemplateDetails(String templateId, Map<String, Object> template, Map<String,Object> batchDetails) {
     Map<String, Object> templateDetails=CourseBatchUtil.validateTemplate(templateId);
      String currentCertificateName = (String) templateDetails.get(JsonKey.NAME);
      Map<String,Object> certTemplates =(Map<String,Object>) batchDetails.get(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN);
      if (MapUtils.isNotEmpty(certTemplates)) {
          Set<String> existingcertificateNames =certTemplates.entrySet().stream().map(certificate ->(String) ((Map<String,Object>) certificate.getValue()).get(JsonKey.NAME)).collect(Collectors.toSet());
          if(existingcertificateNames.contains(currentCertificateName)){
              ProjectCommonException.throwClientErrorException(
                      ResponseCode.CLIENT_ERROR,"Certificate with the name "+currentCertificateName+" already exists");
          }
      }
      template.put(JsonKey.NAME,currentCertificateName);
    try {
       template.put(JsonKey.CRITERIA, mapper.writeValueAsString(template.get(JsonKey.CRITERIA)));
      if (template.get(CourseJsonKey.ISSUER) != null) {
        template.put(
            CourseJsonKey.ISSUER, mapper.writeValueAsString(template.get(CourseJsonKey.ISSUER)));
      }
      if (template.get(CourseJsonKey.SIGNATORY_LIST) != null) {
        template.put(
            CourseJsonKey.SIGNATORY_LIST,
            mapper.writeValueAsString(template.get(CourseJsonKey.SIGNATORY_LIST)));
      }
    } catch (JsonProcessingException ex) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.invalidData,
          "Error in parsing template data, Please check "
              + JsonKey.CRITERIA
              + ","
              + CourseJsonKey.ISSUER
              + " and "
              + CourseJsonKey.SIGNATORY_LIST
              + " fields");
    }
  }

  private Map<String,Object> mapESFieldsToObject(Map<String,Object> courseBatch){
    Map<String,Map<String,Object>> certificateTemplates = (Map<String,Map<String,Object>>)courseBatch.get(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN);
    certificateTemplates.entrySet().stream().forEach(cert_template -> certificateTemplates.put(cert_template.getKey(),mapToObject(cert_template.getValue())));
    courseBatch.put(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN, certificateTemplates);
    return courseBatch;
  }

  private Map<String, Object> mapToObject(Map<String, Object> template) {
    try {
      template.put(
              JsonKey.CRITERIA,
              mapper.readValue(
                      (String) template.get(JsonKey.CRITERIA),
                      new TypeReference<HashMap<String, Object>>() {
                      }));
      template.put(
              CourseJsonKey.SIGNATORY_LIST,
              mapper.readValue(
                      (String) template.get(CourseJsonKey.SIGNATORY_LIST),
                      new TypeReference<List<Object>>() {
                      }));
      template.put(
              CourseJsonKey.ISSUER,
              mapper.readValue(
                      (String) template.get(CourseJsonKey.ISSUER),
                      new TypeReference<HashMap<String, Object>>() {
                      }));
    } catch (Exception ex) {
      ProjectLogger.log(
              "CourseBatchCertificateActor:mapToObject Exception occurred with error message ==", ex);
    }
    return template;
  }
}
