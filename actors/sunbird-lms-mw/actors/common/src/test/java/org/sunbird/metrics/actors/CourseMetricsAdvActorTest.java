package org.sunbird.metrics.actors;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.application.test.SunbirdApplicationActorTest;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.mocker.UserOrgMocker;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import org.sunbird.common.util.CloudStorageUtil;
import org.sunbird.learner.util.ContentSearchUtil;
import org.sunbird.userorg.UserOrgServiceImpl;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  EsClientFactory.class,
  UserOrgServiceImpl.class,
  ContentSearchUtil.class,
  CloudStorageUtil.class
})
@PowerMockIgnore({"javax.management.*"})
public class CourseMetricsAdvActorTest extends SunbirdApplicationActorTest {

  private static MockerBuilder.MockersGroup group;

  @Before
  public void setUp() {
    group =
        MockerBuilder.getFreshMockerGroup()
            .withESMock(new ESMocker())
            .withUserOrgMock(new UserOrgMocker())
            .andStaticMock(ContentSearchUtil.class)
            .andStaticMock(CloudStorageUtil.class);
  }

  public CourseMetricsAdvActorTest() {
    init(CourseMetricsActor.class);
  }

  @Test
  public void courseProgressMetricsV2Success() {
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomCourseBatch().asESIdentifierResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.cbatchstats.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomCourseBatchStats(5).asESSearchResult());
    Request request = new Request();
    request.getContext().put(JsonKey.REQUESTED_BY, "randomUserId");
    request.getContext().put(JsonKey.BATCH_ID, "randomBatchId");
    request.getContext().put(JsonKey.LIMIT, 200);
    request.getContext().put(JsonKey.OFFSET, 0);
    request.setOperation(ActorOperations.COURSE_PROGRESS_METRICS_V2.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
    Assert.assertNotNull(response.getResult());
    Assert.assertTrue((boolean) response.getResult().get(JsonKey.SHOW_DOWNLOAD_LINK));
    List<Map<String, Object>> userData =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.DATA);
    Assert.assertNotNull(userData);
    Assert.assertEquals(5, userData.size());
  }

  @Test
  public void courseProgressMetricsSuccess() {
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomCourseBatch().asESIdentifierResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(5).asESSearchResult());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.courseBatch.getTypeName())))
        .thenReturn(
            CustomObjectBuilder.getCourseBatchBuilder()
                .generateRandomList(5)
                .buildList()
                .asESSearchResult());
    when(ContentSearchUtil.searchContentSync(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(CustomObjectBuilder.getRandomCourse().get());
    Request request = new Request();
    request.put(JsonKey.REQUESTED_BY, "randomUserId");
    request.put(JsonKey.BATCH_ID, "randomBatchId");
    request.put(JsonKey.PERIOD, "14d");
    request.setOperation(ActorOperations.COURSE_PROGRESS_METRICS.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
  }

  @Test
  public void courseProgressMetricsReportSuccess() {
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    when(group
            .getESMockerService()
            .getDataByIdentifier(Mockito.eq(EsType.courseBatch.getTypeName()), Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomCourseBatch().asESIdentifierResult());
    when(CloudStorageUtil.getAnalyticsSignedUrl(
            Mockito.any(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn("https://dummy-signed-url.com");
    Request request = new Request();
    request.put(JsonKey.REQUESTED_BY, "randomUserId");
    request.put(JsonKey.BATCH_ID, "randomBatchId");
    request.setOperation(ActorOperations.COURSE_PROGRESS_METRICS_REPORT.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
    Assert.assertTrue(response.containsKey(JsonKey.SIGNED_URL));
  }

  @Test
  public void courseConsumptionMetricsSuccess() {
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    Request request = new Request();
    request.put(JsonKey.REQUESTED_BY, "randomUserId");
    request.put(JsonKey.COURSE_ID, "randomCourseId");
    request.put(JsonKey.PERIOD, "14d");
    request.setOperation(ActorOperations.COURSE_CREATION_METRICS.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
  }
}
