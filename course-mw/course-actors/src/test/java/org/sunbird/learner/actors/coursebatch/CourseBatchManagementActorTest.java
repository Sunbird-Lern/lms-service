package org.sunbird.learner.actors.coursebatch;

import static org.powermock.api.mockito.PowerMockito.when;

import org.apache.pekko.dispatch.Futures;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.application.test.SunbirdApplicationActorTest;
import org.sunbird.builder.mocker.CassandraMocker;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.mocker.UserOrgMocker;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.kafka.client.KafkaClient;
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.userorg.UserOrgServiceImpl;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("org.sunbird.kafka.client.KafkaClient")
@PrepareForTest({ServiceFactory.class, InstructionEventGenerator.class, KafkaClient.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class CourseBatchManagementActorTest extends SunbirdApplicationActorTest {

  private MockerBuilder.MockersGroup group;

  public CourseBatchManagementActorTest() {
    init(CourseBatchManagementActor.class);
  }

  @Test
  @PrepareForTest({
    ServiceFactory.class,
    EsClientFactory.class,
    UserOrgServiceImpl.class,
    ContentUtil.class,  InstructionEventGenerator.class, KafkaClient.class
  })
  public void createBatchInviteSuccess() throws Exception {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withCassandraMock(new CassandraMocker())
            .withESMock(new ESMocker())
            .withUserOrgMock(new UserOrgMocker())
            .andStaticMock(ContentUtil.class);
    Map<String, Object> courseBatch =
        CustomObjectBuilder.getCourseBatchBuilder()
            .generateRandomFields()
            .build()
            .get();
    when(group
            .getESMockerService()
            .save(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(Futures.successful("randomESID"));
    when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(CustomObjectBuilder.getRandomCourse().get());
    when(group
            .getCassandraMockerService()
            .insertRecord(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(
            new CustomObjectBuilder.CustomObjectWrapper<Boolean>(true).asCassandraResponse());
    when(group.getUserOrgMockerService().getOrganisationById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomOrg().get());
    PowerMockito.mockStatic(InstructionEventGenerator.class);
    PowerMockito.mockStatic(KafkaClient.class);
    PowerMockito.doNothing()
            .when(InstructionEventGenerator.class,
                    "pushInstructionEvent",
                    Mockito.anyString(),
                    Mockito.anyMap());

    PowerMockito.doNothing()
            .when(KafkaClient.class, "send", Mockito.anyString(), Mockito.anyString());
    String orgId = ((List<String>) courseBatch.get(JsonKey.COURSE_CREATED_FOR)).get(0);
    when(group.getUserOrgMockerService().getUsersByIds(Mockito.anyList(), Mockito.anyString()))
        .then(
            new Answer<List<Map<String, Object>>>() {
              @Override
              public List<Map<String, Object>> answer(InvocationOnMock invocation)
                  throws Throwable {
                List<String> userList = (List<String>) invocation.getArguments()[0];
                return CustomObjectBuilder.getRandomUsersWithIds(userList, orgId).get();
              }
            });
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString(), Mockito.anyString()))
        .then(
            new Answer<Map<String, Object>>() {

              @Override
              public Map<String, Object> answer(InvocationOnMock invocation) throws Throwable {
                String userId = (String) invocation.getArguments()[0];
                return CustomObjectBuilder.getRandomUsersWithIds(Arrays.asList(userId), orgId)
                    .get()
                    .get(0);
              }
            });
    PowerMockito.mockStatic(ContentUtil.class);
    mockCourseEnrollmentActor();

    Request req = new Request();
    req.setOperation("createBatch");
    req.setRequest(courseBatch);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.get(JsonKey.BATCH_ID));
  }

  @Test
  @PrepareForTest({EsClientFactory.class})
  public void getBatchSuccess() {
    group = MockerBuilder.getFreshMockerGroup().withESMock(new ESMocker());
    when(group.getESMockerService().getDataByIdentifier(Mockito.any(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomCourseBatch().asESIdentifierResult());
    Request req = new Request();
    req.setOperation("getBatch");
    req.getContext().put(JsonKey.BATCH_ID, "randomBatchId");
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
  }

  private void mockCourseEnrollmentActor(){
    Map<String, Object> courseMap = new HashMap<String, Object>() {{
      put("content", new HashMap<String, Object>() {{
        put("contentType", "Course");
        put("status", "Live");
        put("leafNodesCount", 1);
      }});
    }};
    when(ContentUtil.getContent(
            Mockito.anyString(), Mockito.anyList())).thenReturn(courseMap);
  }
}
