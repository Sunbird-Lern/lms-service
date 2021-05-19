package org.sunbird.learner.actors.coursemanagement;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.CourseBatchManagementActor;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.learner.util.CourseBatchUtil;
import org.sunbird.learner.util.JsonUtil;
import org.sunbird.learner.util.Util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static akka.testkit.JavaTestKit.duration;
import static org.powermock.api.mockito.PowerMockito.doCallRealMethod;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class, EsClientFactory.class, CourseBatchUtil.class, Util.class, ContentUtil.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class CourseBatchManagementActorTest {

  public ActorSystem system = ActorSystem.create("system");
  public static final Props props = Props.create(CourseBatchManagementActor.class);
  private static CassandraOperationImpl mockCassandraOperation;
  private static final String BATCH_ID = "123";
  private static final String BATCH_NAME = "Some Batch Name";
  SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

  private String existingStartDate = "";
  private String existingEndDate = "";
  private String existingEnrollmentEndDate = "";

  @Before
  public void setUp() throws Exception {
    mockCassandraOperation = mock(CassandraOperationImpl.class);
    ActorRef actorRef = mock(ActorRef.class);
    PowerMockito.mockStatic(ServiceFactory.class);
    when(ServiceFactory.getInstance()).thenReturn(mockCassandraOperation);
    PowerMockito.mockStatic(CourseBatchUtil.class);
    PowerMockito.mockStatic(ContentUtil.class);
    courseBatchUtilDateMethods();
  }

  private String calculateDate(int dayOffset) {

    Calendar calender = Calendar.getInstance();
    calender.add(Calendar.DAY_OF_MONTH, dayOffset);
    return format.format(calender.getTime());
  }

  private ProjectCommonException performUpdateCourseBatchFailureTest(
      String startDate,
      String enrollmentEndDate,
      String endDate,
      Response mockGetRecordByIdResponse) throws Exception {
    when(mockCassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(mockGetRecordByIdResponse);
    mockCourseEnrollmentActor();
   
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.UPDATE_BATCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.ID, BATCH_ID);
    innerMap.put(JsonKey.NAME, BATCH_NAME);
    innerMap.put(JsonKey.START_DATE, startDate);
    innerMap.put(JsonKey.END_DATE, endDate);
    innerMap.put(JsonKey.ENROLLMENT_END_DATE, enrollmentEndDate);
    reqObj.getRequest().putAll(innerMap);
    subject.tell(reqObj, probe.getRef());

    ProjectCommonException exception =
        probe.expectMsgClass(duration("10 second"), ProjectCommonException.class);
    return exception;
  }

  private Response performUpdateCourseBatchSuccessTest(
      String startDate,
      String enrollmentEndDate,
      String endDate,
      Response mockGetRecordByIdResponse,
      Response mockUpdateRecordResponse) throws Exception {

    when(mockCassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(mockGetRecordByIdResponse);

    when(mockCassandraOperation.updateRecord(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyMap()))
        .thenReturn(mockUpdateRecordResponse);

    PowerMockito.doNothing().when(CourseBatchUtil.class);
    CourseBatchUtil.syncCourseBatchForeground(null, BATCH_ID, new HashMap<>());
    PowerMockito.mockStatic(ContentUtil.class);
    mockCourseEnrollmentActor();

    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.UPDATE_BATCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.ID, BATCH_ID);
    innerMap.put(JsonKey.COURSE_ID, "someCourseId");
    innerMap.put(JsonKey.NAME, BATCH_NAME);

    if (startDate != null) {
      innerMap.put(JsonKey.START_DATE, startDate);
    }
    if (endDate != null) {
      innerMap.put(JsonKey.END_DATE, endDate);
    }
    if (enrollmentEndDate != null) {
      innerMap.put(JsonKey.ENROLLMENT_END_DATE, enrollmentEndDate);
    }
    reqObj.getRequest().putAll(innerMap);
    subject.tell(reqObj, probe.getRef());

    Response response = probe.expectMsgClass(duration("10 second"), Response.class);
    return response;
  }

  private Response getMockCassandraResult() {

    Response response = new Response();
    response.put("response", "SUCCESS");
    return response;
  }

  private Response getMockCassandraRecordByIdResponse(int batchProgressStatus) throws Exception {

    Response response = new Response();
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> courseResponseMap = new HashMap<>();

    courseResponseMap.put(JsonKey.BATCH_ID, BATCH_ID);
    courseResponseMap.put(JsonKey.VER, "v1");
    courseResponseMap.put(JsonKey.NAME, BATCH_NAME);
    courseResponseMap.put(JsonKey.ENROLMENTTYPE, JsonKey.INVITE_ONLY);
    courseResponseMap.put(JsonKey.COURSE_ID, "someCourseId");
    courseResponseMap.put(JsonKey.COURSE_CREATED_FOR, new ArrayList<Object>());
    courseResponseMap.put(JsonKey.STATUS, batchProgressStatus);
    courseResponseMap.put(CourseJsonKey.CERTIFICATE_TEMPLATES_COLUMN, getCertTemplate());


    if (batchProgressStatus == ProjectUtil.ProgressStatus.STARTED.getValue()) {

      existingStartDate = calculateDate(-4);
      existingEnrollmentEndDate = calculateDate(1);
      existingEndDate = calculateDate(3);
    } else if (batchProgressStatus == ProjectUtil.ProgressStatus.NOT_STARTED.getValue()) {

      existingStartDate = calculateDate(2);
      existingEnrollmentEndDate = calculateDate(3);
      existingEndDate = calculateDate(4);
    } else {

      existingStartDate = calculateDate(-4);
      existingEnrollmentEndDate = calculateDate(-3);
      existingEndDate = calculateDate(-2);
    }

    courseResponseMap.put(JsonKey.START_DATE, existingStartDate);
    courseResponseMap.put(JsonKey.ENROLLMENT_END_DATE, existingEnrollmentEndDate);
    courseResponseMap.put(JsonKey.END_DATE, existingEndDate);

    list.add(courseResponseMap);
    response.put(JsonKey.RESPONSE, list);

    return response;
  }

  private String getOffsetDate(String date, int offSet) {

    try {
      Calendar calender = Calendar.getInstance();
      calender.setTime(format.parse(date));
      calender.add(Calendar.DATE, offSet);
      return format.format(calender.getTime());
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Test
  public void checkTelemetryKeyFailure() throws Exception {

    String telemetryEnvKey = "batch";
    String envKey = "CourseBatchManagementActor";
    PowerMockito.mockStatic(Util.class);
    doNothing()
        .when(
            Util.class,
            "initializeContext",
            Mockito.any(Request.class),
            Mockito.eq(telemetryEnvKey),
            Mockito.eq(envKey));

    int batchProgressStatus = ProjectUtil.ProgressStatus.STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    Response mockUpdateRecordResponse = getMockCassandraResult();
    Response response =
        performUpdateCourseBatchSuccessTest(
            existingStartDate,
            null,
            existingEndDate,
            mockGetRecordByIdResponse,
            mockUpdateRecordResponse);
    Assert.assertTrue(!(telemetryEnvKey.charAt(0) >= 65 && telemetryEnvKey.charAt(0) <= 90));
  }

  @Test
  public void testUpdateEnrollmentEndDateFailureBeforeStartDate() throws Exception {
    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 0),
            getOffsetDate(existingEnrollmentEndDate, -2),
            getOffsetDate(existingEndDate, 0),
            mockGetRecordByIdResponse);

    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.enrollmentEndDateStartError.getErrorCode()));
  }

  @Test
  public void testUpdateEnrollmentEndDateFailureAfterEndDate() throws Exception {
    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 0),
            getOffsetDate(existingEnrollmentEndDate, 2),
            getOffsetDate(existingEndDate, 0),
            mockGetRecordByIdResponse);

    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.enrollmentEndDateEndError.getErrorCode()));
  }

  @Test
  public void testUpdateStartedCourseBatchFailureWithStartDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 1), null, null, mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchStartDateError.getErrorCode()));
  }

  @Test
  public void testUpdateStartedCourseBatchFailureWithEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            null, getOffsetDate(existingEndDate, 4), null, mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.courseBatchStartDateRequired.getErrorCode()));
  }

  @Test
  public void testUpdateStartedCourseBatchFailureWithDifferentStartDateAndEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 2),
            null,
            getOffsetDate(existingEndDate, 4),
            mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchStartDateError.getErrorCode()));
  }

  @Test
  public void testUpdateStartedCourseBatchSuccessWithFutureEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    Response mockUpdateRecordResponse = getMockCassandraResult();
    Response response =
        performUpdateCourseBatchSuccessTest(
            existingStartDate,
            null,
            getOffsetDate(existingEndDate, 2),
            mockGetRecordByIdResponse,
            mockUpdateRecordResponse);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testUpdateStartedCourseBatchSuccessWithEnrollmentEndEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    Response mockUpdateRecordResponse = getMockCassandraResult();
    Response response =
        performUpdateCourseBatchSuccessTest(
            getOffsetDate(existingStartDate, 0),
            getOffsetDate(existingEnrollmentEndDate, 1),
            getOffsetDate(existingEndDate, 0),
            mockGetRecordByIdResponse,
            mockUpdateRecordResponse);

    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testUpdateNotStartedCourseBatchSuccessWithFutureStartDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    Response mockUpdateRecordResponse = getMockCassandraResult();
    Response response =
        performUpdateCourseBatchSuccessTest(
            getOffsetDate(existingStartDate, 2),
            null,
            null,
            mockGetRecordByIdResponse,
            mockUpdateRecordResponse);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testUpdateNotStartedCourseBatchFailureWithFutureEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            null, null, calculateDate(4), mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.courseBatchStartDateRequired.getErrorCode()));
  }

  @Test
  public void testUpdateNotStartedCourseBatchSuccessWithFutureStartDateAndEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    Response mockUpdateRecordResponse = getMockCassandraResult();
    Response response =
        performUpdateCourseBatchSuccessTest(
            getOffsetDate(existingStartDate, 2),
            null,
            getOffsetDate(existingEndDate, 4),
            mockGetRecordByIdResponse,
            mockUpdateRecordResponse);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
  }

  @Test
  public void testUpdateNotStartedCourseBatchFailureWithPastStartDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);

    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            calculateDate(-4), null, null, mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchStartDateError.getErrorCode()));
  }

  @Test
  public void testUpdateNotStartedCourseBatchFailureWithPastEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            null, null, calculateDate(-2), mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.courseBatchStartDateRequired.getErrorCode()));
  }

  @Test
  public void testUpdateNotStartedCourseBatchFailureWithEndDateBeforeFutureStartDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.NOT_STARTED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 6),
            null,
            getOffsetDate(existingEndDate, 2),
            mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchEndDateError.getErrorCode()));
  }

  @Test
  public void testUpdateCompletedCourseBatchFailureWithStartDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 2), null, null, mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchStartDateError.getErrorCode()));
  }

    @Test
    public void testUpdateCompletedCourseBatchSuccessWithEndDateNull() throws Exception {

        int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
        Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
        Response mockUpdateRecordResponse = getMockCassandraResult();
        Response response =
                performUpdateCourseBatchSuccessTest(
                        existingStartDate, null, null, mockGetRecordByIdResponse, mockUpdateRecordResponse);
        Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    }

    @Test
    public void testUpdateCompletedCourseBatchSuccessWithEndDateExtended() throws Exception {

        int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
        Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
        Response mockUpdateRecordResponse = getMockCassandraResult();
        Response response =
                performUpdateCourseBatchSuccessTest(
                        existingStartDate, null, getOffsetDate(existingEndDate, 3), mockGetRecordByIdResponse, mockUpdateRecordResponse);
        Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    }

    @Test
    public void testUpdateCompletedCourseBatchFailureWithEndDateExtended() throws Exception {

        int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
        Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
        Response mockUpdateRecordResponse = getMockCassandraResult();
        ProjectCommonException exception =
                performUpdateCourseBatchFailureTest(
                        existingStartDate, null, getOffsetDate(existingEndDate, -1), mockGetRecordByIdResponse);
        Assert.assertTrue(
                ((ProjectCommonException) exception)
                        .getCode()
                        .equals(ResponseCode.courseBatchEndDateError.getErrorCode()));
    }
  

  @Test
  public void testUpdateCompletedCourseBatchFailureWithEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            null, null, getOffsetDate(existingEndDate, 4), mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.courseBatchStartDateRequired.getErrorCode()));
  }

  @Test
  public void testUpdateCompletedCourseBatchFailureWithStartDateAndEndDate() throws Exception {

    int batchProgressStatus = ProjectUtil.ProgressStatus.COMPLETED.getValue();
    Response mockGetRecordByIdResponse = getMockCassandraRecordByIdResponse(batchProgressStatus);
    ProjectCommonException exception =
        performUpdateCourseBatchFailureTest(
            getOffsetDate(existingStartDate, 2),
            null,
            getOffsetDate(existingEndDate, 4),
            mockGetRecordByIdResponse);
    Assert.assertTrue(
        ((ProjectCommonException) exception)
            .getCode()
            .equals(ResponseCode.invalidBatchStartDateError.getErrorCode()));
  }

  private Map<String, Object> getCertTemplate() throws Exception{
    String template = " {\n" +
            "    \"template_01_prad\": {\n" +
            "        \"identifier\": \"template_01_prad\",\n" +
            "        \"criteria\": \"{\\r\\n            \\\"enrollment\\\": {\\r\\n                \\\"status\\\": 2\\r\\n            }\\r\\n        }\",\n" +
            "        \"name\": \"Course completion certificate prad\",\n" +
            "        \"notifyTemplate\": \"{\\r\\n            \\\"emailTemplateType\\\": \\\"defaultCertTemp\\\",\\r\\n            \\\"subject\\\": \\\"Completion certificate\\\",\\r\\n            \\\"stateImgUrl\\\": \\\"https:\\/\\/sunbirddev.blob.core.windows.net\\/orgemailtemplate\\/img\\/File-0128212938260643843.png\\\",\\r\\n            \\\"regards\\\": \\\"Minister of Gujarat\\\",\\r\\n            \\\"regardsperson\\\": \\\"Chairperson\\\"\\r\\n        }\",\n" +
            "        \"issuer\": \"{\\r\\n            \\\"name\\\": \\\"Gujarat Council of Educational Research and Training\\\",\\r\\n            \\\"publicKey\\\": [\\r\\n                \\\"7\\\",\\r\\n                \\\"8\\\"\\r\\n            ],\\r\\n            \\\"url\\\": \\\"https:\\/\\/gcert.gujarat.gov.in\\/gcert\\/\\\"\\r\\n        }\",\n" +
            "        \"signatoryList\": \"[\\r\\n            {\\r\\n                \\\"image\\\": \\\"https:\\/\\/cdn.pixabay.com\\/photo\\/2014\\/11\\/09\\/08\\/06\\/signature-523237__340.jpg\\\",\\r\\n                \\\"name\\\": \\\"CEO Gujarat\\\",\\r\\n                \\\"id\\\": \\\"CEO\\\",\\r\\n                \\\"designation\\\": \\\"CEO\\\"\\r\\n            }\\r\\n        ]\"\n" +
            "    }\n" +
            "}";
    return JsonUtil.deserialize(template, Map.class);
  }

  private void mockCourseEnrollmentActor(){
    Map<String, Object> courseMap = new HashMap<String, Object>() {{
      put("content", new HashMap<String, Object>() {{
        put("contentType", "Course");
        put("status", "Live");
      }});
    }};
    when(ContentUtil.getContent(
            Mockito.anyString(), Mockito.anyList())).thenReturn(courseMap);
  }

  private void courseBatchUtilDateMethods() throws Exception {
    doCallRealMethod().when(CourseBatchUtil.class, "cassandraCourseMapping",Mockito.any(), Mockito.anyString());
    doCallRealMethod().when(CourseBatchUtil.class, "setEndOfDay", Mockito.any(), Mockito.any(), Mockito.any());
    doCallRealMethod().when(CourseBatchUtil.class, "esCourseMapping", Mockito.any(), Mockito.anyString());
  }
}
