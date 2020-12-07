package org.sunbird.learner.actors;

import static akka.testkit.JavaTestKit.duration;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.util.KeycloakRequiredActionLinkUtil;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.UserCoursesDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  ElasticSearchRestHighImpl.class,
  UserCoursesDaoImpl.class,
  EsClientFactory.class, HttpUtil.class, KeycloakRequiredActionLinkUtil.class , ProjectUtil.class
})
@PowerMockIgnore({"javax.management.*"})
public class SearchHandlerActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(SearchHandlerActor.class);
  private static UserCoursesDao userCoursesDao;
  private static CassandraOperationImpl cassandraOperation;
  private static ElasticSearchService esService;
  private static TestActorRef<SearchHandlerActor> actorRef;

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
    actorRef = TestActorRef.create(system, props, "testSearchHandler");
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = mock(CassandraOperationImpl.class);
    mockStatic(UserCoursesDaoImpl.class);
    userCoursesDao = PowerMockito.mock(UserCoursesDaoImpl.class);
    when(UserCoursesDaoImpl.getInstance()).thenReturn(userCoursesDao);

  }

  @Before
  public void beforeTest() throws Exception {
    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(createResponseGet(true));
    when(esService.search(Mockito.any(), Mockito.any(SearchDTO.class), Mockito.anyVararg()))
        .thenReturn(promise.future());

    PowerMockito.mockStatic(ServiceFactory.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    when(cassandraOperation.getRecordsByProperties(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList(), Mockito.any()))
        .thenReturn(getRecordByPropertyResponse());
    mockStatic(ProjectUtil.class);
    when(ProjectUtil.getConfigValue("user_search_base_url")).thenReturn("http://test.com/api");
    when(ProjectUtil.getConfigValue("sunbird_user_search_cretordetails_fields")).thenReturn("id,firstName,lastName");
    when(ProjectUtil.getConfigValue("sunbird_api_request_lower_case_fields")).thenReturn("compositeSearch,testOperation");
    mockStatic(HttpUtil.class);
    String body = "{\"id\":\"api.user.search\",\"ver\":\"v1\",\"ts\":\"2020-04-15 14:59:51:094+0000\",\"params\":{\"resmsgid\":null,\"msgid\":null,\"err\":null,\"status\":\"success\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"response\":{\"count\":1,\"content\":[{\"lastName\":\"User\",\"firstName\":\"Reviewer\",\"id\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\"}]}}}";
    when(HttpUtil.doPostRequest(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap())).thenReturn(new HttpUtilResponse(body, 200));
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

  private static Map<String, Object> createResponseGet(boolean isResponseRequired) {
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
    Response res = probe.expectMsgClass(duration("200 second"), Response.class);
    assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void testInvalidOperation() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation("INVALID_OPERATION");

    subject.tell(reqObj, probe.getRef());
    ProjectCommonException exc = probe.expectMsgClass(ProjectCommonException.class);
    assertTrue(null != exc);
  }

  @Test
  public void testGetUserSearchRequest() throws Exception {
    Method method = SearchHandlerActor.class.getDeclaredMethod("getUserSearchRequest", List.class, List.class);
    method.setAccessible(true);
    List<String> creatorIds = Arrays.asList("test_id1", "test_id2");
    List<String> fields = Arrays.asList("firstName", "lastName", "id");
    String result = (String) method.invoke(actorRef.underlyingActor(), creatorIds, fields);
    assertTrue(StringUtils.isNotBlank(result));
    assertTrue(result.contains("test_id1"));
    assertTrue(result.contains("firstName"));
    assertTrue(result.contains("filters"));
    assertTrue(result.contains("fields"));
  }

  @Test
  public void testMakePostRequest() throws Exception {
    String req = "{\"request\":{\"filters\":{\"id\":[\"test_id1\",\"test_id2\"]},\"fields\":[\"firstName\",\"lastName\",\"id\"]}}";
    Method method = SearchHandlerActor.class.getDeclaredMethod("makePostRequest", String.class, String.class);
    method.setAccessible(true);
    List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(actorRef.underlyingActor(), "test_url", req);
    assertTrue(CollectionUtils.isNotEmpty(result));
  }

  @Test
  public void testPopulateCreatorDetails() throws Exception {
    Map<String, Object> input = new HashMap<String, Object>(){{
      put("count", 2);
      put("content", new ArrayList<Map<String, Object>>(){{
        add(new HashMap<String, Object>(){{
          put("identifier", "0129889020926115845");
          put("createdBy", "0ac58586-76c9-491f-8af4-f184b3c34a67");
        }});
        add(new HashMap<String, Object>(){{
          put("identifier", "0129889020926115952");
          put("createdBy", "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8");
        }});
      }});
    }};
    Method method = SearchHandlerActor.class.getDeclaredMethod("populateCreatorDetails", Map.class);
    method.setAccessible(true);
    method.invoke(actorRef.underlyingActor(), input);
    List<Map<String, Object>> content = (List<Map<String, Object>>) input.getOrDefault("content", new ArrayList<Map<String, Object>>());
    for(Map<String, Object> map : content){
      if(StringUtils.equalsIgnoreCase("95e4942d-cbe8-477d-aebd-ad8e6de4bfc8", (String) map.get("createdBy")))
        assertTrue(MapUtils.isNotEmpty((Map<String, Object>) map.get("creatorDetails")));
    }
  }
}
