package controllers.courseenrollment;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.ConfigFactory;

import controllers.BaseApplicationTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;
import util.RequestInterceptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static util.TestUtil.mapToJson;

@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
public class CourseEnrollmentControllerTest2 extends BaseApplicationTest {

    String ADMIN_ENROLL_BATCH_URL = "/v1/course/admin/enroll";
    String ADMIN_UENROLL_BATCH_URL = "/v1/course/admin/unenroll";
    String ADMIN_GET_ENROLLED_COURSE_URL_V2 = "/v2/user/courses/admin/list?fields=contentType,name,channel,mimeType";
    String COURSE_ID = "courseId";
    String BATCH_ID = "batchId";
    String USER_ID = "userId";

    @Before
    public void before() throws Exception {
        setup(Arrays.asList(ACTOR_NAMES.COURSE_ENROLMENT_ACTOR,ACTOR_NAMES.CONTENT_CONSUMPTION_ACTOR), DummyActor.class);
    }

    @Test
    public void testAdminEnrollCourseSuccess(){
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_ENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testAdminEnrollCourseFailureWithoutCourseId() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_ENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(null, BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testAdminEnrollCourseFailureWithoutBatchId() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_ENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(COURSE_ID, null, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testAdminEnrollCourseFailureWithoutUserId() {
        if (ConfigFactory.load().getBoolean(JsonKey.AUTH_ENABLED)) {
            PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any())).thenReturn(JsonKey.ANONYMOUS);
            Http.RequestBuilder req =
                    new Http.RequestBuilder()
                            .uri(ADMIN_ENROLL_BATCH_URL)
                            .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, null))
                            .method("POST");
            Result result = Helpers.route(application, req);
            Assert.assertEquals(400, result.status());
        }
    }

    @Test
    public void testAdminUnenrollCourseSuccess(){
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_UENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testAdminUnenrollCourseBatchFailureWithoutCourseId() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_UENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(null, BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testAdminUnenrollCourseBatchFailureWithoutBatchId() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_UENROLL_BATCH_URL)
                        .bodyJson(createCourseEnrollmentRequest(COURSE_ID, null, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 400, result.status());
    }

    @Test
    public void testAdminUnenrollCourseBatchFailureWithoutUserId() {
        if (ConfigFactory.load().getBoolean(JsonKey.AUTH_ENABLED)) {
            PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any())).thenReturn(JsonKey.ANONYMOUS);
            Http.RequestBuilder req =
                    new Http.RequestBuilder()
                            .uri(ADMIN_UENROLL_BATCH_URL)
                            .bodyJson(createCourseEnrollmentRequest(COURSE_ID, BATCH_ID, null))
                            .method("POST");
            Result result = Helpers.route(application, req);
            Assert.assertEquals(400, result.status());
        }
    }

    @Test
    public void testAdminGetUserEnrolledCourses(){
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ADMIN_GET_ENROLLED_COURSE_URL_V2)
                        .bodyJson(createCourseEnrollmentRequest(null,null, USER_ID))
                        .method("POST");
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
