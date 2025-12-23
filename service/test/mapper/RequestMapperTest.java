package mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import play.libs.Json;

import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Json.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class RequestMapperTest {
  @Test
  public void testMapRequestSuccess() throws Exception {
    Request request;
    JsonNode node =
        new ObjectMapper().convertValue(createRequestMap(JsonKey.REQUEST), JsonNode.class);
    request = (Request) RequestMapper.mapRequest(node, Request.class);
    Assert.assertNotNull(request);
    Assert.assertEquals("xyz", request.getRequest().get(JsonKey.FIRST_NAME));
  }

  @Test
  public void testMapRequestFailure() throws Exception {
    try {
      RequestMapper.mapRequest(null, Request.class);
    } catch (ProjectCommonException e) {
      Assert.assertEquals(ResponseCode.contentTypeRequiredError.getErrorMessage(), e.getMessage());
      Assert.assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
    }
  }

  @Test
  public void testMapRequestFailureWithInvalidKey() throws Exception {
    Request request;
    JsonNode node = new ObjectMapper().convertValue(createRequestMap("invalidKey"), JsonNode.class);
    request = (Request) RequestMapper.mapRequest(node, Request.class);
    Assert.assertNotNull(request);
    Assert.assertNull(request.getRequest().get(JsonKey.FIRST_NAME));
  }

  private Map<String, Object> createRequestMap(String requestKey) {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.FIRST_NAME, "xyz");
    innerMap.put(JsonKey.PHONE, "1234567890");
    innerMap.put(JsonKey.EMAIL, "xyz@gmail.com");
    map.put(requestKey, innerMap);
    return map;
  }
}
