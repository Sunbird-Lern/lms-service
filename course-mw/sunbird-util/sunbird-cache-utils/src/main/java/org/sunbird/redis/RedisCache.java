package org.sunbird.redis;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.sunbird.cache.interfaces.Cache;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.notification.utils.JsonUtil;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisCache implements Cache {
  private static final String CACHE_MAP_LIST = "cache.mapNames";
  private Map<String, String> properties = readConfig();
  private String[] mapNameList = properties.get(CACHE_MAP_LIST).split(",");
  private RedissonClient client;

  public RedisCache() {
    client = RedisConnectionManager.getClient();
  }
  private LoggerUtil logger = new LoggerUtil(RedisCache.class);

  @Override
  public String get(String mapName, String key) {
    try {
      RMap<String, String> map = client.getMap(mapName);
      String s = map.get(key);
      return s;
    } catch (Exception e) {
      logger.error(null, 
          "RedisCache:get: Error occurred mapName = " + mapName + ", key = " + key, e);
    }
    return null;
  }

  @Override
  public boolean clear(String mapName) {
    logger.info(null, "RedisCache:clear: mapName = " + mapName);
    try {
      RMap<String, String> map = client.getMap(mapName);
      map.clear();
      return true;
    } catch (Exception e) {
      logger.error(null, 
          "RedisCache:clear: Error occurred mapName = " + mapName + " error = " , e);
    }
    return false;
  }

  @Override
  public void clearAll() {
    logger.info(null, "RedisCache: clearAll called");
    for (int i = 0; i < mapNameList.length; i++) {
      clear(mapNameList[i]);
    }
  }

  @Override
  public boolean setMapExpiry(String name, long seconds) {
    boolean result = client.getMap(name).expire(seconds, TimeUnit.SECONDS);

    logger.info(null, 
        "RedisCache:setMapExpiry: name = " + name + " seconds = " + seconds + " result = " + result);

    return result;
  }

  public boolean put(String mapName, String key, Object value) {
    logger.info(null, 
        "RedisCache:put: mapName = " + mapName + ", key = " + key + ", value = " + value);

    try {
      String res = "";
      if(value instanceof String){
        res = (String) value;
      } else {
        res = JsonUtil.toJson(value);
      }
      RMap<String, String> map = client.getMap(mapName);
      map.put(key, res);
      return true;
    } catch (Exception e) {
      logger.error(null, 
          "RedisCache:put: Error occurred mapName = "
              + mapName
              + ", key = "
              + key
              + ", value = "
              + value, e);
    }
    return false;
  }

  public Object get(String mapName, String key, Class<?> cls) {
    try {
      RMap<String, String> map = client.getMap(mapName);
      String s = map.get(key);
      return JsonUtil.getAsObject(s, cls);
    } catch (Exception e) {
      logger.error(null, 
          "RedisCache:get: Error occurred mapName = " + mapName + ", key = " + key, e);
    }
    return null;
  }
}
