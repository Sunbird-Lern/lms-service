package org.sunbird.common.util;

import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.responsecode.ResponseCode;

@PrepareForTest(ConfigUtil.class)
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*"})
public class ConfigUtilTest {

  String configType = "user";
  String validJson = "{\"key\" : \"value\"}";
  static ConfigUtil configUtilMock;

  @BeforeClass
  public static void setup() throws Exception {
    configUtilMock = Mockito.mock(ConfigUtil.class);
    PowerMockito.whenNew(ConfigUtil.class).withAnyArguments().thenReturn(configUtilMock);
  }

  @Test(expected = ProjectCommonException.class)
  public void testGetConfigFromJsonStringFailureWithNullString() {
    try {
      ConfigUtil.getConfigFromJsonString(null, configType);
    } catch (ProjectCommonException e) {
      Assert.assertTrue(e.getCode().equals(ResponseCode.errorConfigLoadEmptyString.getErrorCode()));
      throw e;
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testGetConfigFromJsonStringFailureWithEmptyString() {
    try {
      ConfigUtil.getConfigFromJsonString("", configType);
    } catch (ProjectCommonException e) {
      Assert.assertTrue(e.getCode().equals(ResponseCode.errorConfigLoadEmptyString.getErrorCode()));
      throw e;
    }
  }

  @Test(expected = ProjectCommonException.class)
  public void testGetConfigFromJsonStringFailureWithInvalidJsonString() {
    try {
      ConfigUtil.getConfigFromJsonString("{dummy}", configType);
    } catch (ProjectCommonException e) {
      Assert.assertTrue(e.getCode().equals(ResponseCode.errorConfigLoadParseString.getErrorCode()));
      throw e;
    }
  }

  @Test
  public void testGetConfigFromJsonStringSuccess() {
    Config config = ConfigUtil.getConfigFromJsonString(validJson, configType);
    Assert.assertTrue("value".equals(config.getString("key")));
  }
}
