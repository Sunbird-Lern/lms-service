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
import org.sunbird.common.request.RequestContext;
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
    Method method = SearchHandlerActor.class.getDeclaredMethod("makePostRequest", RequestContext.class, String.class, String.class);
    method.setAccessible(true);
    List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(actorRef.underlyingActor(), null, "test_url", req);
    assertTrue(CollectionUtils.isNotEmpty(result));
  }

  @Test
  public void testPopulateCreatorDetails() throws Exception {
    Map<String, Object> input = new HashMap<String, Object>(){{
      put("count", 2);
      put("content", new ArrayList<Map<String, Object>>(){{
        add(new HashMap<String, Object>(){{
          put("identifier", "0129889020926115845");
          put("createdBy", "8454cb21-3ce9-4e30-85b5-fade097880d8");
        }});
        add(new HashMap<String, Object>(){{
          put("identifier", "0129889020926115952");
          put("createdBy", "95e4942d-cbe8-477d-aebd-ad8e6de4bfc8");
        }});
      }});
    }};
    mockStatic(HttpUtil.class);
    String response1 = "{\"id\":\"api.user.read.8454cb21-3ce9-4e30-85b5-fade097880d8\",\"ver\":\"v1\",\"ts\":\"2021-02-25 16:53:33:703+0000\",\"params\":{\"resmsgid\":null,\"msgid\":\"136672a9ff083d1c821a64b082fc4aa2\",\"err\":null,\"status\":\"success\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"response\":{\"webPages\":[],\"maskedPhone\":\"******7418\",\"tcStatus\":null,\"subject\":[],\"channel\":null,\"language\":[],\"updatedDate\":\"2020-10-15 12:45:00:730+0000\",\"password\":\"5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8\",\"managedBy\":null,\"flagsValue\":6,\"id\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"recoveryEmail\":\"te*****@gmail.com\",\"identifier\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"thumbnail\":null,\"updatedBy\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"accesscode\":null,\"locationIds\":[\"af8e8c0a-f699-4185-81aa-2596332d0a1b\",\"3d69b821-88da-4838-8897-79ca749a8e5e\"],\"externalIds\":[],\"registryId\":null,\"rootOrgId\":\"ORG_001\",\"prevUsedEmail\":\"\",\"firstName\":\"Mentor First\",\"tncAcceptedOn\":1579606318379,\"allTncAccepted\":{\"groupsTnc\":{\"tncAcceptedOn\":\"2020-10-19 09:28:36:077+0000\",\"version\":\"3.4.0\"}},\"phone\":\"******7418\",\"dob\":null,\"grade\":[],\"currentLoginTime\":null,\"userType\":null,\"status\":1,\"lastName\":\"User\",\"tncLatestVersion\":\"v1\",\"gender\":null,\"roles\":[\"public\"],\"prevUsedPhone\":\"\",\"stateValidated\":true,\"isDeleted\":null,\"organisations\":[{\"organisationId\":\"ORG_001\",\"updatedBy\":null,\"addedByName\":null,\"addedBy\":\"781c21fc-5054-4ee0-9a02-fbb1006a4fdd\",\"roles\":[\"CONTENT_REVIEWER\",\"BOOK_REVIEWER\",\"ORG_ADMIN\",\"FLAG_REVIEWER\",\"CONTENT_CREATOR\",\"BOOK_CREATOR\",\"PUBLIC\"],\"approvedBy\":null,\"updatedDate\":\"2020-09-23 05:35:59:279+0000\",\"userId\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"approvaldate\":null,\"isDeleted\":false,\"hashTagId\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"isRejected\":false,\"id\":\"012718490252500992318\",\"position\":null,\"isApproved\":false,\"orgjoindate\":\"2019-03-14 07:45:04:542+0000\",\"orgLeftDate\":null}],\"provider\":null,\"countryCode\":null,\"tncLatestVersionUrl\":\"https:\\/\\/dev-sunbird-temp.azureedge.net\\/portal\\/terms-and-conditions-v1.html\",\"maskedEmail\":\"te****@yopmail.com\",\"tempPassword\":null,\"email\":\"te****@yopmail.com\",\"rootOrg\":{\"dateTime\":null,\"preferredLanguage\":\"English\",\"keys\":{\"encKeys\":[\"5766\",\"5767\"],\"signKeys\":[\"5766\",\"5767\"]},\"approvedBy\":null,\"channel\":\"ROOT_ORG\",\"description\":\"Andhra State Boardsssssss\",\"updatedDate\":\"2018-11-28 10:00:08:675+0000\",\"addressId\":null,\"orgType\":null,\"provider\":null,\"locationId\":null,\"orgCode\":\"sunbird\",\"theme\":null,\"id\":\"ORG_001\",\"communityId\":null,\"isApproved\":null,\"email\":\"support_dev@sunbird.org\",\"slug\":\"sunbird\",\"isSSOEnabled\":null,\"thumbnail\":null,\"orgName\":\"Sunbird\",\"updatedBy\":\"1d7b85b0-3502-4536-a846-d3a51fd0aeea\",\"locationIds\":[\"969dd3c1-4e98-4c17-a994-559f2dc70e18\"],\"externalId\":null,\"isRootOrg\":true,\"rootOrgId\":\"ORG_001\",\"approvedDate\":null,\"imgUrl\":null,\"homeUrl\":null,\"orgTypeId\":null,\"isDefault\":true,\"createdDate\":null,\"createdBy\":null,\"parentOrgId\":null,\"hashTagId\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"noOfMembers\":64,\"status\":1},\"phoneVerified\":false,\"profileSummary\":null,\"tcUpdatedDate\":null,\"recoveryPhone\":\"\",\"avatar\":null,\"userName\":\"ntptest104\",\"userId\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"userSubType\":null,\"promptTnC\":false,\"emailVerified\":true,\"lastLoginTime\":null,\"createdDate\":\"2017-10-31 10:47:05:401+0000\",\"framework\":{\"board\":[\"CBSE\"],\"gradeLevel\":[\"Grade 1\"],\"id\":[\"NCFCOPY\"],\"medium\":[\"English\"],\"subject\":[\"English\"]},\"createdBy\":\"5d7eb482-c2b8-4432-bf38-cc58f3c23b45\",\"location\":null,\"tncAcceptedVersion\":\"v1\"}}}";
    String response2 = "{\"id\":\"api.user.read.95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"ver\":\"v1\",\"ts\":\"2021-02-25 16:54:17:704+0000\",\"params\":{\"resmsgid\":null,\"msgid\":\"a42fedc5652843368d636b403283e0ec\",\"err\":null,\"status\":\"success\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"response\":{\"webPages\":[{\"type\":\"fb\",\"url\":\"https:\\/\\/www.facebook.com\\/teachers\"},{\"type\":\"twitter\",\"url\":\"https:\\/\\/twitter.com\\/sg\"}],\"maskedPhone\":\"******7418\",\"tcStatus\":null,\"subject\":[\"Bengali\"],\"channel\":\"013016492159606784174\",\"language\":[\"Gujarati\",\"Hindi\"],\"updatedDate\":\"2020-10-21 19:16:09:342+0000\",\"password\":\"5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8\",\"managedBy\":null,\"flagsValue\":6,\"id\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"recoveryEmail\":\"lm****************@yopmail.com\",\"identifier\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"thumbnail\":null,\"updatedBy\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"accesscode\":null,\"locationIds\":[],\"registryId\":null,\"rootOrgId\":\"ORG_001\",\"prevUsedEmail\":\"\",\"firstName\":\"Reviewer\",\"tncAcceptedOn\":1578647946965,\"allTncAccepted\":{\"orgAdminTnc\":{\"tncAcceptedOn\":\"2020-12-11 09:34:03:037+0000\",\"version\":\"3.5.0\"},\"groupsTnc\":{\"tncAcceptedOn\":\"2020-10-22 12:30:36:504+0000\",\"version\":\"3.4.0\"}},\"phone\":\"******7418\",\"dob\":\"2018-04-23\",\"grade\":[\"Grade 5\",\"Grade 4\"],\"currentLoginTime\":null,\"userType\":null,\"status\":1,\"lastName\":\"User\",\"tncLatestVersion\":\"v1\",\"gender\":\"Male\",\"roles\":[\"public\"],\"prevUsedPhone\":\"\",\"stateValidated\":true,\"isDeleted\":null,\"organisations\":[{\"organisationId\":\"ORG_001\",\"updatedBy\":\"781c21fc-5054-4ee0-9a02-fbb1006a4fdd\",\"addedByName\":null,\"addedBy\":\"781c21fc-5054-4ee0-9a02-fbb1006a4fdd\",\"roles\":[\"COURSE_MENTOR\",\"CONTENT_REVIEWER\",\"ADMIN\",\"TEACHER_BADGE_ISSUER\",\"REPORT_VIEWER\",\"ORG_ADMIN\",\"BOOK_CREATOR\",\"BOOK_REVIEWER\",\"OFFICIAL_TEXTBOOK_BADGE_ISSUER\",\"COURSE_CREATOR\",\"COURSE_ADMIN\",\"REPORT_VIEWER\",\"ORG_MODERATOR\",\"PUBLIC\",\"ANNOUNCEMENT_SENDER\",\"CONTENT_CREATOR\",\"FLAG_REVIEWER\"],\"approvedBy\":null,\"updatedDate\":\"2020-10-05 10:46:39:565+0000\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"approvaldate\":null,\"isDeleted\":false,\"hashTagId\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"isRejected\":false,\"id\":\"012718445039091712313\",\"position\":null,\"isApproved\":false,\"orgjoindate\":\"2019-03-14 06:14:56:439+0000\",\"orgLeftDate\":null}],\"provider\":null,\"countryCode\":null,\"tncLatestVersionUrl\":\"https:\\/\\/dev-sunbird-temp.azureedge.net\\/portal\\/terms-and-conditions-v1.html\",\"maskedEmail\":\"us****@yopmail.com\",\"tempPassword\":null,\"email\":\"us****@yopmail.com\",\"rootOrg\":{\"dateTime\":null,\"preferredLanguage\":\"English\",\"keys\":{\"encKeys\":[\"5766\",\"5767\"],\"signKeys\":[\"5766\",\"5767\"]},\"approvedBy\":null,\"channel\":\"ROOT_ORG\",\"description\":\"Andhra State Boardsssssss\",\"updatedDate\":\"2018-11-28 10:00:08:675+0000\",\"addressId\":null,\"orgType\":null,\"provider\":null,\"locationId\":null,\"orgCode\":\"sunbird\",\"theme\":null,\"id\":\"ORG_001\",\"communityId\":null,\"isApproved\":null,\"email\":\"support_dev@sunbird.org\",\"slug\":\"sunbird\",\"isSSOEnabled\":null,\"thumbnail\":null,\"orgName\":\"Sunbird\",\"updatedBy\":\"1d7b85b0-3502-4536-a846-d3a51fd0aeea\",\"locationIds\":[\"969dd3c1-4e98-4c17-a994-559f2dc70e18\"],\"externalId\":null,\"isRootOrg\":true,\"rootOrgId\":\"ORG_001\",\"approvedDate\":null,\"imgUrl\":null,\"homeUrl\":null,\"orgTypeId\":null,\"isDefault\":true,\"createdDate\":null,\"createdBy\":null,\"parentOrgId\":null,\"hashTagId\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"noOfMembers\":64,\"status\":1},\"phoneVerified\":false,\"profileSummary\":\"\",\"tcUpdatedDate\":null,\"recoveryPhone\":\"\",\"avatar\":\"https:\\/\\/sunbirddev.blob.core.windows.net\\/user\\/95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\\/File-0124905755632271362.jpeg\",\"userName\":\"ntptest103\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"userSubType\":null,\"promptTnC\":false,\"emailVerified\":true,\"lastLoginTime\":null,\"createdDate\":\"2017-10-31 10:47:05:083+0000\",\"framework\":{\"board\":[\"NCERT\"],\"gradeLevel\":[\"Kindergarten\"],\"id\":[\"NCFCOPY\"],\"medium\":[\"English\"],\"subject\":[\"Mathematics\"]},\"createdBy\":\"5d7eb482-c2b8-4432-bf38-cc58f3c23b45\",\"location\":\"SdEVfkEzML47CKGeIp6gq2k8u4rJrIbnVHkgHv2cIAeknZLGIJOQUw\\/32a4u0\\/5HBnQ5EjrM1Rs7\\n508rbIHvjbSc2KcIt7kVkP2JR3JV7TeEYXVV\\/JylNVnggsVUUSNhT6a+wzaAmCWueMEdPmZuRg==\",\"tncAcceptedVersion\":\"v1\"}}}";
    when(HttpUtil.sendGetRequest(Mockito.anyString(), Mockito.anyMap())).thenReturn(response1, response2);
    Method method = SearchHandlerActor.class.getDeclaredMethod("populateCreatorDetails", RequestContext.class, Map.class);
    method.setAccessible(true);
    method.invoke(actorRef.underlyingActor(), null, input);
    List<Map<String, Object>> content = (List<Map<String, Object>>) input.getOrDefault("content", new ArrayList<Map<String, Object>>());
    for(Map<String, Object> map : content){
      if(StringUtils.equalsIgnoreCase("95e4942d-cbe8-477d-aebd-ad8e6de4bfc8", (String) map.get("createdBy")))
        assertTrue(MapUtils.isNotEmpty((Map<String, Object>) map.get("creatorDetails")));
    }
  }
}
