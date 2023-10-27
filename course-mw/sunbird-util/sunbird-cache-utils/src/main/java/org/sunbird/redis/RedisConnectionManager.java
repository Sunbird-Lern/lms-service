package org.sunbird.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;

public class RedisConnectionManager {
  private static String host = ProjectUtil.getConfigValue(JsonKey.REDIS_HOST_VALUE);
  private static String port = ProjectUtil.getConfigValue(JsonKey.REDIS_PORT_VALUE);
  private static Boolean isRedisCluster = host.contains(",") ? true : false;
  private static String scanInterval = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_REDIS_SCAN_INTERVAL);
  private static int poolsize =
      Integer.valueOf(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_REDIS_CONN_POOL_SIZE));
  private static RedissonClient client = null;
  private static LoggerUtil logger = new LoggerUtil(RedisConnectionManager.class);

  public static RedissonClient getClient() {
    if (client == null) {
      logger.info(null, 
          "RedisConnectionManager:getClient: Redis client is null");
      boolean start = initialiseConnection();
      logger.info(null, 
          "RedisConnectionManager:getClient: Connection status = " + start);
    }
    return client;
  }

  private static boolean initialiseConnection() {
    try {
      if (isRedisCluster) {
        initialisingClusterServer(host, port);
      } else {
        initialiseSingleServer(host, port);
      }
    } catch (Exception e) {
      logger.error(null, 
          "RedisConnectionManager:initialiseConnection: Error occurred = " + e.getMessage(), e);
      return false;
    }
    return true;
  }

  private static void initialiseSingleServer(String host, String port) {
    logger.info(null, 
        "RedisConnectionManager: initialiseSingleServer called");

    Config config = new Config();
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(host + ":" + port);
    singleServerConfig.setConnectionPoolSize(poolsize);
    config.setCodec(new StringCodec());
    client = Redisson.create(config);
  }

  private static void initialisingClusterServer(String host, String port) {
    logger.info(null, 
        "RedisConnectionManager: initialisingClusterServer called with host = "
            + host
            + " port = "
            + port);

    String[] hosts = host.split(",");
    String[] ports = port.split(",");

    Config config = new Config();

    try {
      config.setCodec(new StringCodec());
      ClusterServersConfig clusterConfig = config.useClusterServers();

      clusterConfig.setScanInterval(Integer.parseInt(scanInterval));
      clusterConfig.setMasterConnectionPoolSize(poolsize);

      for (int i = 0; i < hosts.length && i < ports.length; i++) {
        clusterConfig.addNodeAddress("redis://" + hosts[i] + ":" + ports[i]);
      }

      client = Redisson.create(config);

      logger.info(null, 
          "RedisConnectionManager:initialisingClusterServer: Redis client is created");
    } catch (Exception e) {
      logger.error(null, 
          "RedisConnectionManager:initialisingClusterServer: Error occurred = " + e.getMessage(), e);
    }
  }
}
