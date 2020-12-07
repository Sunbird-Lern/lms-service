package controllers.coursemanagement;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;
import util.TestUtil;

import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class CourseControllerTest extends BaseApplicationTest {

    private static final String COURSE_CREATE_URL = "/v1/course/create";

    @Before
    public void before() {
        setup(ACTOR_NAMES.COURSE_MANAGEMENT_ACTOR, DummyActor.class);
    }

    @Test
    public void testCourseCreateSuccess() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(COURSE_CREATE_URL)
                        .bodyJson(createCourseRequest("application/vnd.ekstep.content-collection", "Course"))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(200, result.status());
    }

    @Test
    public void testCourseCreateWithInvalidRequest() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(COURSE_CREATE_URL)
                        .bodyJson(createCourseInvalidRequest())
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    @Test
    public void testCourseCreateWithInvalidContentType() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(COURSE_CREATE_URL)
                        .bodyJson(createCourseRequest("application/vnd.ekstep.content-collection", "InvalidContent"))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    @Test
    public void testCourseCreateWithInvalidMimeType() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(COURSE_CREATE_URL)
                        .bodyJson(createCourseRequest("application/InvalidCollection", "Course"))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals(400, result.status());
    }

    private JsonNode createCourseRequest(String mimeType, String contentType) {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put("name", "Test_CurriculumCourse With 3 Units");
        courseMap.put("description", "Test_CurriculumCourse description");
        courseMap.put("mimeType", mimeType);
        courseMap.put("contentType", contentType);
        courseMap.put("code", "Test_CurriculumCourse");
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("course", courseMap);
            }});
        }};
        String data = TestUtil.mapToJson(requestMap);
        return Json.parse(data);
    }

    private JsonNode createCourseInvalidRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("content", courseMap);
            }});
        }};
        String data = TestUtil.mapToJson(requestMap);
        return Json.parse(data);
    }
}
