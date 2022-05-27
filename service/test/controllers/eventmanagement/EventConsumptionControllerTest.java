package controllers.eventmanagement;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
import org.junit.Assert;
import org.junit.Before;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static util.TestUtil.mapToJson;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class EventConsumptionControllerTest extends BaseApplicationTest {

    String COURSE_ID = "course-123";
    String CONTENT_ID = "content-123";
    String USER_ID = "user-123";
    String FIXED_BATCH_ID = "event_batch_id";
    String BATCH_ID = "batch-123";
    String EVENT_STATE_UPDATE_URL = "/v1/user/event/state/update";
    String EVENT_STATE_READ_URL = "/v1/user/event/state/read";

    @Before
    public void before() {
        setup(Arrays.asList(ACTOR_NAMES.EVENT_CONSUMPTION_ACTOR, ACTOR_NAMES.EVENT_SET_ENROLMENT_ACTOR), DummyActor.class);
    }

    @Test
    public void testReadContentStateSuccess() {
        JsonNode json = createReadEventStateRequest(USER_ID, COURSE_ID, BATCH_ID);
        Http.RequestBuilder req =
                new Http.RequestBuilder().uri(EVENT_STATE_READ_URL).bodyJson(json).method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(200, result.status());
    }

    @Test
    //Request missing BATCH_ID
    public void testReadContentStateFailed1() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.USER_ID, USER_ID);
        innerMap.put(JsonKey.COURSE_ID, COURSE_ID);
        requestMap.put(JsonKey.REQUEST, innerMap);
        JsonNode json = Json.parse(mapToJson(requestMap));
        Http.RequestBuilder req =
                new Http.RequestBuilder().uri(EVENT_STATE_READ_URL).bodyJson(json).method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    @Test
    //Request missing CourseId
    public void testReadContentStateFailed2() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.USER_ID, USER_ID);
        innerMap.put(JsonKey.FIXED_BATCH_ID, FIXED_BATCH_ID);
        requestMap.put(JsonKey.REQUEST, innerMap);
        JsonNode json = Json.parse(mapToJson(requestMap));
        Http.RequestBuilder req =
                new Http.RequestBuilder().uri(EVENT_STATE_READ_URL).bodyJson(json).method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    @Test
    //Request missing UserID
    public void testReadContentStateFailed3() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.COURSE_ID, COURSE_ID);
        innerMap.put(JsonKey.FIXED_BATCH_ID, FIXED_BATCH_ID);
        requestMap.put(JsonKey.REQUEST, innerMap);
        JsonNode json = Json.parse(mapToJson(requestMap));
        Http.RequestBuilder req =
                new Http.RequestBuilder().uri(EVENT_STATE_READ_URL).bodyJson(json).method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    @Test
    public void testUpdateContentStateSuccess() {
        JsonNode json =
                createUpdateEventSetStateRequest(CONTENT_ID, "Active", generateDatePattern(), COURSE_ID);
        Http.RequestBuilder req =
                new Http.RequestBuilder().uri(EVENT_STATE_UPDATE_URL).bodyJson(json).method("PATCH");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(200, result.status());
    }

    private JsonNode createReadEventStateRequest(String userId, String courseId, String batchId) {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.USER_ID, userId);
        innerMap.put(JsonKey.COURSE_ID, courseId);
        innerMap.put(JsonKey.FIXED_BATCH_ID, FIXED_BATCH_ID);
        requestMap.put(JsonKey.REQUEST, innerMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        return json;
    }

    private JsonNode createUpdateEventSetStateRequest(
            String contentId, String status, String lastUpdatedTime, String courseId) {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.COURSE_ID, courseId);
        innerMap.put(JsonKey.STATUS, status);
        innerMap.put(JsonKey.USER_ID, USER_ID);
        innerMap.put(JsonKey.FIXED_BATCH_ID, FIXED_BATCH_ID);
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
