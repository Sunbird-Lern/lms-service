package controllers.healthmanager;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;
import static play.test.Helpers.route;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import controllers.BaseController;
import controllers.DummyActor;

import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.*;

import controllers.DummyHealthActor;
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
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.HeaderParam;
import play.Application;
import play.Mode;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;
import util.RequestInterceptor;

/** Created by arvind on 5/12/17. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class,HttpUtil.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")

public class HealthControllerTest {

  public static Application application;
  public static ActorSystem system;
  public static final Props props = Props.create(DummyHealthActor.class);

  @Before
  public void startApp() {
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
  public void testgetHealth() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri("/health")
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testgetEkstepServiceHealth() throws IOException {
    PowerMockito.mockStatic(HttpUtil.class);
    String response ="OK";
    when(HttpUtil.sendPostRequest(Mockito.anyString(),Mockito.anyString(),Mockito.anyMap())).thenReturn(response);
    RequestBuilder req = new RequestBuilder().uri("/ekstep/health").method("GET");
    Result result = route(application, req);
    assertEquals(200, result.status());
  }

  @Test
  public void testgetLearnerServiceHealth() throws IOException {
    RequestBuilder req = new RequestBuilder().uri("/learner/health").method("GET");
    Result result = route(application, req);
    assertEquals(200, result.status());
  }

}
