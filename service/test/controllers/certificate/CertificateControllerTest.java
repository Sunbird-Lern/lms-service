package controllers.certificate;

import actors.DummyActor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
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
import util.ACTOR_NAMES;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class CertificateControllerTest extends BaseApplicationTest {
  String COURSE_ID = "courseId";
  String BATCH_ID = "batchId";
  String CERTIFICATE_NAME = "certificateName";
  String TEMPLATE_ID = "templateId";
  String CERTIFICATE = "certificate";
  String ISSUE_CERTIFICATE_URL = "/v1/course/batch/cert/issue";
  String ADD_CERTIFICATE_URL = "/v1/course/batch/cert/template/add";
  String DELETE_CERTIFICATE_URL = "/v1/course/batch/cert/template/remove";
  String TEST = "Test";
  ObjectMapper mapper = new ObjectMapper();

  @Before
  public void before() {
    setup(Arrays.asList(ACTOR_NAMES.COURSEBATCH_CERTIFICATE_ACTOR,ACTOR_NAMES.CERTIFICATE_ACTOR),DummyActor.class);
  }

  @Test
  public void issueCertificateTest() {
    setup(ACTOR_NAMES.CERTIFICATE_ACTOR,DummyActor.class);
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
    Assert.assertEquals(200, result.status());
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
                    null, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true,false,false, true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void addCertificateTest() {
    setup(ACTOR_NAMES.COURSEBATCH_CERTIFICATE_ACTOR,DummyActor.class);
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, false,false,true, true))
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
                    COURSE_ID, BATCH_ID, true, null, TEMPLATE_ID, true, false,false,true, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void addCertificateTestWithoutCertificateTemplate() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, false, CERTIFICATE_NAME, TEMPLATE_ID, false,false,false, false, false))
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
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, false,false,false, true, true))
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
                    COURSE_ID, null, true, CERTIFICATE_NAME, TEMPLATE_ID, true,false,false, true, true))
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
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, null, true,false,false, true, true))
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
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true, false,false,false, true))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void addCertificateTestWithRootOrgIdInUser() throws Exception{
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ADD_CERTIFICATE_URL)
                    .bodyJson(getAddCertificateRequest(
                            COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true,true,true, false, true))
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

    @Test
    public void addCertificateTestWithoutRootOrgIdInUser() throws Exception{
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADD_CERTIFICATE_URL)
                        .bodyJson(getAddCertificateRequest(
                                COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true,true,false, false, true))
                        .method("PATCH");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

  @Test
  public void addCertificateTestWithoutIssuer() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ADD_CERTIFICATE_URL)
            .bodyJson(
                getAddCertificateRequest(
                    COURSE_ID, BATCH_ID, true, CERTIFICATE_NAME, TEMPLATE_ID, true,false,false, true, false))
            .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void deleteCertificateTest() {
    setup(ACTOR_NAMES.COURSEBATCH_CERTIFICATE_ACTOR,DummyActor.class);
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
            .bodyJson(getCertificateRequest(COURSE_ID, BATCH_ID, false, null))
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
      boolean user,
      boolean rootOrgId,
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
        if(user){
            Map<String, Object> userMap = new HashMap<>();
            if(rootOrgId) {
                userMap.put(JsonKey.ROOT_ORG_ID, "rootOrgId");
            }
          criteriaMap.put((JsonKey.USER),userMap);
        }
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
