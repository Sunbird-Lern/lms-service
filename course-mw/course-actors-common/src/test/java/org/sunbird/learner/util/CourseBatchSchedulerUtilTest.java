package org.sunbird.learner.util;

import org.apache.pekko.dispatch.Futures;
import java.io.IOException;
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
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.builder.mocker.CassandraMocker;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.helper.ServiceFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  EsClientFactory.class,
  ElasticSearchHelper.class,
  HttpUtil.class,
  ContentUtil.class
})
@PowerMockIgnore("javax.management.*")
public class CourseBatchSchedulerUtilTest {

  private static MockerBuilder.MockersGroup group;

  @Before
  public void setup() {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withESMock(new ESMocker())
            .withCassandraMock(new CassandraMocker())
            .andStaticMock(HttpUtil.class)
            .andStaticMock(ContentUtil.class);
  }

  @Test
  public void getCourseObjectSuccessTest() {
    PowerMockito.when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(CustomObjectBuilder.getRandomCourse().get());
    Map<String, Object> course =
        CourseBatchSchedulerUtil.getCourseObject(null, "randomCourseId", new HashMap<>());
    Assert.assertNotNull(course);
  }

  @Test
  public void getCourseObjectFailureTest() {
    PowerMockito.when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(null);
    Map<String, Object> course =
        CourseBatchSchedulerUtil.getCourseObject(null, "randomCourseId", new HashMap<>());
    Assert.assertNull(course);
  }

  @Test
  public void updateCourseContentSuccessTest() throws IOException {
    PowerMockito.when(
            HttpUtil.sendPatchRequest(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(JsonKey.SUCCESS);
    boolean success =
        CourseBatchSchedulerUtil.updateCourseContent(
                null, "randomCourseId", "c_test_open_batch_count", 1);
    Assert.assertTrue(success);
  }

  @Test
  public void updateCourseContentFailureTest() throws IOException {
    PowerMockito.when(
            HttpUtil.sendPatchRequest(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(JsonKey.FAILURE);
    boolean success =
        CourseBatchSchedulerUtil.updateCourseContent(
                null, "randomCourseId", "c_test_open_batch_count", 1);
    Assert.assertFalse(success);
  }

  @Test
  public void doOperationInContentCourseSuccessTest() throws IOException {
    PowerMockito.when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(CustomObjectBuilder.getRandomCourse().get());
    PowerMockito.when(
            HttpUtil.sendPatchRequest(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(JsonKey.SUCCESS);
    boolean success =
        CourseBatchSchedulerUtil.doOperationInContentCourse(
                null, "randomCourseId", true, ProjectUtil.EnrolmentType.open.getVal());
    Assert.assertTrue(success);
  }

  @Test
  public void doOperationInContentCourseFailureTest() throws IOException {
    PowerMockito.when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(null);
    boolean success =
        CourseBatchSchedulerUtil.doOperationInContentCourse(
                null, "randomCourseId", true, ProjectUtil.EnrolmentType.open.getVal());
    Assert.assertFalse(success);
  }

  @Test
  public void updateCourseBatchDbStatusSuccessTest() throws IOException {
    Map<String, Object> courseBatch = CustomObjectBuilder.getRandomCourseBatch().get();
    PowerMockito.when(ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(CustomObjectBuilder.getRandomCourse().get());
    PowerMockito.when(
            HttpUtil.sendPatchRequest(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(JsonKey.SUCCESS);
    PowerMockito.when(
            group
                .getESMockerService()
                .update(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(Futures.successful(true));
    PowerMockito.when(
            group
                .getCassandraMockerService()
                .updateRecord(Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(new Response());
    CourseBatchSchedulerUtil.updateCourseBatchDbStatus(courseBatch, true, null);
    PowerMockito.verifyStatic(ContentUtil.class);
    ContentUtil.searchContent(Mockito.anyString(), Mockito.anyMap());
  }
}
