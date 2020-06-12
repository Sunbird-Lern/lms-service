package org.sunbird.learner.actors.coursebatch;

import static org.powermock.api.mockito.PowerMockito.when;

import akka.dispatch.Futures;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.application.test.SunbirdApplicationActorTest;
import org.sunbird.builder.mocker.CassandraMocker;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.mocker.UserOrgMocker;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.builder.object.CustomObjectBuilder.CustomObjectWrapper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class CourseBatchUserManagementActorTest extends SunbirdApplicationActorTest {

  private MockerBuilder.MockersGroup group;

  public CourseBatchUserManagementActorTest() {
    init(CourseBatchManagementActor.class);
  }

  @Test
  @PrepareForTest({ServiceFactory.class})
  public void getBatchParticipantsSuccess() {
    group = MockerBuilder.getFreshMockerGroup().withCassandraMock(new CassandraMocker());
    when(group
            .getCassandraMockerService()
            .getRecords(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList()))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(5).asCassandraResponse());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, "randomBatchId");
    innerMap.put(JsonKey.ACTIVE, true);
    HashMap<String, Object> batchMap = new HashMap<>();
    batchMap.put(JsonKey.BATCH, innerMap);
    req.setOperation("getParticipants");
    req.setRequest(batchMap);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.get(JsonKey.BATCH));
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.BATCH);
    Assert.assertNotNull(result.get(JsonKey.COUNT));
    Assert.assertTrue((int) result.get(JsonKey.COUNT) > 0);
    Assert.assertNotNull(result.get(JsonKey.PARTICIPANTS));
    List<String> participants = (List<String>) result.get(JsonKey.PARTICIPANTS);
    Assert.assertEquals((int) result.get(JsonKey.COUNT), participants.size());
  }

  @Test
  @PrepareForTest({ServiceFactory.class})
  public void getBatchParticipantsEmptySuccess() {
    group = MockerBuilder.getFreshMockerGroup().withCassandraMock(new CassandraMocker());
    when(group
            .getCassandraMockerService()
            .getRecords(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList()))
        .thenReturn(CustomObjectBuilder.getUserCoursesBuilder().buildList().asCassandraResponse());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, "randomBatchId");
    innerMap.put(JsonKey.ACTIVE, true);
    HashMap<String, Object> batchMap = new HashMap<>();
    batchMap.put(JsonKey.BATCH, innerMap);
    req.setOperation("getParticipants");
    req.setRequest(batchMap);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.get(JsonKey.BATCH));
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.BATCH);
    Assert.assertNotNull(result.get(JsonKey.COUNT));
    Assert.assertEquals(0, (int) result.get(JsonKey.COUNT));
    Assert.assertNotNull(result.get(JsonKey.PARTICIPANTS));
    List<String> participants = (List<String>) result.get(JsonKey.PARTICIPANTS);
    Assert.assertTrue(participants.isEmpty());
  }

  @Test
  @PrepareForTest({ServiceFactory.class, EsClientFactory.class, UserOrgServiceImpl.class})
  public void addUserToBatchSuccess() {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withCassandraMock(new CassandraMocker())
            .withESMock(new ESMocker())
            .withUserOrgMock(new UserOrgMocker());
    Map<String, Object> caller = CustomObjectBuilder.getRandomUser().get();
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString())).thenReturn(caller);
    when(group.getUserOrgMockerService().getUsersByIds(Mockito.anyList()))
        .then(
            new Answer<List<Map<String, Object>>>() {

              @Override
              public List<Map<String, Object>> answer(InvocationOnMock invocation)
                  throws Throwable {
                List<String> ids = (List<String>) invocation.getArguments()[0];
                List<Map<String, Object>> users =
                    CustomObjectBuilder.getRandomUsersWithIds(
                            ids, (String) caller.get(JsonKey.ROOT_ORG_ID))
                        .get();
                return users;
              }
            });
    Future<Map<String, Object>> courseBatchES =
        CustomObjectBuilder.getCourseBatchBuilder()
            .generateRandomFields()
            .addField(JsonKey.ENROLLMENT_TYPE, JsonKey.INVITE_ONLY)
            .addField(JsonKey.CREATED_BY, "randomCreator")
            .build()
            .asESIdentifierResult();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatchES);
    CustomObjectWrapper<List<Map<String, Object>>> userCoursesWrapper =
        CustomObjectBuilder.getRandomUserCoursesList(5);
    List<String> availableUsers =
        userCoursesWrapper
            .get()
            .stream()
            .map(uc -> (String) uc.get(JsonKey.USER_ID))
            .collect(Collectors.toList());
    when(group
            .getCassandraMockerService()
            .getRecords(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList()))
        .thenReturn(userCoursesWrapper.asCassandraResponse());
    when(group
            .getESMockerService()
            .upsert(
                Mockito.eq(EsType.usercourses.getTypeName()),
                Mockito.anyString(),
                Mockito.anyMap()))
        .thenReturn(Futures.successful(true));
    Request req = new Request();
    List<String> userIds = Arrays.asList("addUserId1", "addUserId2");
    availableUsers.addAll(userIds);
    req.setOperation("addUserBatch");
    req.put(JsonKey.BATCH_ID, "randomBatchId");
    req.put(JsonKey.USER_IDs, availableUsers);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
  }

  @Test
  @PrepareForTest({ServiceFactory.class, EsClientFactory.class})
  public void removeUserToBatchSuccess() {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withCassandraMock(new CassandraMocker())
            .withESMock(new ESMocker());
    Map<String, Object> caller = CustomObjectBuilder.getRandomUser().get();
    Future<Map<String, Object>> courseBatchES =
        CustomObjectBuilder.getCourseBatchBuilder()
            .generateRandomFields()
            .addField(JsonKey.ENROLLMENT_TYPE, JsonKey.INVITE_ONLY)
            .addField(JsonKey.CREATED_BY, "randomCreator")
            .build()
            .asESIdentifierResult();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatchES);
    CustomObjectWrapper<List<Map<String, Object>>> userCoursesWrapper =
        CustomObjectBuilder.getRandomUserCoursesList(5);
    List<String> availableUsers =
        userCoursesWrapper
            .get()
            .stream()
            .map(uc -> (String) uc.get(JsonKey.USER_ID))
            .collect(Collectors.toList());
    when(group
            .getCassandraMockerService()
            .getRecords(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyList()))
        .thenReturn(userCoursesWrapper.asCassandraResponse());
    when(group
            .getESMockerService()
            .upsert(
                Mockito.eq(EsType.usercourses.getTypeName()),
                Mockito.anyString(),
                Mockito.anyMap()))
        .thenReturn(Futures.successful(true));
    when(group
            .getCassandraMockerService()
            .getRecordById(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .then(
            new Answer<Response>() {

              @Override
              public Response answer(InvocationOnMock invocation) throws Throwable {
                Map<String, Object> inputKeys = (Map<String, Object>) invocation.getArguments()[2];
                return CustomObjectBuilder.getUserCoursesBuilder()
                    .generateRandomFields()
                    .addField(JsonKey.BATCH_ID, (String) inputKeys.get(JsonKey.BATCH_ID))
                    .addField(JsonKey.USER_ID, (String) inputKeys.get(JsonKey.USER_ID))
                    .addField(JsonKey.ACTIVE, true)
                    .wrapToList()
                    .buildList()
                    .asCassandraResponse();
              }
            });
    Request req = new Request();
    List<String> userIds = Arrays.asList("addUserId1", "addUserId2");
    availableUsers.addAll(userIds);
    req.setOperation("removeUserFromBatch");
    req.put(JsonKey.BATCH_ID, "randomBatchId");
    req.put(JsonKey.USER_IDs, availableUsers);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
  }
}
