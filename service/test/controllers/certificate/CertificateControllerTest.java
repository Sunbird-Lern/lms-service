package controllers.certificate;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.learner.constants.CourseJsonKey;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class CertificateControllerTest extends BaseApplicationTest {
  private static final String COURSE_ID = "courseId";
  private static final String BATCH_ID = "batchId";
  private static final String CERTIFICATE_NAME = "certificateName";
  private static final String TEMPLATE_ID = "templateId";
  private static final String CERTIFICATE = "certificate";
  private static final String ISSUE_CERTIFICATE_URL = "/v1/course/batch/cert/issue";
  private static final String ADD_CERTIFICATE_URL = "/v1/course/batch/cert/template/add";
  private static final String DELETE_CERTIFICATE_URL = "/v1/course/batch/cert/template/remove";
  private static final String TEST = "Test";
  ObjectMapper mapper = new ObjectMapper();

  @Before
  public void before() {
    setup(DummyActor.class);
  }

  @Test
  public void issueCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void reIssueCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL + "?reIssue=true")
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void issueCertificateWithoutCourseTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(null, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void issueCertificateWithoutBatchTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, null, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void issueCertificateWithoutCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, null))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  private JsonNode getIssueCertificateRequest(String courseId, String batchId, String certificate) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) innerMap.put(JsonKey.BATCH_ID, batchId);
    if (certificate != null) innerMap.put(CERTIFICATE, certificate);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  @Test
  public void addCertificateTestWithoutCourseId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    null, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void addCertificateTestWithoutCertificateName() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, null, TEMPLATE_ID, true, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutCertificateTemplate() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, false, CERTIFICATE_NAME, TEMPLATE_ID, false, false, false))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutCriteria() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, false, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutBatchId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, null, true, CERTIFICATE_NAME, TEMPLATE_ID, true, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutTemplateId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, null, true, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutSignatoryList() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, false, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void addCertificateTestWithoutIssuer() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, true, false))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void deleteCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(DELETE_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, BATCH_ID, true, TEMPLATE_ID))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void deleteCertificateWithoutCourseId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(DELETE_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(null, BATCH_ID, true, TEMPLATE_ID))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void deleteCertificateTestWithoutBatchId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(DELETE_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, null, true, TEMPLATE_ID))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void deleteCertificateTestWithoutTemplate() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(DELETE_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, null, false, null))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void deleteCertificateTestWithoutTemplateId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(DELETE_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, null, true, null))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  private JsonNode getAddCertificateRequest(
      String courseId,
      String batchId,
      boolean certificateTemplate,
      String certificateName,
      String templateId,
      boolean criteria,
      boolean signatoryList,
      boolean issuer) {
    Map<String, Object> innerMap = new HashMap<>();
    Map<String, Object> batch = new HashMap<>();
    if (courseId != null) batch.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) batch.put(JsonKey.BATCH_ID, batchId);
    Map<String, Object> template = new HashMap<>();
    if (certificateTemplate) {
      batch.put(CourseJsonKey.TEMPLATE, template);
      if (certificateName != null) template.put(JsonKey.NAME, certificateName);
      if (templateId != null) template.put(JsonKey.IDENTIFIER, templateId);
      if (criteria) {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put(JsonKey.STATUS, 2);
        Map<String, Object> criteriaMap = new HashMap<>();
        criteriaMap.put(CourseJsonKey.ENROLLMENT, statusMap);
        template.put(JsonKey.CRITERIA, criteriaMap);
      }
      if (signatoryList) template.put(CourseJsonKey.SIGNATORY_LIST, new ArrayList<>());
      if (issuer) template.put(CourseJsonKey.ISSUER, new HashMap<String, Object>());
    }
    Map<String, Object> requestMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH, batch);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode getCertificateRequest(
      String courseId, String batchId, boolean certificateTemplate, String templateId) {
    Map<String, Object> innerMap = new HashMap<>();
    Map<String, Object> batch = new HashMap<>();
    if (courseId != null) batch.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) batch.put(JsonKey.BATCH_ID, batchId);
    Map<String, Object> template = new HashMap<>();
    if (certificateTemplate) {
      batch.put(CourseJsonKey.TEMPLATE, template);
      if (templateId != null) template.put(JsonKey.IDENTIFIER, templateId);
    }
    Map<String, Object> requestMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH, batch);
    requestMap.put(JsonKey.REQUEST, innerMap);
    System.out.println(requestMap.toString());
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  public String mapToJson(Map map) {
    ObjectMapper mapperObj = new ObjectMapper();
    String jsonResp = "";
    if (map != null) {
      try {
        jsonResp = mapperObj.writeValueAsString(map);
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    return jsonResp;
  }
}
