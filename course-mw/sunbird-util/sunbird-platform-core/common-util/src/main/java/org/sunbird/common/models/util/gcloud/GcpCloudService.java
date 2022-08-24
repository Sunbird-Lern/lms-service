package org.sunbird.common.models.util.gcloud;

import org.sunbird.common.models.util.cloud.CloudService;

import java.io.File;
import java.util.List;

public class GcpCloudService implements CloudService {
    @Override
    public String uploadFile(String containerName, String fileName, String fileLocation) {
        return GcpFileUtility.uploadFile(containerName, fileName, fileLocation);
    }

    @Override
    public boolean downLoadFile(String containerName, String fileName, String downloadFolder) {
        return GcpFileUtility.downLoadFile(containerName, fileName, downloadFolder);
    }

    @Override
    public String uploadFile(String containerName, File file) {
        return GcpFileUtility.uploadFile(containerName, file);
    }

    @Override
    public boolean deleteFile(String containerName, String fileName) {
        return GcpFileUtility.deleteFile(containerName, fileName);
    }

    @Override
    public List<String> listAllFiles(String containerName) {
        return GcpFileUtility.listAllFiles(containerName);
    }

    @Override
    public boolean deleteContainer(String containerName) {
        return GcpFileUtility.deleteContainer(containerName);
    }
}
