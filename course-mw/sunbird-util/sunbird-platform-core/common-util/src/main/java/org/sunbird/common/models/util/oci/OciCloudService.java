package org.sunbird.common.models.util.oci;

import org.sunbird.common.models.util.cloud.CloudService;

import java.io.File;
import java.util.List;

public class OciCloudService implements CloudService {
    @Override
    public String uploadFile(String containerName, String fileName, String fileLocation) {
        return OciFileUtility.uploadFile(containerName, fileName, fileLocation);
    }

    @Override
    public boolean downLoadFile(String containerName, String fileName, String downloadFolder) {
        return OciFileUtility.downLoadFile(containerName, fileName, downloadFolder);
    }

    @Override
    public String uploadFile(String containerName, File file) {
        return OciFileUtility.uploadFile(containerName, file);
    }

    @Override
    public boolean deleteFile(String containerName, String fileName) {
        return OciFileUtility.deleteFile(containerName, fileName);
    }

    @Override
    public List<String> listAllFiles(String containerName) {
        return OciFileUtility.listAllFiles(containerName);
    }

    @Override
    public boolean deleteContainer(String containerName) {
        return OciFileUtility.deleteContainer(containerName);
    }
}
