package org.sunbird.common.models.util.oci;

import scala.Option;

import java.io.File;
import java.util.List;

import static org.sunbird.common.models.util.cloud.CloudUtils.getObjectKey;

public class OciFileUtility {

    public static String uploadFile(String containerName, String fileName, String fileLocation) {
        String objectKey = getObjectKey(containerName,fileName);
        return OciConnectionManager.getStorageService().upload(containerName, fileLocation + fileName, objectKey, Option.apply(false), Option.apply(1), Option.apply(3), Option.apply(1));
    }

    public static boolean downLoadFile(String containerName, String fileName, String downloadFolder) {
        return false;
    }

    public static String uploadFile(String containerName, File file) {
        String objectKey = getObjectKey(containerName,file.getName());
        return OciConnectionManager.getStorageService().upload(containerName, file.getAbsolutePath(), objectKey, Option.apply(false), Option.apply(1), Option.apply(3), Option.apply(1));
    }

    public static boolean deleteFile(String containerName, String fileName) {
        return false;
    }

    public static List<String> listAllFiles(String containerName) {
        return null;
    }

    public static boolean deleteContainer(String containerName) {
        return false;
    }
}
