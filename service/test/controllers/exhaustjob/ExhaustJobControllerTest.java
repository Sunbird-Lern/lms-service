package controllers.exhaustjob;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class ExhaustJobControllerTest extends BaseApplicationTest {

  @Before
  public void before() {
    setup(Arrays.asList(ACTOR_NAMES.EXHAUST_JOB_ACTOR), DummyActor.class);
  }

  @Test
  public void testJobSubmitRequestSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/jobrequest/submit")
                    .bodyJson(createJobSubmitRequest())
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testListJobRequestSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/jobrequest/list/do_2137002173427875841205_01370023185341644822")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }
  private JsonNode createJobSubmitRequest() {
    Map<String, Object> req = new HashMap<>();
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.TAG, "do_2137002173427875841205_01370023185341644822");
    requestMap.put(JsonKey.REQUESTED_BY, "fca2925f-1eee-4654-9177-fece3fd6afc9");
    requestMap.put(JsonKey.DATASET, "progress-exhaust");
    requestMap.put(JsonKey.CONTENT_TYPE, "Course");
    requestMap.put(JsonKey.OUTPUT_FORMAT, "csv");
    requestMap.put(JsonKey.DATASETCONFIG, new HashMap<>().put(JsonKey.BATCH_ID,"01370023185341644822"));
    req.put(JsonKey.REQUEST,requestMap);
    String data = mapToJson(req);
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
