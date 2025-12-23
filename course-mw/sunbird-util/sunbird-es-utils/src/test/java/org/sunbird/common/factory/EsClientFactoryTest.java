package org.sunbird.common.factory;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.inf.ElasticSearchService;

public class EsClientFactoryTest {

  @Test
  public void testGetRestClient() {
    ElasticSearchService service = EsClientFactory.getInstance();
    Assert.assertTrue(service instanceof ElasticSearchRestHighImpl);
  }
}
