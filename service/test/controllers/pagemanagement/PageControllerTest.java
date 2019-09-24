package controllers.pagemanagement;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;
import static play.test.Helpers.route;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseController;
import controllers.DummyActor;

import java.io.File;
import java.io.IOException;
import java.util.*;

import modules.OnRequestHandler;
import modules.StartModule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.HeaderParam;
import play.Application;
import play.Mode;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;

/** Created by arvind on 4/12/17. */
@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class PageControllerTest {

  public static Application application;
  public static ActorSystem system;
  public static final Props props = Props.create(DummyActor.class);
  public static String PAGE_ID="pageID";
  public static String SECTION_ID="sectionId";
  @Before
  public void before() {
    application =
            new GuiceApplicationBuilder()
                    .in(new File("path/to/app"))
                    .in(Mode.TEST)
                    .disable(StartModule.class)
                    .build();

    Helpers.start(application);
    system = ActorSystem.create("system");
    ActorRef subject = system.actorOf(props);
    BaseController.setActorRef(subject);
    PowerMockito.mockStatic(OnRequestHandler.class);
    Map<String, Object> inner = new HashMap<>();
    Map<String, Object> aditionalInfo = new HashMap<String, Object>();
    aditionalInfo.put(JsonKey.START_TIME, System.currentTimeMillis());
    inner.put(JsonKey.ADDITIONAL_INFO, aditionalInfo);
    Map outer = PowerMockito.mock(HashMap.class);
    OnRequestHandler.requestInfo = outer;
    PowerMockito.when(OnRequestHandler.requestInfo.get(Mockito.anyString())).thenReturn(inner);
  }

  @Test
  public void testcreatePageSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/create")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }


  @Test
  public void testcreatePageFailureWithoutName() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", null);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/create")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }


  @Test
  public void testupdatePageSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put(JsonKey.ID, "pageId");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testupdatePageFailureWithoutName() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", null);
    innerMap.put(JsonKey.ID, "pageId");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testupdatePageFailureWithoutId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put(JsonKey.ID, null);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }
  @Test
  public void testgetPageSetting() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/read/"+PAGE_ID)
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testgetPageSettings() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/all/settings")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testgetPageDataSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put(JsonKey.SOURCE, "web");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/assemble")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testgetPageDataFailureWithInvalidSource() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put(JsonKey.SOURCE, "INVALID");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/assemble")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testgetPageDataFailureWithoutName() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", null);
    innerMap.put(JsonKey.SOURCE, "web");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/assemble")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testcreatePageSectionSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put("sectionDataType", "section01");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/create")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testcreatePageSectionFailureWithoutName() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", null);
    innerMap.put("sectionDataType", "section01");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/create")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testcreatePageSectionFailureWithoutSectionType() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put("sectionDataType", null);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/create")
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testupdatePageSectionSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put("sectionDataType", "section01");
    innerMap.put(JsonKey.ID, "001");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testupdatePageSectionFailureWithoutSectionType() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put("sectionDataType", null);
    innerMap.put(JsonKey.ID, "001");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testupdatePageSectionFailureWithoutName() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", null);
    innerMap.put("sectionDataType", "section01");
    innerMap.put(JsonKey.ID, "001");
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testupdatePageSectionFailureWithoutId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("name", "page1");
    innerMap.put("sectionDataType", "section01");
    innerMap.put(JsonKey.ID, null);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/update")
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }


  @Test
  public void testgetSection() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/read/"+SECTION_ID)
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testgetSections() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/v1/page/section/list")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }


  private static String mapToJson(Map map) {
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
