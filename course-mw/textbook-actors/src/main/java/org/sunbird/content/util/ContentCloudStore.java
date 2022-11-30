package org.sunbird.content.util;

import static java.io.File.separator;
import static org.sunbird.common.models.util.JsonKey.*;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import java.io.File;
import org.sunbird.common.util.CloudStorageUtil;

public class ContentCloudStore {

  public static String FOLDER = getConfigValue(CLOUD_FOLDER_CONTENT);
  public static String storageType = getConfigValue(CONTENT_CLOUD_STORAGE_TYPE);

  public static String getUri(String prefix, boolean isDirectory) {
    prefix = FOLDER + prefix;
    try {
      return CloudStorageUtil.getUri(storageType, getConfigValue(CONTENT_CLOUD_STORAGE_CONTAINER), prefix, isDirectory);
    } catch (Exception e) {
      return null;
    }
  }

  public static String upload(String objectKey, File file) {
    objectKey = FOLDER + objectKey + separator;
    if (file.isFile()) {
      objectKey += file.getName();
      return CloudStorageUtil.upload(
          storageType, getConfigValue(CONTENT_CLOUD_STORAGE_CONTAINER), objectKey, file.getAbsolutePath());
    } else {
      return null;
    }
  }

  public static String upload(String storageType, String objectKey, File file) {
    objectKey = FOLDER + objectKey + separator;
    if (file.isFile()) {
      objectKey += file.getName();
      return CloudStorageUtil.upload(
          storageType, getConfigValue(CONTENT_CLOUD_STORAGE_CONTAINER), objectKey, file.getAbsolutePath());
    } else {
      return null;
    }
  }
}
