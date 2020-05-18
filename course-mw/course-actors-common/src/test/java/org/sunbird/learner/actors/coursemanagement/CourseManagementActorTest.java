package org.sunbird.learner.actors.coursemanagement;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static akka.testkit.JavaTestKit.duration;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        ProjectUtil.class,
        Unirest.class
})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*"})
public class CourseManagementActorTest {

    private static ActorSystem system;
    private static final Props props =
            Props.create(org.sunbird.learner.actors.course.CourseManagementActor.class);


    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(ProjectUtil.class);
        PowerMockito.mockStatic(Unirest.class);
        system = ActorSystem.create("system");
        when(ProjectUtil.getConfigValue(JsonKey.EKSTEP_BASE_URL))
                .thenReturn("ekstep_api_base_url");
    }

    @Test
    public void testCourseCreateSuccess() throws UnirestException, IOException {
        mockResponseUnirest();
        Response response = (Response) doRequest(false, createCourseRequest());
        Assert.assertNotNull(response);
    }

    @Test
    public void testCourseCreateCopySuccess() throws UnirestException, IOException {
        mockResponseUnirest();
        Response response = (Response) doRequest(false, createCourseCopyRequest());
        Assert.assertNotNull(response);
    }

    @Test
    public void testCourseCreateFailure() throws UnirestException, IOException {
        mockResponseUnirest();
        Response response = (Response) doRequest(false, createCourseInvalidRequest());
        Assert.assertNotNull(ResponseCode.customServerError.getErrorCode(), response.getResponseCode());
    }

    @Test
    public void testCourseCreateCopyFailure() throws UnirestException, IOException {
        mockResponseUnirest();
        Response response = (Response) doRequest(false, createCourseCopyInvalidRequest());
        Assert.assertNotNull(ResponseCode.customServerError.getErrorCode(), response.getResponseCode());
    }

    private Object doRequest(boolean error, Map<String, Object> data) throws IOException {
        TestKit probe = new TestKit(system);
        ActorRef toc = system.actorOf(props);
        Request request = new Request();
        request.getRequest().put(JsonKey.COURSE, data);
        request.setOperation("createCourse");
        toc.tell(request, probe.getRef());
        if (error) {
            ProjectCommonException res =
                    probe.expectMsgClass(duration("10 second"), ProjectCommonException.class);
            return res;
        }
        Response response = probe.expectMsgClass(duration("10 second"), Response.class);
        return response;
    }

    private void mockResponseUnirest() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.post(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getBody()).thenReturn("{\"responseCode\" :\"OK\" }");
    }

    private Map<String, Object> createCourseRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put("name", "Test_CurriculumCourse With 3 Units");
        courseMap.put("description", "Test_CurriculumCourse description");
        courseMap.put("mimeType", "application/vnd.ekstep.content-collection");
        courseMap.put("contentType", "Course");
        courseMap.put("code", "Test_CurriculumCourse");
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("course", courseMap);
            }});
        }};
        return requestMap;
    }

    private Map<String, Object> createCourseCopyRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put("name", "Test_CurriculumCourse With 3 Units");
        courseMap.put("code", "Test_CurriculumCourse");
        courseMap.put("copyScheme", "TextBookToCourse");
        courseMap.put("createdBy", "testCreatedBy");
        courseMap.put("createdFor", Arrays.asList("abc"));
        courseMap.put("framework", "testFramework");
        courseMap.put("organisation", Arrays.asList("abc"));
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("source", "do_123");
                put("course", courseMap);
            }});
        }};
        return requestMap;
    }

    private Map<String, Object> createCourseInvalidRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put("name", "Test_CurriculumCourse With 3 Units");
        courseMap.put("description", "Test_CurriculumCourse description");
        courseMap.put("mimeType", "invalidMimeType");
        courseMap.put("contentType", "Course");
        courseMap.put("code", "Test_CurriculumCourse");
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("course", courseMap);
            }});
        }};
        return requestMap;
    }

    private Map<String, Object> createCourseCopyInvalidRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put("name", "Test_CurriculumCourse With 3 Units");
        courseMap.put("code", "Test_CurriculumCourse");
        courseMap.put("copyScheme", "TextBookToCourse");
        courseMap.put("createdBy", "testCreatedBy");
        courseMap.put("createdFor", Arrays.asList("abc"));
        courseMap.put("organisation", Arrays.asList("abc"));
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put("request", new HashMap<String, Object>() {{
                put("source", "do_123");
                put("course", courseMap);
            }});
        }};
        return requestMap;
    }
}