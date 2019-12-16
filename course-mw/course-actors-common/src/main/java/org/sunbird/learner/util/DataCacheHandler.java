/** */
package org.sunbird.learner.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.helper.ServiceFactory;

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
  private static final String KEY_SPACE_NAME = "sunbird";

  @Override
  public void run() {
    ProjectLogger.log("DataCacheHandler:run: Cache refresh started.", LoggerEnum.INFO.name());
    cache(pageMap, "page_management");
    cache(sectionMap, "page_section");
    ProjectLogger.log("DataCacheHandler:run: Cache refresh completed.", LoggerEnum.INFO.name());
  }

  @SuppressWarnings("unchecked")
  private void cache(Map<String, Map<String, Object>> map, String tableName) {
    try {
      Response response = cassandraOperation.getAllRecords(KEY_SPACE_NAME, tableName);
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
      ProjectLogger.log("pagemap keyset " + map.keySet());
      ProjectLogger.log(tableName + " cache size: " + map.size(), LoggerEnum.INFO.name());
    } catch (Exception e) {
      ProjectLogger.log(
          "DataCacheHandler:cache: Exception in retrieving page section " + e.getMessage(), e);
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
