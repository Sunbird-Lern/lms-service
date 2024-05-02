package org.sunbird.common.factory;

import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.util.LoggerUtil;

public class EsClientFactory {

  private static ElasticSearchService restClient = null;
  private static final LoggerUtil logger = new LoggerUtil(EsClientFactory.class);

  /**
   * This method return REST/TCP client for elastic search
   *
   * @return ElasticSearchService with the respected type impl
   */
  public static ElasticSearchService getInstance() {
    if (restClient == null) {
      synchronized (EsClientFactory.class) {
        if (restClient == null) {
          restClient = new ElasticSearchRestHighImpl();
        }
      }
    }
    return restClient;
  }

}
