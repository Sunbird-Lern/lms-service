package org.sunbird.common.util;

import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import org.sunbird.common.models.util.JsonKey;
import scala.Option;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({  "javax.management.*", "javax.net.ssl.*", "javax.security.*", "com.microsoft.azure.storage.*",
        "jdk.internal.reflect.*", "sun.security.ssl.*", "javax.crypto.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@PrepareForTest({StorageServiceFactory.class, CloudStorageUtil.class})
public class CloudStorageUtilTest {

  String SIGNED_URL = "singedUrl";
  String UPLOAD_URL = "uploadUrl";
  String PUT_SIGNED_URL = "gcpSignedUrl";

  @Before
  public void initTest() {
    BaseStorageService service = mock(BaseStorageService.class);
    mockStatic(StorageServiceFactory.class);

    try {
      when(StorageServiceFactory.class, "getStorageService", Mockito.any()).thenReturn(service);

      when(service.upload(
              Mockito.anyString(),
              Mockito.anyString(),
              Mockito.anyString(),
              Mockito.any(Option.class),
              Mockito.any(Option.class),
              Mockito.any(Option.class),
              Mockito.any(Option.class)))
          .thenReturn(UPLOAD_URL);

      when(service.getSignedURL(
              Mockito.anyString(),
              Mockito.anyString(),
              Mockito.any(Option.class),
              Mockito.any(Option.class)))
          .thenReturn(SIGNED_URL);
      when(service.getPutSignedURL(
              Mockito.anyString(),
              Mockito.anyString(),
              Mockito.any(Option.class),
              Mockito.any(Option.class),
              Mockito.any(Option.class)))
              .thenReturn(PUT_SIGNED_URL);
      when(service.getSignedURLV2(
              Mockito.eq("azurecontainer"),
              Mockito.anyString(),
              Mockito.any(Option.class),
              Mockito.any(Option.class),
              Mockito.any(Option.class)))
              .thenReturn(SIGNED_URL);
      when(service.getSignedURLV2(
              Mockito.eq("gcpcontainer"),
              Mockito.anyString(),
              Mockito.any(Option.class),
              Mockito.any(Option.class),
              Mockito.any(Option.class)))
              .thenReturn(PUT_SIGNED_URL);

    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testUploadSuccess() {
    String result =
        CloudStorageUtil.upload("azure", "container", "key", "/file/path");
    assertTrue(UPLOAD_URL.equals(result));
  }

  @Test
  public void testGetSignedUrlSuccess() {
    String signedUrl = CloudStorageUtil.getSignedUrl("azure", "azurecontainer", "key");
    assertTrue(SIGNED_URL.equals(signedUrl));
  }

  @Test
  public void testGetSignedUrlGCPSuccess() {
    String signedUrl = CloudStorageUtil.getSignedUrl(JsonKey.GCP, "gcpcontainer", "key");
    assertTrue(PUT_SIGNED_URL.equals(signedUrl));
  }
}
