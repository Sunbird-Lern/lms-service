package org.sunbird.learner.actors.certificate;

import static org.powermock.api.mockito.PowerMockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.application.test.SunbirdApplicationActorTest;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.builder.object.CustomObjectBuilder.CustomObjectWrapper;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.kafka.client.KafkaClient;
import org.sunbird.learner.actors.certificate.service.CertificateActor;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@SuppressStaticInitializationFor("org.sunbird.kafka.client.KafkaClient")
public class CertificateActorTest extends SunbirdApplicationActorTest {

  private MockerBuilder.MockersGroup group;

  @Before
  public void setUp() {
    init(CertificateActor.class);
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, KafkaClient.class})
  public void issueCertificateTest() throws Exception {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withESMock(new ESMocker())
            .andStaticMock(KafkaClient.class);
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getCourseBatchBuilder()
            .generateRandomFields()
            .addField("cert_templates", new HashMap<>())
            .build();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    PowerMockito.doNothing().when(KafkaClient.class, "send", Mockito.any(), Mockito.anyString());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, KafkaClient.class})
  public void reIssueCertificateTest() throws Exception {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withESMock(new ESMocker())
            .andStaticMock(KafkaClient.class);
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getCourseBatchBuilder()
            .generateRandomFields()
            .addField("cert_templates", new HashMap<>())
            .build();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    PowerMockito.doNothing().when(KafkaClient.class, "send", Mockito.any(), Mockito.anyString());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    req.getContext().put("reIssue", "true");
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  @PrepareForTest({
    EsClientFactory.class,
    ElasticSearchHelper.class,
    InstructionEventGenerator.class
  })
  public void issueCertificateCertEmptyTest() {
    group = MockerBuilder.getFreshMockerGroup().withESMock(new ESMocker());
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getCourseBatchBuilder().generateRandomFields().build();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    ProjectCommonException ex = executeInTenSeconds(req, ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }

  @Test
  @PrepareForTest({
    EsClientFactory.class,
    ElasticSearchHelper.class,
    InstructionEventGenerator.class
  })
  public void issueCertificateCourseMismatchTest() {
    group = MockerBuilder.getFreshMockerGroup().withESMock(new ESMocker());
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getCourseBatchBuilder().generateRandomFields().build();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, "otherCourseId");
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    ProjectCommonException ex = executeInTenSeconds(req, ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }

  @Test
  @PrepareForTest({
    EsClientFactory.class,
    ElasticSearchHelper.class,
    InstructionEventGenerator.class
  })
  public void issueCertificateMissingBatchTest() {
    group = MockerBuilder.getFreshMockerGroup().withESMock(new ESMocker());
    CustomObjectWrapper<Map<String, Object>> courseBatch = CustomObjectBuilder.getEmptyMap();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, "randomBatchId");
    innerMap.put(JsonKey.COURSE_ID, "randomCourseId");
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    ProjectCommonException ex = executeInTenSeconds(req, ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }
}
