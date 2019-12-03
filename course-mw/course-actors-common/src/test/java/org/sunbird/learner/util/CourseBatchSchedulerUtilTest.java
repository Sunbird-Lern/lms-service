package org.sunbird.learner.util;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.builder.mocker.CassandraMocker;
import org.sunbird.builder.mocker.ESMocker;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.helper.ServiceFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  EsClientFactory.class,
  ElasticSearchHelper.class,
  HttpUtil.class
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
            .andStaticMock(HttpUtil.class);
  }
}
