package org.sunbird.learner.actors.certificate;

import static org.powermock.api.mockito.PowerMockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.*;
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
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import org.sunbird.kafka.client.InstructionEventGenerator;

@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class})
@SuppressStaticInitializationFor("org.sunbird.kafka.client.KafkaClient")
@PowerMockIgnore("javax.management.*")
public class CertificateActorTest extends SunbirdApplicationActorTest {

  private static MockerBuilder.MockersGroup group;

  private static final String batchId = "randomBatchId";
  private static final String courseId = "randomCourseId";
  private static final String certificateName = "certificateName";

  @Before
  public void beforeEach() {
    group = MockerBuilder.getFreshMockerGroup().withESMock(new ESMocker());
  }

  public CertificateActorTest() {
    init(CertificateActor.class);
  }

  @Test
  @PrepareForTest({
    EsClientFactory.class,
    ElasticSearchHelper.class,
    InstructionEventGenerator.class
  })
  public void issueCertificateTest() throws Exception {
    group.andStaticMock(InstructionEventGenerator.class);
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(1).asESSearchResult());
    PowerMockito.doNothing()
        .when(
            InstructionEventGenerator.class,
            "pushInstructionEvent",
            Mockito.anyString(),
            Mockito.anyMap());

    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
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
  public void reIssueCertificateTest() throws Exception {
    group.andStaticMock(InstructionEventGenerator.class);
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(1).asESSearchResult());
    PowerMockito.doNothing()
        .when(
            InstructionEventGenerator.class,
            "pushInstructionEvent",
            Mockito.anyString(),
            Mockito.anyMap());

    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    innerMap.put("certificate", certificateName);
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
  public void issueCertificateEmptyTest() {
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getEmptyContentList().asESSearchResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, courseBatch.get().get(JsonKey.COURSE_ID));
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    Response response = executeInTenSeconds(req, Response.class);
    Assert.assertNotNull(response);
    Map<String, Object> result = (Map<String, Object>) response.get(JsonKey.RESULT);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.STATUS));
  }

  @Test
  public void issueCertificateCourseMismatchTest() {
    CustomObjectWrapper<Map<String, Object>> courseBatch =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(courseBatch.asESIdentifierResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, courseBatch.get().get(JsonKey.BATCH_ID));
    innerMap.put(JsonKey.COURSE_ID, "wrongCourseId");
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    ProjectCommonException ex = executeInTenSeconds(req, ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }

  @Test
  public void issueCertificateMissingBatchTest() {
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getEmptyMap().asESIdentifierResult());
    Request req = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.BATCH_ID, batchId);
    innerMap.put(JsonKey.COURSE_ID, courseId);
    innerMap.put("certificate", certificateName);
    req.setOperation("issueCertificate");
    req.setRequest(innerMap);
    ProjectCommonException ex = executeInTenSeconds(req, ProjectCommonException.class);
    Assert.assertNotNull(ex);
  }
}
