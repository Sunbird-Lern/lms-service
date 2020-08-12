package org.sunbird.learner.actors.coursebatch;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.javadsl.TestKit;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil.ProgressStatus;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.ContentUtil;
import scala.concurrent.Future;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ContentUtil.class,
  ServiceFactory.class,
  ElasticSearchHelper.class,
  EsClientFactory.class
})
@PowerMockIgnore("javax.management.*")
@Ignore
public class CourseEnrollmentActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(CourseEnrollmentActor.class);
  private CassandraOperation cassandraOperation;
  private String userId = "someUserId";
  private String batchId = "someBatchId";
  private String courseId = "someCourseId";
  private String id = "someid";
  private String courseName = "someCourseName";
  private String courseDescription = "someCourseDescription";
  private String courseAppIcon = "somecourseAppIcon";
  private static ElasticSearchService esService;
  private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
    PowerMockito.mockStatic(ContentUtil.class);
  }

  @Before
  public void beforeEachTest() {
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = mock(CassandraOperationImpl.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    PowerMockito.mockStatic(EsClientFactory.class);
    PowerMockito.mockStatic(ElasticSearchHelper.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
  }

  private void mockUserCoursesEsResponse(boolean isEmpty) {
    Map<String, Object> userCourses = new HashMap<>();
    List<Map<String, Object>> l1 = new ArrayList<>();
    if (!isEmpty) {
      l1.add(getMapforUserCourses(batchId, "user1"));
    }
    userCourses.put(JsonKey.CONTENT, l1);
    Promise<Map<String, Object>> promiseUserCourses = Futures.promise();
    promiseUserCourses.success(userCourses);
    Future<Map<String, Object>> ucf = promiseUserCourses.future();
    when(esService.search(Mockito.any(), Mockito.any())).thenReturn(ucf);
    when(ElasticSearchHelper.getResponseFromFuture(ucf)).thenReturn(userCourses);
  }

  private Map<String, Object> getMapforUserCourses(String batchId, String userId) {
    Map<String, Object> result = new HashMap<>();
    result.put(JsonKey.BATCH_ID, batchId);
    result.put(JsonKey.USER_ID, userId);
    return result;
  }

  @Test
  public void testEnrollCourseSuccessForNotStartedBatch() {
    Response response =
        getEnrollSuccessTestResponse(true, false, ProgressStatus.NOT_STARTED.getValue());
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testEnrollCourseSuccessForStartedBatch() {
    Response response =
        getEnrollSuccessTestResponse(true, false, ProgressStatus.STARTED.getValue());
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testEnrollCourseSuccessAfterUnenroll() {
    Response response =
        getEnrollSuccessTestResponse(false, false, ProgressStatus.STARTED.getValue());
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testEnrollCourseFailureForAlreadyCompletedBatch() {
    ProjectCommonException exception =
        getEnrollFailureTestResponse(true, false, ProgressStatus.COMPLETED.getValue());
    Assert.assertTrue(
        null != exception
            && exception.getResponseCode() == ResponseCode.CLIENT_ERROR.getResponseCode());
  }

  @Test
  public void testUnenrollCourseFailureWithoutEnroll() {
    ProjectCommonException exception =
        getUnenrollFailureTestResponse(true, false, ProgressStatus.STARTED.getValue());
    Assert.assertTrue(
        null != exception
            && exception.getResponseCode() == ResponseCode.CLIENT_ERROR.getResponseCode());
  }

  @Test
  public void testUnenrollCourseFailureForAlreadyUnenrolledBatch() {
    ProjectCommonException exception =
        getUnenrollFailureTestResponse(false, false, ProgressStatus.STARTED.getValue());
    Assert.assertTrue(
        null != exception
            && exception.getResponseCode() == ResponseCode.CLIENT_ERROR.getResponseCode());
  }

  @Test
  public void testUnenrollCourseFailureForAlreadyCompletedBatch() {
    ProjectCommonException exception =
        getUnenrollFailureTestResponse(false, false, ProgressStatus.COMPLETED.getValue());
    Assert.assertTrue(
        null != exception
            && exception.getResponseCode() == ResponseCode.CLIENT_ERROR.getResponseCode());
  }

  private Response getEnrollSuccessTestResponse(
      boolean isUserFirstTimeEnrolled, boolean userEnrollStatus, int batchStatus) {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    if (isUserFirstTimeEnrolled) {
      Response insertResponse = createCassandraInsertSuccessResponse();
      when(cassandraOperation.insertRecord(
              Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
          .thenReturn(insertResponse);
    }
    mockUserCoursesEsResponse(true);
    mockCassandraRequestForReadRecordById(isUserFirstTimeEnrolled, userEnrollStatus, batchStatus);
    subject.tell(
        createRequest(userId, batchId, courseId, ActorOperations.ENROLL_COURSE.getValue()),
        probe.getRef());
    Response response = probe.expectMsgClass(Duration.ofSeconds(10), Response.class);
    return response;
  }

  private ProjectCommonException getEnrollFailureTestResponse(
      boolean isUserFirstTimeEnrolled, boolean userEnrollStatus, int batchStatus) {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    mockCassandraRequestForReadRecordById(isUserFirstTimeEnrolled, userEnrollStatus, batchStatus);
    subject.tell(
        createRequest(userId, batchId, courseId, ActorOperations.ENROLL_COURSE.getValue()),
        probe.getRef());
    ProjectCommonException exception =
        probe.expectMsgClass(Duration.ofSeconds(10), ProjectCommonException.class);
    return exception;
  }

  private Response getUnenrollSuccessTestResponse(
      boolean isUserFirstTimeEnrolled, boolean userEnrollStatus, int batchStatus) {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    mockCassandraRequestForReadRecordById(isUserFirstTimeEnrolled, userEnrollStatus, batchStatus);
    subject.tell(
        createRequest(userId, batchId, courseId, ActorOperations.UNENROLL_COURSE.getValue()),
        probe.getRef());
    Response response = probe.expectMsgClass(Duration.ofSeconds(10), Response.class);
    return response;
  }

  private ProjectCommonException getUnenrollFailureTestResponse(
      boolean isUserFirstTimeEnrolled, boolean userEnrollStatus, int batchStatus) {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    mockCassandraRequestForReadRecordById(isUserFirstTimeEnrolled, userEnrollStatus, batchStatus);
    subject.tell(
        createRequest(userId, batchId, courseId, ActorOperations.UNENROLL_COURSE.getValue()),
        probe.getRef());
    ProjectCommonException exception = probe.expectMsgClass(ProjectCommonException.class);
    return exception;
  }

  private Response createCassandraInsertSuccessResponse() {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    return response;
  }

  private void mockCassandraRequestForReadRecordById(
      boolean isUserFirstTimeEnrolled, boolean userEnrollStatus, int batchStatus) {
    if (isUserFirstTimeEnrolled)
      when(cassandraOperation.getRecordByIdentifier(
              Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList(), Mockito.any()))
          .thenReturn(
              createGetCourseBatchSuccessResponse(batchStatus),
              createGetUserCourseFailureResponse());
    else
      when(cassandraOperation.getRecordByIdentifier(
              Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList(), Mockito.any()))
          .thenReturn(
              createGetCourseBatchSuccessResponse(batchStatus),
              createGetUserCourseSuccessResponse(userEnrollStatus, batchStatus));
  }

  private Response createGetUserCourseFailureResponse() {
    Response response = new Response();
    List<Map<String, Object>> result = new ArrayList<>();
    response.put(JsonKey.RESPONSE, result);
    return response;
  }

  private Response createGetUserCourseSuccessResponse(boolean active, int batchStatus) {
    Response response = new Response();
    Map<String, Object> userMap = new HashMap<>();
    userMap.put(JsonKey.USER_ID, userId);
    userMap.put(JsonKey.BATCH_ID, batchId);
    userMap.put(JsonKey.COURSE_ID, courseId);
    userMap.put(JsonKey.ACTIVE, active);
    userMap.put(JsonKey.PROGRESS, batchStatus);
    userMap.put(JsonKey.ID, id);
    List<Map<String, Object>> result = new ArrayList<>();
    result.add(userMap);
    response.put(JsonKey.RESPONSE, result);
    return response;
  }

  private Response createGetCourseBatchSuccessResponse(int status) {
    Response response = new Response();
    Map<String, Object> userMap = new HashMap<>();
    userMap.put(JsonKey.COURSE_ID, courseId);
    userMap.put(JsonKey.ID, batchId);
    userMap.put(JsonKey.STATUS, status);
    userMap.put(JsonKey.START_DATE, calculateDate(0));
    userMap.put(JsonKey.END_DATE, calculateDate(4));
    List<Map<String, Object>> result = new ArrayList<>();
    result.add(userMap);
    response.put(JsonKey.RESPONSE, result);
    return response;
  }

  public Request createRequest(String userId, String batchId, String courseId, String operation) {
    Request actorMessage = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.REQUESTED_BY, userId);
    actorMessage.setContext(innerMap);
    actorMessage.setOperation(operation);
    HashMap<String, Object> requestBody = new HashMap<>();
    requestBody.put(JsonKey.BATCH_ID, batchId);
    requestBody.put(JsonKey.USER_ID, userId);
    requestBody.put(JsonKey.COURSE_ID, courseId);
    actorMessage.setRequest(requestBody);

    return actorMessage;
  }

  private String calculateDate(int dayOffset) {

    Calendar calender = Calendar.getInstance();
    calender.add(Calendar.DAY_OF_MONTH, dayOffset);
    return format.format(calender.getTime());
  }
}
