package org.sunbird.common.models.util.oci;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;
import static org.sunbird.common.models.util.JsonKey.OCI_STR;

public class OciConnectionManager {
    private static String accountName = "";
    private static String accountKey = "";

    private static scala.Option<String> accountEndpoint = scala.Option.apply("");

    private static scala.Option<String> accountRegion = scala.Option.apply("");
    private static OciConnectionManager connectionManager;
    private static BaseStorageService baseStorageService;

    static {
        String name = System.getenv(JsonKey.ACCOUNT_NAME);
        String key = System.getenv(JsonKey.ACCOUNT_KEY);
        scala.Option<String> endpoint = scala.Option.apply(System.getenv(JsonKey.ACCOUNT_ENDPOINT));
        scala.Option<String> region = scala.Option.apply("");
        if (StringUtils.isBlank(name) || StringUtils.isBlank(key) ||  StringUtils.isBlank(endpoint.toString()) ) {
            ProjectLogger.log(
                    "OCI account name and key and endpont is not provided by environment variable." + name + " " + key+ " " + endpoint);
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
                    "OCI account name and key and endpoint is  provided by environment variable." + name + " " + key+ " " + endpoint);
        }
    }

    private OciConnectionManager() throws CloneNotSupportedException {
        if (connectionManager != null) throw new CloneNotSupportedException();
    }

    public static BaseStorageService getStorageService(){
        if(null == baseStorageService){
            baseStorageService = StorageServiceFactory.getStorageService(new StorageConfig(OCI_STR, accountKey, accountName,accountEndpoint,accountRegion));
            ProjectLogger.log(
                    "Oci account storage service with account name and key and endpoint as " + accountName + " " + accountKey+ " " + accountEndpoint);
        }
        return  baseStorageService;
    }
}
