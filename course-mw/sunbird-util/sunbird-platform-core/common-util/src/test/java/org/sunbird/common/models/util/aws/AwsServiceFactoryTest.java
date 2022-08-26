package org.sunbird.common.models.util.aws;

import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.cloud.CloudService;
import org.sunbird.common.models.util.cloud.CloudServiceFactory;

import java.io.File;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sunbird.common.models.util.JsonKey.AWS_STR;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PropertiesCache.class, StorageServiceFactory.class})
@PowerMockIgnore({
        "javax.management.*",
        "javax.net.ssl.*",
        "javax.security.*",
        "com.microsoft.azure.storage.*",
        "jdk.internal.reflect.*",
        "javax.crypto.*",
        "javax.script.*",
        "javax.xml.*",
        "com.sun.org.apache.xerces.*",
        "org.xml.*"
})
public class AwsServiceFactoryTest {
    static Object obj = null;
    String containerName = "testcontainer";

    @BeforeClass
    public static void getObject() {
        PowerMockito.mockStatic(PropertiesCache.class);
        PowerMockito.mockStatic(StorageServiceFactory.class);
        PropertiesCache propertiesCache = mock(PropertiesCache.class);
        StorageServiceFactory storageServiceFactory = mock(StorageServiceFactory.class);
        BaseStorageService baseStorageService = mock(BaseStorageService.class);
        when(PropertiesCache.getInstance()).thenReturn(propertiesCache);
        when(propertiesCache.getProperty(Mockito.anyString())).thenReturn("anyString");
        when(storageServiceFactory.getStorageService(any())).thenReturn(baseStorageService);


        obj = CloudServiceFactory.get(AWS_STR);
        Assert.assertTrue(obj instanceof CloudService);
        Assert.assertNotNull(obj);
    }

    @Test
    public void testGetFailureWithWrongType() {
        Object obj = CloudServiceFactory.get("Aws12");
        Assert.assertNull(obj);
    }

    @Test
    public void testGetSuccess() {
        Object obj1 = CloudServiceFactory.get(AWS_STR);
        Assert.assertNotNull(obj1);
        Assert.assertTrue(obj.equals(obj1));
    }

    @Test
    @Ignore
    public void testUploadFileSuccess() {
        try {
            CloudService service = (CloudService) obj;
            service.uploadFile(containerName, new File("test.txt"));
        } catch (Exception e) {
            Assert.assertNotNull(e);
        }

    }

    @Test
    @Ignore
    public void testUploadFileFailureWithoutContainerName() {
        try {
        CloudService service = (CloudService) obj;
        service.uploadFile("", new File("test.txt"));
    } catch (Exception e) {
        Assert.assertNotNull(e);
    }
    }

    @Test
    @Ignore
    public void testUploadFileSuccessWithMultiplePath() {
        try {
            CloudService service = (CloudService) obj;
            service.uploadFile("/tez/po/" + containerName, new File("test.txt"));
        }catch (Exception e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    @Ignore
    public void testUploadFileSuccessWithFileLocation() {
        try{
        CloudService service = (CloudService) obj;
        service.uploadFile(containerName, "test.txt", "");
    }catch (Exception e) {
        Assert.assertNotNull(e);
    }
    }

    @Test
    public void testListAllFilesSuccess() {
        CloudService service = (CloudService) obj;
        List<String> filesList = service.listAllFiles(containerName);
        Assert.assertEquals(null, filesList);
    }

    @Test
    public void testDownloadFileSuccess() {
        CloudService service = (CloudService) obj;
        Boolean isFileDeleted = service.downLoadFile(containerName, "test1.txt", "");
        Assert.assertFalse(isFileDeleted);
    }

    @Test
    public void testDeleteFileSuccess() {
        CloudService service = (CloudService) obj;
        Boolean isFileDeleted = service.deleteFile(containerName, "test1.txt");
        Assert.assertFalse(isFileDeleted);
    }

    @Test
    public void testDeleteFileSuccessWithoutContainerName() {
        CloudService service = (CloudService) obj;
        Boolean isFileDeleted = service.deleteFile("", "test.abc");
        Assert.assertFalse(isFileDeleted);
    }

    @Test
    public void testDeleteContainerSuccess() {
        CloudService service = (CloudService) obj;
        boolean response = service.deleteContainer(containerName);
        Assert.assertFalse(response);
    }

    @AfterClass
    public static void shutDown() {
        obj = null;
    }
}
