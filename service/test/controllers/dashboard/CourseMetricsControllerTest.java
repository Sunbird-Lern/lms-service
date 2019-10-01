package controllers.dashboard;


import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
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
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.responsecode.ResponseCode;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class CourseMetricsControllerTest {

    public static String COURSE_ID = "courseId";
    public static String BATCH_ID = "batchId";
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

