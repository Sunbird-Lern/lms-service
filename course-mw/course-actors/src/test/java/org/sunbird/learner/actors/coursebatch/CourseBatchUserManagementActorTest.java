package org.sunbird.learner.actors.coursebatch;

import static org.powermock.api.mockito.PowerMockito.when;

import org.apache.pekko.dispatch.Futures;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Ignore;
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
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
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
            .getRecordsByIndexedProperty(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
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
            .getRecordsByIndexedProperty(
                    Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
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
}
