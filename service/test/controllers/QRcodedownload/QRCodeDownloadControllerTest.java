package controllers.QRcodedownload;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import controllers.DummyActor;
import modules.OnRequestHandler;
import modules.StartModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static controllers.TestUtil.mapToJson;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class QRCodeDownloadControllerTest {
    public static Application application;
    public static ActorSystem system;
    public static final Props props = Props.create(DummyActor.class);

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
