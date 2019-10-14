package controllers.cache;


import controllers.BaseApplicationTest;
import actors.DummyActor;
import modules.OnRequestHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper"})
@PowerMockIgnore("javax.management.*")
public class CacheControllerTest extends BaseApplicationTest {

    public static String MAP_NAME="mapName";
    @Before
    public void before() {
        setup(DummyActor.class);
    }

    @Test
    public void testClearCache() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/cache/clear/"+MAP_NAME)
                        .method("DELETE");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }
}
