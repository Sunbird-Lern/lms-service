package org.sunbird.common.models.util.cloud;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.ProjectLogger;

public class CloudUtils {

    public static String getObjectKey(String containerName, String fileName) {
        if (containerName == null || fileName == null) {
            ProjectLogger.log("Container or fileName can not be null");
            return "";
        }
        String containerPath = "";
        String filePath = "";
        String contrName = containerName;
        String objectKey = "";

        if (containerName.startsWith("/")) {
            contrName = containerName.substring(1);
        }
        if (contrName.contains("/")) {
            String[] arr = contrName.split("/", 2);
            containerPath = arr[0];
            if (arr[1].length() > 0 && arr[1].endsWith("/")) {
                filePath = arr[1];
            } else if (arr[1].length() > 0) {
                filePath = arr[1] + "/";
            }
        } else {
            containerPath = contrName;
        }

        if(StringUtils.isBlank(filePath))
            objectKey = containerPath.endsWith("/") ? containerPath + fileName : containerPath + "/" + fileName;
        else
            objectKey = filePath + fileName;

        return objectKey;
    }
}
