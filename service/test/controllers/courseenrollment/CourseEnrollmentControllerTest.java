package controllers.courseenrollment;

import static controllers.TestUtil.mapToJson;
import static org.junit.Assert.assertEquals;
import static play.test.Helpers.route;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import controllers.BaseControllerTest;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import controllers.DummyActor;
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
import org.sunbird.common.models.util.JsonKey;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OnRequestHandler.class})
@SuppressStaticInitializationFor({"util.AuthenticationHelper", "util.Global"})
@PowerMockIgnore("javax.management.*")
public class CourseEnrollmentControllerTest {

  public static String COURSE_ID = "courseId";
  public static String BATCH_ID = "batchId";
  public static String USER_ID = "userId";
  private static final String ENROLL_BATCH_URL = "/v1/course/enroll";
  private static final String UENROLL_BATCH_URL = "/v1/course/unenroll";
  private static final String GET_ENROLLED_COURSES_URL = "/v1/user/courses/list/"+USER_ID;
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
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testUnenrollCourseBatchSuccess() {

    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testEnrollCourseBatchFailureWithoutCourseId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(null, BATCH_ID, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testEnrollCourseBatchFailureWithoutBatchId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, null, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testEnrollCourseBatchFailureWithoutUserId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, null))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testUnenrollCourseBatchFailureWithoutCourseId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(null, BATCH_ID, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testUnenrollCourseBatchFailureWithoutBatchId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, null, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testUnenrollCourseBatchFailureWithoutUserId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UENROLL_BATCH_URL)
                    .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, null))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testGetEnrolledCoursesSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(GET_ENROLLED_COURSES_URL)
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  private JsonNode createCourseEnrollmentRequest(
      String courseId, String batchId, String userId) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) innerMap.put(JsonKey.BATCH_ID, batchId);
    if (userId != null) innerMap.put(JsonKey.USER_ID, userId);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }
}
