package controllers.QRcodedownload;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
import actors.DummyActor;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static util.TestUtil.mapToJson;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class QRCodeDownloadControllerTest extends BaseApplicationTest {

    @Before
    public void before() {
        setup();
    }

    @Test
    public void testEnrollCourseBatchSuccess() {
        Map<String, Object> requestMap = new HashMap<>();
        List<String> userIds= new ArrayList<>();
        userIds.add("user1");
        userIds.add("user2");
        Map<String,Object> userMap=new HashMap<>();
        userMap.put(JsonKey.USER_IDs,userIds);
        Map<String,Object> filterMap=new HashMap<>();
        filterMap.put(JsonKey.FILTER,userMap);
        requestMap.put(JsonKey.REQUEST,filterMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/course/qrcode/download")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testEnrollCourseBatchFailureWithoutUserIds() {
        Map<String, Object> requestMap = new HashMap<>();
        Map<String,Object> filterMap=new HashMap<>();
        Map<String,Object> userMap=new HashMap<>();
        userMap.put(JsonKey.USER_IDs,null);
        filterMap.put(JsonKey.FILTER,userMap);
        requestMap.put(JsonKey.REQUEST,filterMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/course/qrcode/download")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testEnrollCourseBatchFailureWithoutFilter(){
        Map<String, Object> requestMap = new HashMap<>();
        Map<String,Object> filterMap=new HashMap<>();
        filterMap.put(JsonKey.FILTER,null);
        requestMap.put(JsonKey.REQUEST,filterMap);
        String data = mapToJson(requestMap);
        JsonNode json = Json.parse(data);
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/course/qrcode/download")
                        .bodyJson(json)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }
}
