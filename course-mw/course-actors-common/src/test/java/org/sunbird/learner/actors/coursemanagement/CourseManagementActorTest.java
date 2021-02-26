package org.sunbird.learner.actors.coursemanagement;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.sunbird.common.request.RequestContext;
import org.sunbird.keys.*;

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
    private static final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        PowerMockito.mockStatic(ProjectUtil.class);
        PowerMockito.mockStatic(Unirest.class);
        system = ActorSystem.create("system");
        when(ProjectUtil.getConfigValue(JsonKey.EKSTEP_BASE_URL))
                .thenReturn("ekstep_api_base_url");
        when(ProjectUtil.getConfigValue(JsonKey.CONTENT_PROPS_TO_ADD))
                .thenReturn("learning.content.props.to.add");
    }

    @Test
    public void testCourseCreateSuccess() throws Exception {
        mockCreateResponse();
        Response response = (Response) doRequest(createCourseRequest(), false);
        Assert.assertNotNull(response);
    }

    @Test
    public void testCourseCreateCopySuccess() throws Exception {
        mockCopyResponse();
        Response response = (Response) doRequest(createCourseCopyRequest(), false);
        Assert.assertNotNull(response);
    }

    @Test
    public void testCourseCreateHierarchySuccess() throws Exception {
        mockCreateResponse();
        mockUpdateHierarchyResponseSuccess();
        Response response = (Response) doRequest(createCourseHierarchyRequest(), false);
        Assert.assertNotNull(response);
    }

    @Test
    public void testCourseCreateHierarchyClientException() throws Exception {
        mockCreateResponse();
        mockUpdateHierarchyResponseSuccess();
        ProjectCommonException res = (ProjectCommonException) doRequest(createCourseHierarchyRequestClientException(), true);
        Assert.assertEquals("visibility is a mandatory parameter for child of parent with id: do_1130532922458931201385", res.getMessage());
    }


    private Object doRequest(Map<String, Object> data, Boolean error) throws Exception {
        TestKit probe = new TestKit(system);
        ActorRef toc = system.actorOf(props);
        Request request = new Request();
        request.setRequestContext(new RequestContext(
            JsonKey.SERVICE_NAME,
            JsonKey.PRODUCER_NAME,
            "test",
            "X_DEVICE_ID",
            "X_SESSION_ID",
            JsonKey.PID,JsonKey.P_VERSION, null));
        request.getRequest().putAll(data);
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

    private void mockCopyResponse() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.post(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"api.content.copy\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-06-30T07:49:40ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"83c737dc-33d7-4601-ba09-a0567642f5d2\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"node_id\": {\n" +
                "            \"do_1126971789600276481279\": \"do_11305397968749363211455\"\n" +
                "        },\n" +
                "        \"versionKey\": \"1593503379825\"\n" +
                "    }\n" +
                "}");
    }

    private void mockCreateResponse() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.post(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"api.content.create\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-06-29T08:31:03ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"7f88375d-4c59-4f3a-9626-be0fd3b3ad72\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"identifier\": \"do_1130532922458931201385\",\n" +
                "        \"node_id\": \"do_1130532922458931201385\",\n" +
                "        \"versionKey\": \"1593419463612\"\n" +
                "    }\n" +
                "}");
    }

    private void mockUpdateHierarchyResponseSuccess() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.patch(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"api.content.hierarchy.update\",\n" +
                "    \"ver\": \"3.0\",\n" +
                "    \"ts\": \"2020-06-30T07:58:04ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"d0e18676-97dd-4b45-a7d0-d4f7b4b23198\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"content_id\": \"do_113051195498643456145\",\n" +
                "        \"identifiers\": {\n" +
                "            \"do_1130482530313338881716\": \"do_113053983819718656110\",\n" +
                "            \"do_1130482530313338881717\": \"do_113053983819726848112\"\n" +
                "        }\n" +
                "    }\n" +
                "}");
    }


    private Map<String, Object> createCourseRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put(SunbirdKey.NAME, "Test_CurriculumCourse With 3 Units");
        courseMap.put(SunbirdKey.DESCRIPTION, "Test_CurriculumCourse description");
        courseMap.put(SunbirdKey.MIME_TYPE, "application/vnd.ekstep.content-collection");
        courseMap.put(SunbirdKey.CONTENT_TYPE, "Course");
        courseMap.put(SunbirdKey.CODE, "Test_CurriculumCourse");
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
                put(SunbirdKey.COURSE, courseMap);
        }};
        return requestMap;
    }

    private Map<String, Object> createCourseCopyRequest() {
        Map<String, Object> courseMap = new HashMap<>();
        courseMap.put(SunbirdKey.NAME, "Test_CurriculumCourse With 3 Units");
        courseMap.put(SunbirdKey.CODE, "Test_CurriculumCourse");
        courseMap.put(SunbirdKey.COPY_SCHEME, "TextBookToCourse");
        courseMap.put(SunbirdKey.CREATED_BY, "testCreatedBy");
        courseMap.put(SunbirdKey.COURSE_CREATED_FOR, Arrays.asList("abc"));
        courseMap.put(SunbirdKey.FRAMEWORK, "testFramework");
        courseMap.put(SunbirdKey.ORGANISATION, Arrays.asList("abc"));
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
                put(SunbirdKey.SOURCE, "do_123");
                put(SunbirdKey.COURSE, courseMap);
        }};
        return requestMap;
    }

    private Map<String, Object> createCourseHierarchyRequest() throws Exception {
       String request = "{\n" +
               "        \"course\": {\n" +
               "            \"name\": \"Test_CurriculumCourse With 3 Units\",\n" +
               "            \"code\": \"test-code\",\n" +
               "            \"description\": \"abc\",\n" +
               "            \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
               "            \"contentType\": \"Course\"\n" +
               "        },\n" +
               "        \"hierarchy\": [\n" +
               "            {\n" +
               "                \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
               "                \"code\": \"tbu\",\n" +
               "                \"contentType\": \"TextBookUnit\",\n" +
               "                \"identifier\": \"do_11304065956451123217\",\n" +
               "                \"name\": \"UNIT-2\",\n" +
               "                \"visibility\": \"Parent\",\n" +
               "                \"children\": [\n" +
               "                    {\n" +
               "                        \"mimeType\": \"application/pdf\",\n" +
               "                        \"contentType\": \"MarkingSchemeRubric\",\n" +
               "                        \"identifier\": \"do_11303997216713113613821\",\n" +
               "                        \"visibility\": \"Default\",\n" +
               "                        \"resourceType\": \"Read\",\n" +
               "                        \"name\": \"Resource 1\"\n" +               "                    },\n" +
               "                    {\n" +
               "                        \"mimeType\": \"application/pdf\",\n" +
               "                        \"contentType\": \"LearningOutcomeDefinition\",\n" +
               "                        \"identifier\": \"do_11304337189392384011143\",\n" +
               "                        \"visibility\": \"Default\",\n" +
               "                        \"resourceType\": \"Read\",\n" +
               "                        \"name\": \"Resource 2\"\n" +               "                    }\n" +
               "                ]\n" +
               "            }\n" +
               "        ]\n" +
               "}";
       return mapper.readValue(request, new TypeReference< Map<String, Object>> () {});
    }

    private Map<String, Object> createCourseHierarchyRequestClientException() throws Exception {
        String request = "{\n" +
                "        \"course\": {\n" +
                "            \"name\": \"Test_CurriculumCourse With 3 Units\",\n" +
                "            \"code\": \"test-code\",\n" +
                "            \"description\": \"abc\",\n" +
                "            \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
                "            \"contentType\": \"Course\"\n" +
                "        },\n" +
                "        \"hierarchy\": [\n" +
                "            {\n" +
                "                \"mimeType\": \"application/vnd.ekstep.content-collection\",\n" +
                "                \"code\": \"tbu\",\n" +
                "                \"contentType\": \"TextBookUnit\",\n" +
                "                \"identifier\": \"do_11304065956451123217\",\n" +
                "                \"name\": \"UNIT-2\",\n" +
                "                \"children\": [\n" +
                "                    {\n" +
                "                        \"mimeType\": \"application/pdf\",\n" +
                "                        \"contentType\": \"MarkingSchemeRubric\",\n" +
                "                        \"identifier\": \"do_11303997216713113613821\",\n" +
                "                        \"visibility\": \"Default\",\n" +
                "                        \"resourceType\": \"Read\",\n" +
                "                        \"name\": \"Resource 1\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                        \"mimeType\": \"application/pdf\",\n" +
                "                        \"contentType\": \"LearningOutcomeDefinition\",\n" +
                "                        \"identifier\": \"do_11304337189392384011143\",\n" +
                "                        \"visibility\": \"Default\",\n" +
                "                        \"resourceType\": \"Read\",\n" +
                "                        \"name\": \"Resource 2\"\n" +
                "                    }\n" +
                "                ]\n" +
                "            }\n" +
                "        ]\n" +
                "}";
        return mapper.readValue(request, new TypeReference< Map<String, Object>> () {});
    }

}