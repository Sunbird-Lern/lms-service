/** */
package org.sunbird.learner.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.responsecode.ResponseCode;

/** @author rahul */
@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpUtil.class})
@PowerMockIgnore("javax.management.*")
public class ContentUtilTest {

  private static MockerBuilder.MockersGroup group;

  @Before
  public void setup() {
    group = MockerBuilder.getFreshMockerGroup().andStaticMock(HttpUtil.class);
  }

  @Test
  public void searchContentSuccessTest() throws Exception {
    PowerMockito.when(
            HttpUtil.sendPostRequest(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(
            "{\"id\":\"dummy\",\"params\":{\"resmsgid\":\"randomId\"},\"result\":{\"content\":[],\"count\":0}}");
    Map<String, Object> result = ContentUtil.searchContent("dummyTest", new HashMap<>());
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.PARAMS));
    Assert.assertEquals(
        "dummy", ((Map<String, Object>) result.get(JsonKey.PARAMS)).get(JsonKey.API_ID));
    Assert.assertNotNull(result.get(JsonKey.CONTENTS));
  }

  @Test
  public void searchContentEmptyTest() throws Exception {
    PowerMockito.when(
            HttpUtil.sendPostRequest(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn("{}");
    Map<String, Object> result = ContentUtil.searchContent("dummyTest", new HashMap<>());
    Assert.assertNotNull(result);
    Assert.assertNull(result.get(JsonKey.PARAMS));
    Assert.assertNull(result.get(JsonKey.CONTENTS));
  }

  @Test
  public void contentCallSuccessTest() throws Exception {
    PowerMockito.when(
            HttpUtil.doPostRequest(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(new HttpUtilResponse("some dummy response", 200));
    String result =
        ContentUtil.contentCall("dummyBaseUrl", "dummyConfigKey", "dummyAuthKey", "dummy body");
    Assert.assertNotNull(result);
    Assert.assertEquals("some dummy response", result);
  }

  @Test
  public void contentCallFailureTest() throws IOException {
    PowerMockito.when(
            HttpUtil.doPostRequest(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(new HttpUtilResponse("No response", 404));
    String result = null;
    try {
      result =
          ContentUtil.contentCall("dummyBaseUrl", "dummyConfigKey", "dummyAuthKey", "dummy body");
    } catch (ProjectCommonException ex) {
      Assert.assertEquals(ResponseCode.unableToConnect.getErrorCode(), ex.getCode());
    }
    Assert.assertNull(result);
  }

  @Test
  public void getContentTest() throws Exception {
    PowerMockito.when(HttpUtil.sendGetRequest(Mockito.anyString(), Mockito.any()))
            .thenReturn("{\"result\":{\"content\":{\"contentType\":\"Course\",\"identifier\":\"do_1130293726460805121168\",\"languageCode\":[\"en\"],\"status\":\"Live\"}}}");
    Map<String, Object> result = ContentUtil.getContent("do_1130293726460805121168", null);
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.get(JsonKey.CONTENT));
  }
}
