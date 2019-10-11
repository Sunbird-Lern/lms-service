package controllers.dashboard;


import controllers.BaseApplicationTest;
import actors.DummyActor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.responsecode.ResponseCode;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class CourseMetricsControllerTest extends BaseApplicationTest {

    public static String COURSE_ID = "courseId";
    public static String BATCH_ID = "batchId";

    @Before
    public void before() {
        setup(DummyActor.class);
    }

    @Test
    public void testGetCourseProgress() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/dashboard/progress/course/"+BATCH_ID)
                        .method("GET");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }


    @Test
    public void testGetCourseProgressV2() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v2/dashboard/progress/course/"+BATCH_ID)
                        .method("GET");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testGetCourseProgressV2failureForNonNumericLimit() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v2/dashboard/progress/course/"+BATCH_ID+"?limit=abc")
                        .method("GET");
        try {
            Helpers.route(application, req);
        }
        catch (ProjectCommonException ex) {
            Assert.assertEquals(ResponseCode.dataTypeError.getErrorCode(),ex.getCode());
        }
    }

    @Test
    public void testGetCourseProgressV2failureForNonNumericOffset() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v2/dashboard/progress/course/"+BATCH_ID+"?offset=abc")
                        .method("GET");
        try {
            Helpers.route(application, req);
        }
        catch(ProjectCommonException ex) {
            Assert.assertEquals(ResponseCode.dataTypeError.getErrorCode(),ex.getCode());
        }
    }

    @Test
    public void testGetCourseProgressV2failureForIncorrectSortOrder() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v2/dashboard/progress/course/"+BATCH_ID+"?sortOrder=randomOrder")
                        .method("GET");
        try {
            Helpers.route(application, req);
        }
        catch(ProjectCommonException ex) {
            Assert.assertEquals(ResponseCode.invalidParameterValue.getErrorCode(),ex.getCode());
        }
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

    @Test
    public void testGetCourseProgressReport() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri("/v1/dashboard/progress/course/"+COURSE_ID+"/export")
                        .method("GET");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }
}

