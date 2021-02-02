package controllers.bulkapimanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import actors.DummyActor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/** Created by arvind on 4/12/17. */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})

public class BulkUploadControllerTest extends BaseApplicationTest {

  @Before
  public void before() {
    setup(ACTOR_NAMES.BULK_UPLOAD_MANAMGEMENT_ACTOR, DummyActor.class);
  }

  @Test
  public void testGetUploadStatus() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/upload/status/pid")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testGetStatusDownLoadLink() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/upload/statusDownloadLink/pid")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testBulkBatchEnrollment() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.DATA, "sampleStream".getBytes(Charset.defaultCharset()));
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/batch/bulk/enrollment")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }


  @Test
  public void testBulkBatchUnEnrollment() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.DATA, "sampleStream".getBytes(Charset.defaultCharset()));
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/batch/bulk/unenrollment")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  private String mapToJson(Map map) {
    ObjectMapper mapperObj = new ObjectMapper();
    String jsonResp = "";
    try {
      jsonResp = mapperObj.writeValueAsString(map);
    } catch (IOException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
    return jsonResp;
  }


}
