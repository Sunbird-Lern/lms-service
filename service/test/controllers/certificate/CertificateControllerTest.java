package controllers.certificate;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import java.io.IOException;
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
  private static final String CERTIFICATE_TEMPLATE = "certificateTemplate";
  private static final String CERTIFICATE = "certificate";
  private static final String ISSUE_CERTIFICATE_URL = "/v1/course/batch/cert/issue";
  private static final String ADD_CERTIFICATE_URL = "/v1/course/batch/cert/template";
  private static final String GET_CERTIFICATE_URL = "/v1/course/batch/cert/template/list";
  private static final String TEST = "Test";

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
            .bodyJson(getAddCertificateRequest(null, BATCH_ID, CERTIFICATE_NAME, true))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getAddCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE_NAME, true))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void addCertificateTestWithoutCertificateName() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getAddCertificateRequest(COURSE_ID, BATCH_ID, null, true))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutCertificateTemplate() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getAddCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE_NAME, false))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTestWithoutBatchId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getAddCertificateRequest(COURSE_ID, null, CERTIFICATE_NAME, true))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void getCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(GET_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, CERTIFICATE_NAME))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void getCertificateTestWithoutCourseId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(GET_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(null, CERTIFICATE_NAME))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void getCertificateTestWithoutName() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(GET_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, null))
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void deleteCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, CERTIFICATE_NAME))
            .method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void deleteCertificateWithoutCourseId() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(null, CERTIFICATE_NAME))
            .method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void deleteCertificateTestWithoutName() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(getCertificateRequest(COURSE_ID, null))
            .method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  private JsonNode getAddCertificateRequest(
      String courseId, String batchId, String certificateName, boolean certificateTemplate) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) innerMap.put(JsonKey.BATCH_ID, batchId);
    if (certificateName != null) innerMap.put(JsonKey.NAME, certificateName);
    if (certificateTemplate) innerMap.put(CourseJsonKey.TEMPLATE, new HashMap<String, Object>());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode getCertificateRequest(String courseId, String certificateName) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (certificateName != null) innerMap.put(JsonKey.NAME, certificateName);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
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
