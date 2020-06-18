package controllers.dashboard;


import controllers.BaseApplicationTest;
import actors.DummyActor;
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

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class CourseMetricsControllerTest extends BaseApplicationTest {

    public static String COURSE_ID = "courseId";

    @Before
    public void before() {
        setup(ACTOR_NAMES.COURSE_METRICS_ACTOR,DummyActor.class);
    }

    @Test
    public void testGetCourseCreation() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/dashboard/consumption/course/"+COURSE_ID)
                        .method("GET");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

}

