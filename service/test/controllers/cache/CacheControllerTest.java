package controllers.cache;

import actors.DummyActor;
import controllers.BaseApplicationTest;
import modules.OnRequestHandler;
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
import org.sunbird.common.request.HeaderParam;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;
import util.RequestInterceptor;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper"})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class CacheControllerTest extends BaseApplicationTest {

  public static String MAP_NAME = "mapName";

  @Before
  public void before() {
    setup(ACTOR_NAMES.CACHE_MANAGEMENT_ACTOR, DummyActor.class);
  }

  @Test
  public void testClearCache() {
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri("/v1/cache/clear/" + MAP_NAME).method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void testClearCacheWithHeaders() {
    Http.RequestBuilder req =
        new Http.RequestBuilder()
            .uri("/v1/cache/clear/" + MAP_NAME)
            .header(JsonKey.MESSAGE_ID, "messageId")
            .header(HeaderParam.CHANNEL_ID.getName(), "channelId")
            .header(HeaderParam.X_APP_ID.getName(), "appId")
            .method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(200, result.status());
  }

  @Test
  public void testClearCacheUnauth() {
    PowerMockito.mockStatic(RequestInterceptor.class);
    PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any()))
        .thenReturn(JsonKey.UNAUTHORIZED);
    Http.RequestBuilder req =
        new Http.RequestBuilder().uri("/v1/cache/clear/" + MAP_NAME).method("DELETE");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(401, result.status());
  }
}
