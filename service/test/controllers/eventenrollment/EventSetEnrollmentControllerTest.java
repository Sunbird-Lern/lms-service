package controllers.eventenrollment;

import actors.DummyActor;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseApplicationTest;
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
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class EventSetEnrollmentControllerTest extends BaseApplicationTest {
    String COURSE_ID = "courseId";
    String FIXED_BATCH_ID = "fixedBatchId";
    String BATCH_ID = "batchId";
    String USER_ID = "userId";
    String ENROLL_EVENTSET_URL = "/v1/eventset/enroll";
    String UENROLL_EVENTSET_URL = "/v1/eventset/unenroll";

    @Before
    public void before() {
        setup(Arrays.asList(ACTOR_NAMES.EVENT_SET_ENROLMENT_ACTOR), DummyActor.class);
    }

    @Test
    public void testEnrollCourseBatchSuccess() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(ENROLL_EVENTSET_URL)
                        .bodyJson(createEventSetEnrollmentRequest(COURSE_ID, FIXED_BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    @Test
    public void testUnEnrollCourseBatchSuccess() {
        Http.RequestBuilder req =
                new Http.RequestBuilder()
                        .uri(UENROLL_EVENTSET_URL)
                        .bodyJson(createEventSetEnrollmentRequest(COURSE_ID, FIXED_BATCH_ID, USER_ID))
                        .method("POST");
        Result result = Helpers.route(application, req);
        Assert.assertEquals( 200, result.status());
    }

    private JsonNode createEventSetEnrollmentRequest(
            String courseId, String batchId, String userId) {
        Map<String, Object> innerMap = new HashMap<>();
        if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
        if (batchId != null) innerMap.put(JsonKey.FIXED_BATCH_ID, batchId);
        if (userId != null) innerMap.put(JsonKey.USER_ID, userId);
        innerMap.put(JsonKey.BATCH_ID, batchId);
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(JsonKey.REQUEST, innerMap);
        String data = mapToJson(requestMap);
        return Json.parse(data);
    }
}
