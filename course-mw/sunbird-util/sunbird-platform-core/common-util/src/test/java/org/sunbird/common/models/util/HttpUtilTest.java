package org.sunbird.common.models.util;

import com.mashape.unirest.http.Unirest;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({  "javax.management.*", "javax.net.ssl.*", "javax.security.*", "com.microsoft.azure.storage.*",
        "jdk.internal.reflect.*", "sun.security.ssl.*", "javax.crypto.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class HttpUtilTest extends BaseHttpTest {
  String JSON_STRING_DATA = "asdasasfasfsdfdsfdsfgsd";

  @Test
  public void testSendPatchRequestSuccess() {

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "123456");
    String url = "http://localhost:8000/v1/issuer/issuers";
    try {
      PowerMockito.mockStatic(Unirest.class);
      Mockito.doReturn("SUCCESS").when(Unirest.patch(url));
      String response = HttpUtil.sendPatchRequest(url, "{\"message\":\"success\"}", headers);
      assertTrue("SUCCESS".equals(response));
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage());
    }
  }

  @Test
  public void testSendPostRequestSuccess() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "123456");
    String url = "http://localhost:8000/v1/issuer/issuers";
    try {
      String response = HttpUtil.sendPostRequest(url, "{\"message\":\"success\"}", headers);
      assertTrue("{\"message\":\"success\"}".equals(response));
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage());
    }
  }

  @Test
  public void testSendGetRequestSuccess() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "123456");
    String urlString = "http://localhost:8000/v1/issuer/issuers";
    try {
      String response = HttpUtil.sendGetRequest(urlString, headers);
      assertTrue("{\"message\":\"success\"}".equals(response));
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage());
    }
  }

  @Test
  public void testGetHeaderWithInput() throws Exception {
    Map<String, String> input = new HashMap<String, String>(){{
      put("x-channel-id", "test-channel");
      put("x-device-id", "test-device");
    }};
    Map<String, String> headers = HttpUtil.getHeader(input);
    assertTrue(!headers.isEmpty());
    assertTrue(headers.size()==3);
    assertTrue(headers.containsKey("Content-Type"));
    assertTrue(headers.containsKey("x-channel-id"));
    assertTrue(headers.containsKey("x-device-id"));
  }

  @Test
  public void testGetHeaderWithoutInput() throws Exception {
    Map<String, String> headers = HttpUtil.getHeader(null);
    assertTrue(!headers.isEmpty());
    assertTrue(headers.size()==1);
    assertTrue(headers.containsKey("Content-Type"));
  }
}
