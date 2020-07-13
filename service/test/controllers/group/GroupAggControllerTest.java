package controllers.group;

import actors.DummyActor;
import controllers.BaseApplicationTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

import java.util.Arrays;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class GroupAggControllerTest extends BaseApplicationTest {

    public static String USER_ID = "userId";
    private static final String GROUP_ACTIVITY_AGGREGATE_URL = "/v1/group/activity/agg";

    @Before
    public void before() {
        setup(Arrays.asList(ACTOR_NAMES.GROUP_AGGREGATES_ACTORS), DummyActor.class);
    }

    @Test
    public void testGetGroupActivityAggregatesSuccess() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(GROUP_ACTIVITY_AGGREGATE_URL)
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }
}
