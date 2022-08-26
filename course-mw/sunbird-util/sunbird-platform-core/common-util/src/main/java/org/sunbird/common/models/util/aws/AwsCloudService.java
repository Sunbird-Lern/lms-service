package org.sunbird.common.models.util.aws;

import org.sunbird.common.models.util.cloud.CloudService;

import java.io.File;
import java.util.List;

public class AwsCloudService implements CloudService {
    @Override
    public String uploadFile(String containerName, String fileName, String fileLocation) {
        return AwsFileUtility.uploadFile(containerName, fileName, fileLocation);
    }

    @Override
    public boolean downLoadFile(String containerName, String fileName, String downloadFolder) {
        return AwsFileUtility.downLoadFile(containerName, fileName, downloadFolder);
    }

    @Override
    public String uploadFile(String containerName, File file) {
        return AwsFileUtility.uploadFile(containerName, file);
    }

    @Override
    public boolean deleteFile(String containerName, String fileName) {
        return AwsFileUtility.deleteFile(containerName, fileName);
    }

    @Override
    public List<String> listAllFiles(String containerName) {
        return AwsFileUtility.listAllFiles(containerName);
    }

    @Override
    public boolean deleteContainer(String containerName) {
        return AwsFileUtility.deleteContainer(containerName);
    }
}
