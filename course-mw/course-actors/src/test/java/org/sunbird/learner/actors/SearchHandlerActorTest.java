package org.sunbird.learner.actors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.dispatch.Futures;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.response.Response;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import scala.concurrent.Promise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  ElasticSearchRestHighImpl.class,
  UserCoursesDaoImpl.class,
  EsClientFactory.class
})
@PowerMockIgnore({"javax.management.*"})
public class SearchHandlerActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(SearchHandlerActor.class);
  private static UserCoursesDao userCoursesDao;
  private static CassandraOperationImpl cassandraOperation;
  private static ElasticSearchService esService;

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = mock(CassandraOperationImpl.class);
    mockStatic(UserCoursesDaoImpl.class);
    userCoursesDao = PowerMockito.mock(UserCoursesDaoImpl.class);
    when(UserCoursesDaoImpl.getInstance()).thenReturn(userCoursesDao);
  }

  @Before
  public void beforeTest() {
    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance()).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(createResponseGet());
    when(esService.search(Mockito.any(SearchDTO.class), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(ServiceFactory.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    when(cassandraOperation.getRecordsByProperties(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList(), Mockito.any()))
        .thenReturn(getRecordByPropertyResponse());
  }

  private static Response getRecordByPropertyResponse() {
    Response response = new Response();
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> courseMap = new HashMap<>();
    courseMap.put(JsonKey.ACTIVE, true);
    courseMap.put(JsonKey.USER_ID, "anyUserId");
    list.add(courseMap);
    response.put(JsonKey.RESPONSE, list);
    return response;
  }

  private static Map<String, Object> createResponseGet() {
    HashMap<String, Object> response = new HashMap<>();
    List<Map<String, Object>> content = new ArrayList<>();
    HashMap<String, Object> innerMap = new HashMap<>();
    List<Map<String, Object>> batchList = new ArrayList<>();
    innerMap.put(JsonKey.BATCHES, batchList);
    content.add(innerMap);
    response.put(JsonKey.CONTENT, content);
    return response;
  }

  @Test
  @Ignore
  public void searchCourse() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.COMPOSITE_SEARCH.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.QUERY, "");
    Map<String, Object> filters = new HashMap<>();
    List<String> objectType = new ArrayList<String>();
    objectType.add("course-batch");
    filters.put(JsonKey.OBJECT_TYPE, objectType);
    innerMap.put(JsonKey.FILTERS, filters);
    innerMap.put(JsonKey.LIMIT, 1);
    reqObj.setRequest(innerMap);

    Map<String, Object> contextMap = new HashMap<>();
    contextMap.put(JsonKey.PARTICIPANTS, JsonKey.PARTICIPANTS);
    reqObj.setContext(contextMap);

    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(java.time.Duration.ofSeconds(200), Response.class);
    Assert.assertNotNull(res.get(JsonKey.RESPONSE));
  }

  @Test
  @Ignore
  public void testInvalidOperation() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation("INVALID_OPERATION");

    subject.tell(reqObj, probe.getRef());
    ProjectCommonException exc = probe.expectMsgClass(ProjectCommonException.class);
      Assert.assertNotNull(exc);
  }
}
