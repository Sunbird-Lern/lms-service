package org.sunbird.builder.mocker;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;

public class ESMocker implements Mocker<ElasticSearchService> {
  private ElasticSearchService esService;

  public ESMocker() {
    esService = mock(ElasticSearchRestHighImpl.class);
    PowerMockito.mockStatic(EsClientFactory.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
  }

  public ElasticSearchService getServiceMock() {
    return esService;
  }
}
