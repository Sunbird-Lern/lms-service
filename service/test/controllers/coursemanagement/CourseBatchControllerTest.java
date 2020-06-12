package controllers.coursemanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import actors.DummyActor;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.ACTOR_NAMES;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;


@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class CourseBatchControllerTest extends BaseApplicationTest {

  public static String COURSE_ID = "courseId";
  public static String COURSE_NAME = "courseName";
  public static int DAY_OF_MONTH = 2;
  public static String INVALID_ENROLLMENT_TYPE = "invalid";
  public static String BATCH_ID = "batchID";
  public static List<String> MENTORS = Arrays.asList("mentors");
  public static String INVALID_MENTORS_TYPE = "invalidMentorType";
  private static final String CREATE_BATCH_URL = "/v1/course/batch/create";
  private static final String UPDATE_BATCH_URL = "/v1/course/batch/update";
  private static final String GET_BATCH_URL =  "/v1/course/batch/read/"+BATCH_ID;
  private static final String SEARCH_BATCH_URL =  "/v1/course/batch/search";
  private static final String BATCH_PARTICIPANTS_LIST_URL =  "/v1/batch/participants/list";
  private static final String ADD_USERS_BATCH_URL =  "/v1/course/batch/users/add/"+BATCH_ID;
  private static final String REMOVE_USERS_BATCH_URL =  "/v1/course/batch/users/remove/"+BATCH_ID;


  @Before
  public void before() {
    setup(Arrays.asList(ACTOR_NAMES.COURSE_BATCH_MANAGEMENT_ACTOR,ACTOR_NAMES.SEARCH_HANDLER_ACTOR), DummyActor.class);
  }

  @Test
  public void testCreateBatchSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY,new Date(),getEndDate(true),getEnrollmentEndDate(true),null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testCreateBatchSuccessWithValidMentors() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY,new Date(),getEndDate(true),getEnrollmentEndDate(true),MENTORS ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }


  @Test
  public void testCreateBatchSuccessWithoutEndDate() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY,new Date(),null,null,null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testCreateBatchFailureWithInvalidEnrollmentType() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,INVALID_ENROLLMENT_TYPE,new Date(),getEndDate(true),getEnrollmentEndDate(true),null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testCreateBatchFailureWithInvalidMentorType() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY,new Date(),getEndDate(true),getEnrollmentEndDate(true),INVALID_MENTORS_TYPE ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testCreateBatchFailureWithEndDateBeforeStartDate() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY,new Date(),getEndDate(false),getEnrollmentEndDate(true),null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testCreateBatchFailureWithStartDateBeforeToday() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME, JsonKey.INVITE_ONLY, DateUtils.addDays(new Date(), -1), getEndDate(false), getEnrollmentEndDate(true), null))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals(400, result.status());
  }

  @Test
  public void testCreateBatchFailureWithSameStartAndEndDate() {
    Date currentdate = new Date();
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY, currentdate,
                            currentdate,null,null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testCreateBatchFailureWithEnrollmentEndDateBeforeStartDate() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(CREATE_BATCH_URL)
                    .bodyJson(createCourseBatchRequest(COURSE_ID, COURSE_NAME,JsonKey.INVITE_ONLY, new Date(),null,
                            getEnrollmentEndDate(false),null ))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }


  @Test
  public void testUpdateBatchSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UPDATE_BATCH_URL)
                    .bodyJson(updateCourseBatchRequest(COURSE_ID,BATCH_ID,COURSE_NAME,JsonKey.INVITE_ONLY, new Date(),
                            getEndDate(true),null ))
                    .method("PATCH");
    System.out.println(req.toString());
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testUpdateBatchSuccessWithoutEndDate() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UPDATE_BATCH_URL)
                    .bodyJson(updateCourseBatchRequest(COURSE_ID,BATCH_ID, COURSE_NAME,JsonKey.INVITE_ONLY, new Date(),
                            null,null ))
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testUpdateBatchFailureWithEndDateBeforeStartDate() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UPDATE_BATCH_URL)
                    .bodyJson(updateCourseBatchRequest(COURSE_ID,BATCH_ID, COURSE_NAME,JsonKey.INVITE_ONLY, new Date(),
                            getEndDate(false),null ))
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testUpdateBatchFailureWithSameStartAndEndDate() {
    Date currentDate = new Date();
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(UPDATE_BATCH_URL)
                    .bodyJson(updateCourseBatchRequest(COURSE_ID,BATCH_ID, COURSE_NAME,JsonKey.INVITE_ONLY, currentDate,
                            currentDate,null ))
                    .method("PATCH");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testGetBatchSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(GET_BATCH_URL)
                    .method("GET");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testSearchBatchSuccess() {
 //   setup(ACTOR_NAMES.SEARCH_HANDLER_ACTOR, DummyActor.class);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(SEARCH_BATCH_URL)
                    .bodyJson(searchCourseBatchRequest(true,false))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testSearchBatchSuccessWithoutFilters() {
 //   setup(ACTOR_NAMES.SEARCH_HANDLER_ACTOR, DummyActor.class);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(SEARCH_BATCH_URL)
                    .bodyJson(searchCourseBatchRequest(false,true))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testSearchBatchSuccessWithEmptyFilters() {
  //  setup(ACTOR_NAMES.SEARCH_HANDLER_ACTOR, DummyActor.class);
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(SEARCH_BATCH_URL)
                    .bodyJson(searchCourseBatchRequest(true,true))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testAddUserToBatchSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ADD_USERS_BATCH_URL)
                    .bodyJson(addAndRemoveUserToBatchRequest(true))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testAddUserToBatchFailureWithoutUserIds() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(ADD_USERS_BATCH_URL)
                    .bodyJson(addAndRemoveUserToBatchRequest(false))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testRemoveUserFromBatchSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(REMOVE_USERS_BATCH_URL)
                    .bodyJson(addAndRemoveUserToBatchRequest(true))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }

  @Test
  public void testRemoveUserToBatchFailureWithoutUserIds() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(REMOVE_USERS_BATCH_URL)
                    .bodyJson(addAndRemoveUserToBatchRequest(false))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testGetParticipantListFailureWithoutBatchId() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(BATCH_PARTICIPANTS_LIST_URL)
                    .bodyJson(getParticipantListRequest(false))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 400, result.status());
  }

  @Test
  public void testGetParticipantListSuccess() {
    Http.RequestBuilder req =
            new Http.RequestBuilder()
                    .uri(BATCH_PARTICIPANTS_LIST_URL)
                    .bodyJson(getParticipantListRequest(true))
                    .method("POST");
    Result result = Helpers.route(application, req);
    Assert.assertEquals( 200, result.status());
  }
  private JsonNode searchCourseBatchRequest(boolean isFilter, boolean isEmpty) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> filtermap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST,filtermap);
    if(isFilter)
    {
      if(isEmpty)
        filtermap.put(JsonKey.FILTERS,null);
      else
      {
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put(JsonKey.COURSE_ID,COURSE_ID);
        filtermap.put(JsonKey.FILTERS,innerMap);
      }
      requestMap.put(JsonKey.REQUEST,filtermap);
    }
    System.out.println(requestMap.toString());
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode getParticipantListRequest(boolean batchId) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> batchMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    if(batchId)
    {
      innerMap.put(JsonKey.BATCH_ID,BATCH_ID);
    }
    batchMap.put(JsonKey.BATCH,innerMap);
    requestMap.put(JsonKey.REQUEST, batchMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode addAndRemoveUserToBatchRequest(boolean isUserIds) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    if (isUserIds) {
      List<String> users = new ArrayList();
      users.add("123");
      innerMap.put(JsonKey.USER_IDs, users);
    }
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode createCourseBatchRequest(
      String courseId,
      String name,
      String enrollmentType,
      Date startDate,
      Date endDate,
      Date enrollmentEndDate,
      Object mentors) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (name != null) innerMap.put(JsonKey.NAME, name);
    if (enrollmentType != null) innerMap.put(JsonKey.ENROLLMENT_TYPE, enrollmentType);
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    if (startDate != null) innerMap.put(JsonKey.START_DATE, format.format(startDate));
    if (endDate != null) {
      innerMap.put(JsonKey.END_DATE, format.format(endDate));
    }
    if (enrollmentEndDate != null) {
      innerMap.put(JsonKey.ENROLLMENT_END_DATE, format.format(enrollmentEndDate));
    }
    if (mentors != null) innerMap.put(JsonKey.MENTORS, mentors);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  private JsonNode updateCourseBatchRequest(
          String courseId,
          String batchId,
          String name,
          String enrollmentType,
          Date startDate,
          Date endDate,
          Object mentors) {
    Map<String, Object> innerMap = new HashMap<>();
    if (courseId != null) innerMap.put(JsonKey.COURSE_ID, courseId);
    if (batchId != null) innerMap.put(JsonKey.ID, batchId);
    if (name != null) innerMap.put(JsonKey.NAME, name);
    if (enrollmentType != null) innerMap.put(JsonKey.ENROLLMENT_TYPE, enrollmentType);
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    if (startDate != null) innerMap.put(JsonKey.START_DATE, format.format(startDate));
    if (endDate != null) {
      innerMap.put(JsonKey.END_DATE, format.format(endDate));
    }
    if (mentors != null) innerMap.put(JsonKey.MENTORS, mentors);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, innerMap);
    String data = mapToJson(requestMap);
    return Json.parse(data);
  }

  public String mapToJson(Map map) {
    ObjectMapper mapperObj = new ObjectMapper();
    String jsonResp = "";

    if (map != null) {
      try {
        jsonResp = mapperObj.writeValueAsString(map);
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    return jsonResp;
  }


  private Date getEndDate(boolean isFuture) {
    Calendar calendar = Calendar.getInstance();
    if (isFuture) {
      calendar.add(Calendar.DAY_OF_MONTH, ++DAY_OF_MONTH);
    } else {
      calendar.add(Calendar.DAY_OF_MONTH, -DAY_OF_MONTH);
    }
    return calendar.getTime();
  }

  private Date getEnrollmentEndDate(boolean isFuture) {
    Calendar calendar = Calendar.getInstance();
    if (isFuture) {
      calendar.add(Calendar.DAY_OF_MONTH, DAY_OF_MONTH);
    } else {
      calendar.add(Calendar.DAY_OF_MONTH, -DAY_OF_MONTH);
    }
    return calendar.getTime();
  }
}
