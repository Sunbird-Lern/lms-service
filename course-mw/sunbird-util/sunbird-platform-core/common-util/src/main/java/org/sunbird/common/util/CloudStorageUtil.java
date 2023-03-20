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

  private static final Map<String, IStorageService> storageServiceMap = new HashMap<>();

  public enum CloudStorageType {
    AZURE(AZURE_STR),
    AWS(AWS_STR),
    GCLOUD(GCLOUD_STR),
    OCI(OCI_STR);

    private String type;

    private CloudStorageType(String type) {
      this.type = type;
    }

    public String getType() {
      return this.type;
    }

    public static CloudStorageType getByName(String type) {
      if (AZURE.type.equalsIgnoreCase(type)) {
        return CloudStorageType.AZURE;
      } if (AWS.type.equalsIgnoreCase(type)) {
        return CloudStorageType.AWS;
      } if (GCLOUD.type.equalsIgnoreCase(type)) {
        return CloudStorageType.GCLOUD;
      }if (OCI.type.equalsIgnoreCase(type)) {
        return CloudStorageType.OCI;
      } else {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.errorUnsupportedCloudStorage,
            ProjectUtil.formatMessage(
                ResponseCode.errorUnsupportedCloudStorage.getErrorMessage(), type));
        return null;
      }
    }
  }

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
    scala.Option<String> storageEndpoint = scala.Option.apply(PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_ENDPOINT));
    scala.Option<String> storageRegion = scala.Option.apply("");
    return getStorageService(storageType, storageKey, storageSecret,storageEndpoint,storageRegion);
  }

  private static IStorageService getAnalyticsStorageService(CloudStorageType storageType) {
    String storageKey = PropertiesCache.getInstance().getProperty(JsonKey.ANALYTICS_ACCOUNT_NAME);
    String storageSecret = PropertiesCache.getInstance().getProperty(JsonKey.ANALYTICS_ACCOUNT_KEY);
    scala.Option<String> storageEndpoint = scala.Option.apply(PropertiesCache.getInstance().getProperty(JsonKey.ANALYTICS_ACCOUNT_ENDPOINT));
    scala.Option<String> storageRegion = scala.Option.apply("");
    return getStorageService(storageType, storageKey, storageSecret,storageEndpoint,storageRegion);
  }

  private static IStorageService getStorageService(
      CloudStorageType storageType, String storageKey, String storageSecret,scala.Option<String> storageEndpoint ,scala.Option<String> storageRegion) {
    String compositeKey = storageType.getType() + "-" + storageKey;
    if (storageServiceMap.containsKey(compositeKey)) {
      return storageServiceMap.get(compositeKey);
    }
    synchronized (CloudStorageUtil.class) {
      StorageConfig storageConfig =
          new StorageConfig(storageType.getType(), storageKey, storageSecret,storageEndpoint,storageRegion);
      IStorageService storageService = StorageServiceFactory.getStorageService(storageConfig);
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
