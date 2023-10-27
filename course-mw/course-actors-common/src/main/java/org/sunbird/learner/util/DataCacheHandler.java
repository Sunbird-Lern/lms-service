/** */
package org.sunbird.learner.util;

import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TableNameUtil;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.helper.ServiceFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class will handle the data cache.
 *
 * @author Amit Kumar
 */
public class DataCacheHandler implements Runnable {
  /**
   * pageMap is the map of (orgId:pageName) and page Object (i.e map of string , object) sectionMap
   * is the map of section Id and section Object (i.e map of string , object)
   */
  private static Map<String, Map<String, Object>> pageMap = new ConcurrentHashMap<>();

  private static Map<String, Map<String, Object>> sectionMap = new ConcurrentHashMap<>();
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private LoggerUtil logger = new LoggerUtil(DataCacheHandler.class);
  
  @Override
  public void run() {
    logger.info(null, "DataCacheHandler:run: Cache refresh started.");
    cache(pageMap, TableNameUtil.PAGE_MANAGEMENT_TABLENAME);
    cache(sectionMap, TableNameUtil.PAGE_SECTION_TABLENAME);
    logger.info(null, "DataCacheHandler:run: Cache refresh completed.");
  }

  @SuppressWarnings("unchecked")
  private void cache(Map<String, Map<String, Object>> map, String tableName) {
    try {
      Response response = cassandraOperation.getAllRecords(null, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), tableName);
      List<Map<String, Object>> responseList =
          (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
      if (null != responseList && !responseList.isEmpty()) {
        for (Map<String, Object> resultMap : responseList) {
          if (tableName.equalsIgnoreCase(JsonKey.PAGE_SECTION)) {
            map.put((String) resultMap.get(JsonKey.ID), resultMap);
          } else {
            String orgId =
                (((String) resultMap.get(JsonKey.ORGANISATION_ID)) == null
                    ? "NA"
                    : (String) resultMap.get(JsonKey.ORGANISATION_ID));
            map.put(orgId + ":" + ((String) resultMap.get(JsonKey.PAGE_NAME)), resultMap);
          }
        }
      }
      logger.debug(null, "pagemap keyset " + map.keySet());
      logger.info(null, tableName + " cache size: " + map.size());
    } catch (Exception e) {
      logger.error(null, "DataCacheHandler:cache: Exception in retrieving page section " + e.getMessage(), e);
    }
  }

  /** @return the pageMap */
  public static Map<String, Map<String, Object>> getPageMap() {
    return pageMap;
  }

  /** @return the sectionMap */
  public static Map<String, Map<String, Object>> getSectionMap() {
    return sectionMap;
  }
}
