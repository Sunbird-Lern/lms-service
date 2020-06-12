package org.sunbird.learner.util;

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
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.builder.object.CustomObjectBuilder;
import org.sunbird.helper.ServiceFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class DataCacheHandlerTest {

  private static MockerBuilder.MockersGroup group;

  @Before
  public void setup() {
    group = MockerBuilder.getFreshMockerGroup().withCassandraMock(new CassandraMocker());
  }

  @Test
  public void pageManagementSuccessTest() {
    PowerMockito.when(
            group
                .getCassandraMockerService()
                .getAllRecords(Mockito.anyString(), Mockito.eq("page_management")))
        .thenReturn(CustomObjectBuilder.getRandomPageManagements(10).asCassandraResponse());
    PowerMockito.when(
            group
                .getCassandraMockerService()
                .getAllRecords(Mockito.anyString(), Mockito.eq("page_section")))
        .thenReturn(CustomObjectBuilder.getRandomPageSections(4).asCassandraResponse());
    DataCacheHandler cacheHandler = new DataCacheHandler();
    cacheHandler.run();
    Assert.assertNotNull(cacheHandler.getPageMap());
    Assert.assertNotNull(cacheHandler.getSectionMap());
    Assert.assertEquals(10, cacheHandler.getPageMap().size());
    Assert.assertEquals(4, cacheHandler.getSectionMap().size());
  }
}
