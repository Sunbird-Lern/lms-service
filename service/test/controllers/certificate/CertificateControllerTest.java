package controllers.certificate;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseController;
import controllers.DummyActor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import util.Global;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Global.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class CertificateControllerTest {
  private static final String COURSE_ID = "courseId";
  private static final String BATCH_ID = "batchId";
  private static final String CERTIFICATE = "certificate";
  private static final String ISSUE_CERTIFICATE_URL = "/v1/course/batch/cert/issue";

  public static FakeApplication app;
  public static Map<String, String[]> headerMap;
  public static ActorSystem system;
  public static final Props props = Props.create(DummyActor.class);

  @BeforeClass
  public static void startApp() {
    app = Helpers.fakeApplication(Helpers.fakeGlobal());
    Helpers.start(app);
    system = ActorSystem.create("system");
    ActorRef subject = system.actorOf(props);
    BaseController.setActorRef(subject);
  }

  @Before
  public void before() {
    PowerMockito.mockStatic(Global.class);
    Map<String, Object> inner = new HashMap<>();
    Map<String, Object> aditionalInfo = new HashMap<String, Object>();
    aditionalInfo.put(JsonKey.START_TIME, System.currentTimeMillis());
    inner.put(JsonKey.ADDITIONAL_INFO, aditionalInfo);
    Map outer = PowerMockito.mock(HashMap.class);
    Global.requestInfo = outer;
    PowerMockito.when(Global.requestInfo.get(Mockito.anyString())).thenReturn(inner);
  }

  @Test
  public void issueCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(app, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void reIssueCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL + "?reIssue=true")
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(app, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void issueCertificateWithoutCourseTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(null, BATCH_ID, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(app, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void issueCertificateWithoutBatchTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, null, CERTIFICATE))
            .method("POST");
    Result result = Helpers.route(app, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void issueCertificateWithoutCertificateTest() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(ISSUE_CERTIFICATE_URL)
            .bodyJson(getIssueCertificateRequest(COURSE_ID, BATCH_ID, null))
            .method("POST");
    Result result = Helpers.route(app, req);
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
