package controllers.textbook;

import static org.junit.Assert.assertEquals;
import static play.test.Helpers.route;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseController;
import controllers.BaseControllerTest;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import controllers.DummyActor;
import modules.OnRequestHandler;
import modules.StartModule;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
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
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import play.Application;
import play.Mode;
import play.api.http.MediaType;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class TextbookControllerTest {

  private static ObjectMapper mapper = new ObjectMapper();
  private static String TEXTBOOK_ID = "textbookId";
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
  public void testUploadTocWithUrl() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri(
                "/v1/textbook/toc/upload/do_1126526588628582401237?fileUrl=https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/toc/do_112648449830322176179/download.csv")
            .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testGetTocUrl() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(
                            "/v1/textbook/toc/download/"+TEXTBOOK_ID)
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }
}
