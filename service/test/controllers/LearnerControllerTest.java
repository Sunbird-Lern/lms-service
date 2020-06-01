package controllers;

import static util.TestUtil.mapToJson;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

/** @author arvind */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class LearnerControllerTest extends BaseApplicationTest {

  private static final String COURSE_ID = "course-123";
  private static final String USER_ID = "user-123";
  private static final String CONTENT_ID = "content-123";
  private static final String BATCH_ID = "batch-123";
  private static final String CONTENT_STATE_UPDATE_URL = "/v1/content/state/update";
  private static final String CONTENT_STATE_READ_URL = "/v1/content/state/read";

  @BeforeClass
  public void before() {
    setup();
  }

  @Test
  public void testUpdateContentStateSuccess() {
    JsonNode json =
        createUpdateContentStateRequest(CONTENT_ID, "Active", generateDatePattern(), COURSE_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void testGetContentStateSuccess() {
    JsonNode json = createGetContentStateRequest(USER_ID, COURSE_ID, BATCH_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_READ_URL).bodyJson(json).method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
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
        new Http.RequestBuilder().uri(CONTENT_STATE_READ_URL).bodyJson(json).method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testGetContentStateFailureWithoutUserId() {
    JsonNode json = createGetContentStateRequest(null, COURSE_ID, BATCH_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_READ_URL).bodyJson(json).method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testGetContentStateFailureWithoutCourseId() {
    JsonNode json = createGetContentStateRequest(USER_ID, null, BATCH_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_READ_URL).bodyJson(json).method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testUpdateContentStateFailureForMissingCourseId() {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, CONTENT_ID);
    map.put(JsonKey.STATUS, "Active");
    map.put(JsonKey.LAST_UPDATED_TIME, generateDatePattern());
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
        new Http.RequestBuilder().uri(CONTENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testUpdateContentStateFailureWithoutContentId() {
    JsonNode json =
        createUpdateContentStateRequest(null, "Active", generateDatePattern(), COURSE_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testUpdateContentStateFailureWithoutStatus() {
    JsonNode json =
        createUpdateContentStateRequest(CONTENT_ID, null, generateDatePattern(), COURSE_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testUpdateContentStateFailureWithIncorrectDateFormat() {
    JsonNode json =
        createUpdateContentStateRequest(
            CONTENT_ID, "Active", "18-12-2017 10:47:30:707+0530", COURSE_ID);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri(CONTENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testPreflightAll() {
    Http.RequestBuilder req = new Http.RequestBuilder().uri("/abcall").method("OPTIONS");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  private JsonNode createUpdateContentStateRequest(
      String contentId, String status, String lastUpdatedTime, String courseId) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    List<Object> list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTENT_ID, contentId);
    map.put(JsonKey.COURSE_ID, courseId);
    map.put(JsonKey.STATUS, status);
    map.put(JsonKey.LAST_UPDATED_TIME, lastUpdatedTime);
    list.add(map);
    innerMap.put("contents", list);
    innerMap.put("userId", USER_ID);
    innerMap.put("courseId", courseId);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    return json;
  }

  private JsonNode createGetContentStateRequest(String userId, String courseId, String batchId) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.USER_ID, userId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    JsonNode json = Json.parse(data);
    return json;
  }

  private String generateDatePattern() {
    Date date = Calendar.getInstance().getTime();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ");
    String strDate = dateFormat.format(date);
    return strDate;
  }
}
