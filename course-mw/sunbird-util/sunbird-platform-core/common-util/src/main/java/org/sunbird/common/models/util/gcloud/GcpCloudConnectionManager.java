package org.sunbird.common.models.util.gcloud;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;
import static org.sunbird.common.models.util.JsonKey.GCLOUD_STR;

public class GcpCloudConnectionManager {
    private static String accountName = "";
    private static String accountKey = "";
    private static scala.Option<String> accountEndpoint = scala.Option.apply("");

    private static scala.Option<String> accountRegion = scala.Option.apply("");
    private static GcpCloudConnectionManager connectionManager;
    private static BaseStorageService baseStorageService;

    static {
        String name = System.getenv(JsonKey.ACCOUNT_NAME);
        String key = System.getenv(JsonKey.ACCOUNT_KEY);
        scala.Option<String> endpoint = scala.Option.apply(System.getenv(JsonKey.ACCOUNT_ENDPOINT));
        scala.Option<String> region = scala.Option.apply("");
        if (StringUtils.isBlank(name) || StringUtils.isBlank(key)) {
            ProjectLogger.log(
                    "Gcloud account name and key is not provided by environment variable." + name + " " + key);
            accountName = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_NAME);
            accountKey = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_KEY);
            scala.Option<String> accountEndpoint = scala.Option.apply(System.getenv(JsonKey.ACCOUNT_ENDPOINT));
            scala.Option<String> accountRegion = scala.Option.apply("");
        } else {
            accountName = name;
            accountKey = key;
            accountEndpoint = endpoint;
            accountRegion= region;
            ProjectLogger.log(
                    "Gcloud account name and key is  provided by environment variable." + name + " " + key);
        }
    }

    private GcpCloudConnectionManager() throws CloneNotSupportedException {
        if (connectionManager != null) throw new CloneNotSupportedException();
    }

    public static BaseStorageService getStorageService(){
        if(null == baseStorageService){
            baseStorageService = StorageServiceFactory.getStorageService(new StorageConfig(GCLOUD_STR, accountKey, accountName,accountEndpoint,accountRegion));
            ProjectLogger.log(
                    "Gcloud account storage service with account name and key as " + accountName + " " + accountKey);
        }
        return  baseStorageService;
    }
}
