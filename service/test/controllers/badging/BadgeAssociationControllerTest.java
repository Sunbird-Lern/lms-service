package controllers.badging;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
import actors.DummyActor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.BadgingJsonKey;
import org.sunbird.common.models.util.JsonKey;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static util.TestUtil.mapToJson;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class BadgeAssociationControllerTest extends BaseApplicationTest {
    private static final String CONTENT_ID = "content-123";
    @Before
    public void before() {
        setup();
    }

    @Test
    public void testCreateAssociationSuccess() {
        List<String> badgeIds = new ArrayList<>();
        badgeIds.add("badgeId");
        JsonNode json= createBadgeRequest(CONTENT_ID,badgeIds);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/link")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testCreateAssociationFailureWithoutContentId() {
        List<String> badgeIds = new ArrayList<>();
        badgeIds.add("badgeId");
        JsonNode json= createBadgeRequest(null,badgeIds);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/link")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testCreateAssociationFailureWithoutBageIds() {
        JsonNode json= createBadgeRequest(CONTENT_ID,null);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/link")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testRemoveAssociationSuccess() {
        List<String> badgeIds = new ArrayList<>();
        badgeIds.add("badgeId");
        JsonNode json= createBadgeRequest(CONTENT_ID,badgeIds);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/unlink")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testSearchAssociationSuccess() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        Map<String, Object> filterMap = new HashMap<>();
        List<String> badgeIds = new ArrayList<>();
        badgeIds.add("badgeId");
        innerMap.put(JsonKey.FILTERS, filterMap);
        requestMap.put(JsonKey.REQUEST, innerMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/link/search")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testSearchAssociationFailureWithoutBody() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/content/link/search")
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    private JsonNode createBadgeRequest(String contentId, List<String> badgeIds){
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
  ;
        innerMap.put(JsonKey.CONTENT_ID, contentId);
        innerMap.put(BadgingJsonKey.BADGE_IDs, badgeIds);
        requestMap.put(JsonKey.REQUEST, innerMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        return json;

    }
}
