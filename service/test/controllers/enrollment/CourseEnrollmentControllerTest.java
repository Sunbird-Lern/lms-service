package controllers.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
import actors.DummyActor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static util.TestUtil.mapToJson;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class CourseEnrollmentControllerTest extends BaseApplicationTest {

  public static String COURSE_ID = "courseId";
  public static String BATCH_ID = "batchId";
  public static String USER_ID = "userId";
  private static final String ENROLL_BATCH_URL = "/v1/course/enroll";
  private static final String UENROLL_BATCH_URL = "/v1/course/unenroll";
  private static final String GET_ENROLLED_COURSES_URL = "/v1/user/courses/list/"+USER_ID;
  private static final String GET_ENROLLED_COURSE_URL_V2 = "/v2/user/courses/list";
  private static final String GET_ENROLLED_COURSE_URL_CACHE = "/v1/user/courses/list/" + USER_ID + "?cache=false";

  @Before
  public void before() {
    setup(Arrays.asList(ACTOR_NAMES.COURSE_ENROLMENT_ACTOR,ACTOR_NAMES.CONTENT_CONSUMPTION_ACTOR), DummyActor.class);
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

  @Test
  public void testGetUserEnrolledCourses() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(GET_ENROLLED_COURSE_URL_V2)
                    .bodyJson(createCourseEnrollmentRequest(null,null, USER_ID))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testGetUserEnrolledCoursesWithCache() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(GET_ENROLLED_COURSE_URL_CACHE)
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
