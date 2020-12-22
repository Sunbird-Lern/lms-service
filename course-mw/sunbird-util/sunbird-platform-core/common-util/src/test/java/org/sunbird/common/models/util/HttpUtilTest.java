package org.sunbird.common.models.util;

import com.mashape.unirest.http.Unirest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class HttpUtilTest extends BaseHttpTest {
  public static final String JSON_STRING_DATA = "asdasasfasfsdfdsfdsfgsd";

  @Test
  public void testSendPatchRequestSuccess() {

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "123456");
    String url = "http://localhost:8000/v1/issuer/issuers";
    try {
      PowerMockito.mockStatic(Unirest.class);
      PowerMockito.when(Unirest.patch(url), Mockito.anyString(), Mockito.anyMap()).thenReturn("SUCCESS");
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
