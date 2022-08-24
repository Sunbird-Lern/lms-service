package org.sunbird.common.models.util.aws;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;
import static org.sunbird.common.models.util.JsonKey.AWS_STR;

public class AwsConnectionManager {
    private static String accountName = "";
    private static String accountKey = "";
    private static AwsConnectionManager connectionManager;
    private static BaseStorageService baseStorageService;

    static {
        String name = System.getenv(JsonKey.ACCOUNT_NAME);
        String key = System.getenv(JsonKey.ACCOUNT_KEY);
        if (StringUtils.isBlank(name) || StringUtils.isBlank(key)) {
            ProjectLogger.log(
                    "Aws account name and key is not provided by environment variable." + name + " " + key);
            accountName = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_NAME);
            accountKey = PropertiesCache.getInstance().getProperty(JsonKey.ACCOUNT_KEY);
        } else {
            accountName = name;
            accountKey = key;
            ProjectLogger.log(
                    "Aws account name and key is  provided by environment variable." + name + " " + key);
        }
    }

    private AwsConnectionManager() throws CloneNotSupportedException {
        if (connectionManager != null) throw new CloneNotSupportedException();
    }

    public static BaseStorageService getStorageService(){
        if(null == baseStorageService){
            baseStorageService = StorageServiceFactory.getStorageService(new StorageConfig(AWS_STR, accountKey, accountName));
            ProjectLogger.log(
                    "Aws account storage service with account name and key as " + accountName + " " + accountKey);
        }
        return  baseStorageService;
    }
}
