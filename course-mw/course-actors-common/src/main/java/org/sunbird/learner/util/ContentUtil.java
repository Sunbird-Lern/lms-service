/** */
package org.sunbird.learner.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.responsecode.ResponseCode;

/**
 * This class will make the call to EkStep content search
 *
 * @author Manzarul
 */
public final class ContentUtil {

  private static ObjectMapper mapper = new ObjectMapper();

  private ContentUtil() {}

  private static final String CHARSETS_UTF_8 = "UTF-8";

  /**
   * @param params String
   * @param headers Map<String, String>
   * @return Map<String,Object>
   */
  public static Map<String, Object> searchContent(String params, Map<String, String> headers) {
    Map<String, Object> resMap = new HashMap<>();
    try {
      String baseSearchUrl = ProjectUtil.getConfigValue(JsonKey.SEARCH_SERVICE_API_BASE_URL);
      headers.put(
          JsonKey.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.EKSTEP_AUTHORIZATION));
      headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      headers.remove(HttpHeaders.ACCEPT_ENCODING.toLowerCase());
      headers.put(HttpHeaders.ACCEPT_ENCODING.toLowerCase(), "UTF-8");
      if (StringUtils.isBlank(headers.get(JsonKey.AUTHORIZATION))) {
        headers.put(
            JsonKey.AUTHORIZATION,
            PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION));
      }
      ProjectLogger.log("making call for content search ==" + params, LoggerEnum.INFO.name());
      String response =
          HttpUtil.sendPostRequest(
              baseSearchUrl
                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
              params,
              headers);
      ProjectLogger.log("Content serach response is ==" + response, LoggerEnum.INFO.name());
      Map<String, Object> data = mapper.readValue(response, Map.class);
      if (MapUtils.isNotEmpty(data)) {
        String resmsgId = (String) ((Map<String, Object>) data.get("params")).get("resmsgid");
        String apiId = (String) data.get("id");
        data = (Map<String, Object>) data.get(JsonKey.RESULT);
        ProjectLogger.log(
            "Total number of content fetched from Ekstep while assembling page data : "
                + data.get("count"),
            LoggerEnum.INFO.name());
        if (MapUtils.isNotEmpty(data)) {
          Object contentList = data.get(JsonKey.CONTENT);
          Map<String, Object> param = new HashMap<>();
          param.put(JsonKey.RES_MSG_ID, resmsgId);
          param.put(JsonKey.API_ID, apiId);
          resMap.put(JsonKey.PARAMS, param);
          resMap.put(JsonKey.CONTENTS, contentList);
          Iterator<Map.Entry<String, Object>> itr = data.entrySet().iterator();
          while (itr.hasNext()) {
            Map.Entry<String, Object> entry = itr.next();
            if (!JsonKey.CONTENT.equals(entry.getKey())) {
              resMap.put(entry.getKey(), entry.getValue());
            }
          }
        }
      } else {
        ProjectLogger.log("EkStepRequestUtil:searchContent No data found", LoggerEnum.INFO.name());
      }
    } catch (IOException e) {
      ProjectLogger.log("Error found during contnet search parse==" + e.getMessage(), e);
    }
    return resMap;
  }

  public static String contentCall(String baseURL, String apiURL, String authKey, String body)
      throws IOException {
    HttpClient client = HttpClientBuilder.create().build();
    HttpPost post = new HttpPost(baseURL + PropertiesCache.getInstance().getProperty(apiURL));
    post.addHeader("Content-Type", "application/json; charset=utf-8");
    post.addHeader(JsonKey.AUTHORIZATION, authKey);
    post.setEntity(new StringEntity(body, CHARSETS_UTF_8));
    ProjectLogger.log(
        "BaseMetricsActor:makePostRequest completed requested data : " + body,
        LoggerEnum.INFO.name());
    ProjectLogger.log(
        "BaseMetricsActor:makePostRequest completed Url : "
            + baseURL
            + PropertiesCache.getInstance().getProperty(apiURL),
        LoggerEnum.INFO.name());
    HttpResponse response = client.execute(post);
    if (response.getStatusLine().getStatusCode() != 200) {
      ProjectLogger.log(
          "BaseMetricsActor:makePostRequest: Status code from analytics is not 200 ",
          LoggerEnum.INFO.name());
      throw new ProjectCommonException(
          ResponseCode.unableToConnect.getErrorCode(),
          ResponseCode.unableToConnect.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    BufferedReader rd =
        new BufferedReader(
            new InputStreamReader(response.getEntity().getContent(), CHARSETS_UTF_8));

    StringBuilder result = new StringBuilder();
    String line = "";
    while ((line = rd.readLine()) != null) {
      result.append(line);
    }
    ProjectLogger.log(
        "BaseMetricsActor:makePostRequest: Response from analytics store for metrics = "
            + response.toString(),
        LoggerEnum.INFO.name());
    return result.toString();
  }
}
