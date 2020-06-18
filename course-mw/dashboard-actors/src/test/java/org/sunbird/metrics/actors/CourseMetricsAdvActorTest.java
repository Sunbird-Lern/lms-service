package org.sunbird.metrics.actors;

import static org.mockito.Mockito.when;

import java.io.IOException;
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
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.userorg.UserOrgServiceImpl;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  EsClientFactory.class,
  UserOrgServiceImpl.class,
  ContentSearchUtil.class,
  CloudStorageUtil.class,
  ContentUtil.class
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
  public void courseConsumptionMetricsSuccess() throws IOException {
    group.andStaticMock(ContentUtil.class);
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    when(group.getUserOrgMockerService().getOrganisationById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomOrg().get());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(5).asESSearchResult());
    when(ContentUtil.contentCall(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(
            "{\"result\":{\"metrics\":[{\"key\":\"2019-09-04\", \"key_name\":\"2019-09-04\", \"d_period\":\"20190904\", \"m_total_ts\":100}], \"summary\":{\"m_total_ts\":100}}}");
    Request request = new Request();
    request.put(JsonKey.REQUESTED_BY, "randomUserId");
    request.put(JsonKey.COURSE_ID, "randomCourseId");
    request.put(JsonKey.PERIOD, "14d");
    request.setOperation(ActorOperations.COURSE_CREATION_METRICS.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
  }

  @Test
  public void courseConsumptionMetricsWeekSuccess() throws IOException {
    group.andStaticMock(ContentUtil.class);
    when(group.getUserOrgMockerService().getUserById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomUser().get());
    when(group.getUserOrgMockerService().getOrganisationById(Mockito.anyString()))
        .thenReturn(CustomObjectBuilder.getRandomOrg().get());
    when(group
            .getESMockerService()
            .search(Mockito.any(), Mockito.eq(EsType.usercourses.getTypeName())))
        .thenReturn(CustomObjectBuilder.getRandomUserCoursesList(5).asESSearchResult());
    when(ContentUtil.contentCall(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
        .thenReturn(
            "{\"result\":{\"metrics\":[{\"key\":\"2019-09-04\", \"key_name\":\"2019-09-04\", \"d_period\":\"20190904\", \"m_total_ts\":100}], \"summary\":{\"m_total_ts\":100}}}");
    Request request = new Request();
    request.put(JsonKey.REQUESTED_BY, "randomUserId");
    request.put(JsonKey.COURSE_ID, "randomCourseId");
    request.put(JsonKey.PERIOD, "5w");
    request.setOperation(ActorOperations.COURSE_CREATION_METRICS.getValue());
    Response response = executeInTenSeconds(request, Response.class);
    Assert.assertNotNull(response);
  }
}
