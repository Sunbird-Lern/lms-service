package controllers.group;

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
import org.sunbird.learner.constants.CourseJsonKey;
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
public class GroupAggControllerTest extends BaseApplicationTest {

    String USER_ID = "userId";
    String GROUPID = "groupId";
    String ACTIVITYID = "activityId";
    String ACTIVITYTYPE = "activityType";
    String GROUP_ACTIVITY_AGGREGATE_URL = "/v1/group/activity/agg";

    @Before
    public void before() {
        setup(Arrays.asList(ACTOR_NAMES.GROUP_AGGREGATES_ACTORS), DummyActor.class);
    }

    @Test
    public void testGetGroupActivityAggregatesSuccess() {
        setup(ACTOR_NAMES.GROUP_AGGREGATES_ACTORS, DummyActor.class);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(GROUP_ACTIVITY_AGGREGATE_URL)
                        .bodyJson(
                                getGroupActivityAggregatesRequest(
                                        GROUPID, ACTIVITYID, ACTIVITYTYPE))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    private JsonNode getGroupActivityAggregatesRequest(
            String groupId, String activityId, String activityType) {
        Map<String, Object> innerMap = new HashMap<>();
        Map<String, Object> activity = new HashMap<>();
        if (groupId != null) activity.put(JsonKey.GROUPID, groupId);
        if (activityId != null) activity.put(JsonKey.ACTIVITYID, activityId);
        if (activityType != null) activity.put(JsonKey.ACTIVITYTYPE, activityType);
        Map<String, Object> requestMap = new HashMap<>();
        innerMap.put(JsonKey.GROUP_ACTIVITY_DB, activity);
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