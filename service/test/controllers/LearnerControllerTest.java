package controllers;

import static controllers.TestUtil.mapToJson;
import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;
import static play.test.Helpers.route;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import modules.OnRequestHandler;
import modules.StartModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.responsecode.ResponseCode;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

/** @author arvind */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ RequestInterceptor.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class LearnerControllerTest  {

  private static final String COURSE_ID = "course-123";
  private static final String USER_ID = "user-123";
  private static final String CONTENT_ID = "content-123";
  private static final String BATCH_ID = "batch-123";
  public static Application application;
  public static ActorSystem system;
  public static final Props props = Props.create(DummyActor.class);
  private static final String CONTENT_STATE_UPDATE_URL = "/v1/content/state/update";
  private static final String CONTENT_STATE_READ_URL = "/v1/content/state/read";

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
    PowerMockito.mockStatic(RequestInterceptor.class);
    Http.Request request = PowerMockito.mock(Http.Request.class);
    when(RequestInterceptor.verifyRequestData(request)).thenReturn("AUTHORIZED");
  }
  @Test
  public void testUpdateContentStateSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, CONTENT_ID);
    map.put(JsonKey.STATUS, "Active");
    map.put(JsonKey.LAST_UPDATED_TIME, "2019-08-18 10:47:30:707+0530");
    list.add(map);
    innerMap.put("contents", list);
    innerMap.put("courseId", COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_UPDATE_URL)
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());

  }

  @Test
  public void testGetContentStateSuccess() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, BATCH_ID);
    innerMap.put(JsonKey.USER_ID, USER_ID);
    innerMap.put(JsonKey.COURSE_ID,COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_READ_URL)
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testGetContentStateFailureWithInvalidFieldType() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("courseId", COURSE_ID);
    innerMap.put(JsonKey.COURSE_IDS, new HashMap<>());
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);

    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_READ_URL)
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testGetContentStateFailureWithoutUserId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("courseId", COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_READ_URL)
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }


  @Test
  public void testGetContentStateFailureWithoutCourseId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("userId", USER_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_READ_URL)
                    .bodyJson(json)
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }
  @Test
  public void testUpdateContentStateFailureForMissingCourseId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, CONTENT_ID);
    map.put(JsonKey.STATUS, "Active");
    map.put(JsonKey.LAST_UPDATED_TIME, "2017-12-18 10:47:30:707+0530");
    list.add(map);

    List<Map<String, Object>> assData = new ArrayList<Map<String, Object>>();
    Map<String, Object> assData1 = new HashMap<String, Object>();
    assData1.put("batchId", BATCH_ID);
    assData1.put("contentId", CONTENT_ID);
    Map<String, Object> assEvents1 = new HashMap<String, Object>();
    List<Map<String, Object>> eventsArray = new ArrayList();
    eventsArray.add(assEvents1);
    assData1.put("events", eventsArray);
    assData.add(assData1);

    innerMap.put("contents", list);
    innerMap.put("courseId", COURSE_ID);
    innerMap.put(JsonKey.ASSESSMENT_EVENTS, assData);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_UPDATE_URL)
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());

  }

  @Test
  public void testUpdateContentStateFailureWithoutContentId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, null);
    map.put(JsonKey.STATUS, "Active");
    map.put(JsonKey.LAST_UPDATED_TIME, "2017-12-18 10:47:30:707+0530");
    list.add(map);
    innerMap.put("contents", list);
    innerMap.put("courseId", COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_UPDATE_URL)
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());

  }

  @Test
  public void testUpdateContentStateFailureWithoutStatus() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, CONTENT_ID);
    map.put(JsonKey.STATUS, null);
    map.put(JsonKey.LAST_UPDATED_TIME, "2017-12-18 10:47:30:707+0530");
    list.add(map);
    innerMap.put("contents", list);
    innerMap.put("courseId", COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_UPDATE_URL)
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());

  }

  @Test
  public void testUpdateContentStateFailureWithIncorextDateFormat() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, CONTENT_ID);
    map.put(JsonKey.STATUS, "Active");
    map.put(JsonKey.LAST_UPDATED_TIME, "08-08-2019 10:47:30:707+0530");
    list.add(map);
    innerMap.put("contents", list);
    innerMap.put("courseId", COURSE_ID);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CONTENT_STATE_UPDATE_URL)
                    .bodyJson(json)
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());

  }
}
