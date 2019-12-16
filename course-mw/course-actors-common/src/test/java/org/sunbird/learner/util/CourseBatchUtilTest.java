package org.sunbird.learner.util;

import static org.powermock.api.mockito.PowerMockito.when;

import akka.dispatch.Futures;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.body.RequestBodyEntity;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.builder.object.CustomObjectBuilder.CustomObjectWrapper;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.responsecode.ResponseCode;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Unirest.class})
@PowerMockIgnore("javax.management.*")
public class CourseBatchUtilTest {

  private static MockerBuilder.MockersGroup group;

  @Before
  public void setup() {
    group = MockerBuilder.getFreshMockerGroup().andStaticMock(Unirest.class);
  }

  @Test
  public void validateTemplateSuccessTest() throws UnirestException {
    mockResponseForTemplate(
        200,
        "{\"result\":{\"certificate\":{\"template\":{\"identifier\":\"randomTemplateId\",\"name\":\"randomTemplate\"}}}}");
    Map<String, Object> certificate = CourseBatchUtil.validateTemplate("randomTemplateId");
    Assert.assertNotNull(certificate);
    Assert.assertTrue(certificate.containsKey("name"));
    Assert.assertEquals("randomTemplate", certificate.get("name"));
  }

  @Test
  public void validateTemplateFailureTest() throws UnirestException {
    mockResponseForTemplate(404, null);
    try {
      Map<String, Object> certificate = CourseBatchUtil.validateTemplate("randomTemplateId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.RESOURCE_NOT_FOUND.getErrorCode(), ex.getCode());
    }
  }

  @Test
  public void validateTemplateFailureBodyTest() throws UnirestException {
    mockResponseForTemplate(200, null);
    try {
      Map<String, Object> certificate = CourseBatchUtil.validateTemplate("randomTemplateId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.SERVER_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  public void validateTemplateFailureIdTest() throws UnirestException {
    mockResponseForTemplate(
        200,
        "{\"result\":{\"certificate\":{\"template\":{\"identifier\":\"randomTemplateId\",\"name\":\"randomTemplate\"}}}}");
    try {
      Map<String, Object> certificate = CourseBatchUtil.validateTemplate("templateId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  public void validateTemplateFailureTemplateTest() throws UnirestException {
    mockResponseForTemplate(
        200,
        "{\"result\":{\"certificate\":{\"identifier\":\"randomTemplateId\",\"name\":\"randomTemplate\"}}}");
    try {
      Map<String, Object> certificate = CourseBatchUtil.validateTemplate("randomTemplateId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  public void validateTemplateFailureCertificateTest() throws UnirestException {
    mockResponseForTemplate(
        200,
        "{\"result\":{\"template\":{\"identifier\":\"randomTemplateId\",\"name\":\"randomTemplate\"}}}");
    try {
      Map<String, Object> certificate = CourseBatchUtil.validateTemplate("randomTemplateId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, Unirest.class})
  public void validateCourseBatchSuccessTest() {
    group.withESMock(new ESMocker());
    CustomObjectWrapper<Map<String, Object>> courseBatchIn =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatchIn.asESIdentifierResult());
    Map<String, Object> courseBatchOut =
        CourseBatchUtil.validateCourseBatch(
            (String) courseBatchIn.get().get(JsonKey.COURSE_ID),
            (String) courseBatchIn.get().get(JsonKey.BATCH_ID));
    Assert.assertNotNull(courseBatchOut);
    Assert.assertEquals(courseBatchIn.get(), courseBatchOut);
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, Unirest.class})
  public void validateCourseBatchFailureTest() {
    group.withESMock(new ESMocker());
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getEmptyMap().asESIdentifierResult());
    try {
      Map<String, Object> courseBatchOut =
          CourseBatchUtil.validateCourseBatch("courseId", "batchId");
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, Unirest.class})
  public void validateCourseBatchCourseMismatchFailureTest() {
    group.withESMock(new ESMocker());
    CustomObjectWrapper<Map<String, Object>> courseBatchIn =
        CustomObjectBuilder.getRandomCourseBatch();
    when(group.getESMockerService().getDataByIdentifier(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(courseBatchIn.asESIdentifierResult());
    try {
      Map<String, Object> courseBatchOut =
          CourseBatchUtil.validateCourseBatch(
              "anotherCourseId", (String) courseBatchIn.get().get(JsonKey.BATCH_ID));
    } catch (ProjectCommonException ex) {
      Assert.assertNotNull(ex);
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getErrorCode(), ex.getCode());
    }
  }

  @Test
  @PrepareForTest({EsClientFactory.class, ElasticSearchHelper.class, Unirest.class})
  public void syncCourseBatchForegroundSuccessTest() {
    group.withESMock(new ESMocker());
    when(group
            .getESMockerService()
            .save(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(Futures.successful("randomBatchId"));
    CourseBatchUtil.syncCourseBatchForeground(
        "randomBatchId", CustomObjectBuilder.getRandomCourseBatch().get());
    PowerMockito.verifyStatic();
    ElasticSearchHelper.getResponseFromFuture(Mockito.any());
  }

  private void mockResponseForTemplate(int status, String body) throws UnirestException {
    GetRequest http = Mockito.mock(GetRequest.class);
    RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(Unirest.get(Mockito.anyString())).thenReturn(http);
    when(http.headers(Mockito.anyMap())).thenReturn(http);
    when(http.asString()).thenReturn(response);
    when(response.getStatus()).thenReturn(status);
    when(response.getBody()).thenReturn(body);
  }
}
