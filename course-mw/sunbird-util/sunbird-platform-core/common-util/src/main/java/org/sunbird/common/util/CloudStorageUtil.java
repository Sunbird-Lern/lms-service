package org.sunbird.common.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import scala.Option;
import scala.Some;

import static org.sunbird.common.models.util.JsonKey.CLOUD_STORAGE_CNAME_URL;
import static org.sunbird.common.models.util.JsonKey.CLOUD_STORE_BASE_PATH;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

public class CloudStorageUtil {
  private static final int STORAGE_SERVICE_API_RETRY_COUNT = 3;

  private static final Map<String, BaseStorageService> storageServiceMap = new HashMap<>();
  
  public static String upload(
      String storageType, String container, String objectKey, String filePath) {

    BaseStorageService storageService = getStorageService(storageType);

    return storageService.upload(
            container,
            filePath,
            objectKey,
            Option.apply(false),
            Option.apply(1),
            Option.apply(STORAGE_SERVICE_API_RETRY_COUNT),
            Option.empty());
  }

  public static String getSignedUrl(
      String storageType, String container, String objectKey) {
    BaseStorageService storageService = getStorageService(storageType);
    return getSignedUrl(storageService, container, objectKey,storageType);
  }

  public static String getSignedUrl(
          BaseStorageService storageService,
          String container,
          String objectKey, String cloudType) {
    return storageService.getSignedURLV2(container, objectKey, Some.apply(getTimeoutInSeconds()),
            Some.apply("r"), Some.apply("application/pdf"));
  }



	private static BaseStorageService getStorageService(String storageType) {
		String storageKey = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_NAME);
		String storageSecret = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_KEY);
		return getStorageService(storageType, storageKey, storageSecret);
	}
	  
  private static BaseStorageService getStorageService(
	      String storageType, String storageKey, String storageSecret) {
	    String compositeKey = storageType + "-" + storageKey;
	    scala.Option<String> storageEndpoint = scala.Option.apply(PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_ENDPOINT));
	    scala.Option<String> storageRegion = scala.Option.apply("");	    
	    if (storageServiceMap.containsKey(compositeKey)) {
	      return storageServiceMap.get(compositeKey);
	    }
	    synchronized (CloudStorageUtil.class) {
	      StorageConfig storageConfig =
	      new StorageConfig(storageType, storageKey, storageSecret,storageEndpoint,storageRegion);
	      BaseStorageService storageService = StorageServiceFactory.getStorageService(storageConfig);
	      storageServiceMap.put(compositeKey, storageService);
	    }
	    return storageServiceMap.get(compositeKey);
   }
  

  private static int getTimeoutInSeconds() {
    String timeoutInSecondsStr = ProjectUtil.getConfigValue(JsonKey.DOWNLOAD_LINK_EXPIRY_TIMEOUT);
    return Integer.parseInt(timeoutInSecondsStr);
  }

  public static String getUri(
      String storageType, String container, String prefix, boolean isDirectory) {
    BaseStorageService storageService = getStorageService(storageType);
    return storageService.getUri(container, prefix, Option.apply(isDirectory));
  }

  public static String getBaseUrl() {
    String baseUrl = getConfigValue(CLOUD_STORAGE_CNAME_URL);
    if(StringUtils.isEmpty(baseUrl))
      baseUrl = getConfigValue(CLOUD_STORE_BASE_PATH);
    return baseUrl;
  }
}
