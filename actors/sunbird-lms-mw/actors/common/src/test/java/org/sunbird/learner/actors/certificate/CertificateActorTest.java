package org.sunbird.learner.actors.certificate;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.javadsl.TestKit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.learner.actors.certificate.service.CertificateActor;
import scala.concurrent.Future;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, InstructionEventGenerator.class})
@PowerMockIgnore("javax.management.*")
@Ignore
public class CertificateActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(CertificateActor.class);
  private static ElasticSearchService esService;
  private static final String batchId = "randomBatchId";
  private static final String courseId = "randomCourseId";
  private static final String certificateName = "certificateName";

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
  }

  @Before
  public void beforeEach() throws Exception {
    PowerMockito.mockStatic(EsClientFactory.class);
    PowerMockito.mockStatic(ElasticSearchHelper.class);
    PowerMockito.mockStatic(InstructionEventGenerator.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    //    PowerMockito.doNothing()
    //    .when(InstructionEventGenerator.class);
    PowerMockito.doNothing()
        .when(
            InstructionEventGenerator.class,
            "pushInstructionEvent",
            Mockito.anyString(),
            Mockito.anyMap());
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

  private void mockCourseBatchEsResponse(String courseId, String batchId, boolean isEmpty) {
    Map<String, Object> courseBatch = new HashMap<>();
    courseBatch.put(JsonKey.BATCH_ID, batchId);
    courseBatch.put(JsonKey.COURSE_ID, courseId);
    Promise<Map<String, Object>> promiseCourseBatch = Futures.promise();
    promiseCourseBatch.success(courseBatch);
    Future<Map<String, Object>> cbf = promiseCourseBatch.future();
    when(esService.getDataByIdentifier(Mockito.anyString(), Mockito.anyString())).thenReturn(cbf);
    when(ElasticSearchHelper.getResponseFromFuture(cbf)).thenReturn(isEmpty ? null : courseBatch);
  }

  private Map<String, Object> getMapforUserCourses(String batchId, String userId) {
    Map<String, Object> result = new HashMap<>();
    result.put(JsonKey.BATCH_ID, batchId);
    result.put(JsonKey.USER_ID, userId);
    return result;
  }

  @Test
  public void issueCertificateTest() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    mockCourseBatchEsResponse(courseId, batchId, false);
    mockUserCoursesEsResponse(false);
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    subject.tell(req, probe.getRef());
    Response response = probe.expectMsgClass(Duration.ofSeconds(10), Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  public void reIssueCertificateTest() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    mockCourseBatchEsResponse(courseId, batchId, false);
    mockUserCoursesEsResponse(false);
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    req.getContext().put("reIssue", "true");
    subject.tell(req, probe.getRef());
    Response response = probe.expectMsgClass(Duration.ofSeconds(10), Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  public void issueCertificateEmptyTest() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    mockCourseBatchEsResponse(courseId, batchId, false);
    mockUserCoursesEsResponse(true);
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    subject.tell(req, probe.getRef());
    Response response = probe.expectMsgClass(Duration.ofSeconds(10), Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  public void issueCertificateCourseMismatchTest() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    mockCourseBatchEsResponse(courseId, batchId, false);
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, "wrongCourseId");
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    subject.tell(req, probe.getRef());
    ProjectCommonException ex =
        probe.expectMsgClass(Duration.ofSeconds(10), ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }

  @Test
  public void issueCertificateMissingBatchTest() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    mockCourseBatchEsResponse(courseId, batchId, true);
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    subject.tell(req, probe.getRef());
    ProjectCommonException ex =
        probe.expectMsgClass(Duration.ofSeconds(10), ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }
}
