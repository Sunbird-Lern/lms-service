/** */
package org.sunbird.learner.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;

import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.JsonUtil;

/**
 * This class will make the call to EkStep content search
 *
 * @author Manzarul
 */
public final class ContentUtil {

  private static ObjectMapper mapper = new ObjectMapper();
  public static Map<String, String> headerMap = new HashMap<>();
  private static String EKSTEP_COURSE_SEARCH_QUERY =
          "{\"request\": {\"filters\":{\"contentType\": [\"Course\"], \"identifier\": \"COURSE_ID_PLACEHOLDER\", \"status\": \"Live\", \"mimeType\": \"application/vnd.ekstep.content-collection\", \"trackable.enabled\": \"Yes\"},\"limit\": 1}}";
  private static LoggerUtil logger = new LoggerUtil(ContentUtil.class);
  private ContentUtil() {}

  static {
    String header = ProjectUtil.getConfigValue(JsonKey.EKSTEP_AUTHORIZATION);
    header = JsonKey.BEARER + header;
    headerMap.put(JsonKey.AUTHORIZATION, header);
    headerMap.put("Content-Type", "application/json");
  }

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
      logger.info(null, "making call for content search ==" + params);
      String response =
          HttpUtil.sendPostRequest(
              baseSearchUrl
                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
              params,
              headers);
      logger.info(null, "Content search response", null, new HashMap<>(){{put("response", response);}});
      Map<String, Object> data = mapper.readValue(response, Map.class);
      if (MapUtils.isNotEmpty(data)) {
        String resmsgId = (String) ((Map<String, Object>) data.get("params")).get("resmsgid");
        String apiId = (String) data.get("id");
        data = (Map<String, Object>) data.get(JsonKey.RESULT);
        logger.info(null,
            "Total number of content fetched from Ekstep while assembling page data : "
                + data.get("count"));
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
        logger.info(null, "EkStepRequestUtil:searchContent No data found");
      }
    } catch (Exception e) {
      logger.error(null, "Error found during contnet search parse==" + e.getMessage(), e);
    }
    return resMap;
  }

  /**
   * Returns the count
   *
   * @param params String
   * @param headers Map<String, String>
   * @return Map<String,Object>
   */
  public static Map<String, Object> searchContentCount(String params, Map<String, String> headers) {
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
      logger.info(null, "making call for content search ==" + params);
      String response =
              HttpUtil.sendPostRequest(
                      baseSearchUrl
                              + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_COUNT_URL),
                      params,
                      headers);
      logger.info(null, "Content search response", null, new HashMap<>(){{put("response", response);}});
      Map<String, Object> data = mapper.readValue(response, Map.class);
      if (MapUtils.isNotEmpty(data)) {
        String resmsgId = (String) ((Map<String, Object>) data.get("params")).get("resmsgid");
        String apiId = (String) data.get("id");
        data = (Map<String, Object>) data.get(JsonKey.RESULT);
        logger.info(null,
                "Total number of content fetched from Ekstep while assembling page data : "
                        + data.get(JsonKey.COUNT));
        if (MapUtils.isNotEmpty(data)) {
          Map<String, Object> param = new HashMap<>();
          param.put(JsonKey.RES_MSG_ID, resmsgId);
          param.put(JsonKey.API_ID, apiId);
          resMap.put(JsonKey.PARAMS, param);
          resMap.put(JsonKey.COUNT, data.get(JsonKey.COUNT));
        }
      } else {
        logger.info(null, "EkStepRequestUtil:searchContent No data found");
      }
    } catch (Exception e) {
      logger.error(null, "Error found during content search parse==" + e.getMessage(), e);
    }
    return resMap;
  }

  public static String contentCall(String baseURL, String apiURL, String authKey, String body)
      throws IOException {
    String url = baseURL + PropertiesCache.getInstance().getProperty(apiURL);
    logger.info(null,
        "BaseMetricsActor:makePostRequest completed requested url :" + url, null, new HashMap<>(){{put("data", body);}});
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json; charset=utf-8");
    headers.put(JsonKey.AUTHORIZATION, authKey);
    HttpUtilResponse response = HttpUtil.doPostRequest(url, body, headers);
    if (response == null || response.getStatusCode() != 200) {
      logger.info(null,
          "BaseMetricsActor:makePostRequest: Status code from analytics is not 200");
      throw new ProjectCommonException(
          ResponseCode.unableToConnect.getErrorCode(),
          ResponseCode.unableToConnect.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }

    String result = response.getBody();
    logger.info(null,
        "BaseMetricsActor:makePostRequest: Response from analytics store for metrics", null, new HashMap<>(){{put("result", result);}});
    return result;
  }
  public static Map<String, Object> getContent(String courseId, List<String> fields) {
    Map<String, Object> resMap = new HashMap<>();
    Map<String, String> headers = new HashMap<>();
    try {
      String fieldsStr = StringUtils.join(fields, ",");
      String baseContentreadUrl = ProjectUtil.getConfigValue(JsonKey.EKSTEP_BASE_URL) + "/content/v3/read/" + courseId + "?fields=" + fieldsStr;
      headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
      headers.put(JsonKey.AUTHORIZATION, PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION));

      logger.info(null, "making call for content read ==" + courseId);
      String response = HttpUtil.sendGetRequest(baseContentreadUrl, headers);

      logger.info(null, "Content read response", null, new HashMap<>(){{put("response", response);}});
      Map<String, Object> data = mapper.readValue(response, Map.class);
      if (MapUtils.isNotEmpty(data)) {
        data = (Map<String, Object>) data.get(JsonKey.RESULT);
        if (MapUtils.isNotEmpty(data)) {
          Object content = data.get(JsonKey.CONTENT);
          resMap.put(JsonKey.CONTENT, content);
        }else {
          logger.info(null, "EkStepRequestUtil:searchContent No data found");
        }
      } else {
        logger.info(null, "EkStepRequestUtil:searchContent No data found");
      }
    } catch (IOException e) {
      logger.error(null, "Error found during content search parse==" + e.getMessage(), e);
    } catch (UnirestException e) {
      logger.error(null, "Error found during content search parse==" + e.getMessage(), e);
    }
    return resMap;
  }


  public static Map<String, Object> getCourseObjectFromEkStep(
          String courseId, Map<String, String> headers) {
    logger.info(null, "Requested course id is ==" + courseId);
    if (!StringUtils.isBlank(courseId)) {
      try {
        String query = EKSTEP_COURSE_SEARCH_QUERY.replaceAll("COURSE_ID_PLACEHOLDER", courseId);
        Map<String, Object> result = ContentUtil.searchContent(query, headers);
        if (null != result && !result.isEmpty() && result.get(JsonKey.CONTENTS) != null) {
          return ((List<Map<String, Object>>) result.get(JsonKey.CONTENTS)).get(0);
          // return (Map<String, Object>) contentObject;
        } else {
          logger.info(null,
                  "CourseEnrollmentActor:getCourseObjectFromEkStep: Content not found for requested courseId "
                          + courseId);
        }
      } catch (Exception e) {
        logger.error(null, e.getMessage(), e);
      }
    }
    return null;
  }

  public static boolean updateCollection(RequestContext requestContext, String collectionId, Map<String, Object> data) {
    String response = "";
    try {
      String contentUpdateBaseUrl = ProjectUtil.getConfigValue(JsonKey.LEARNING_SERVICE_BASE_URL);
      Request request = new Request();
      request.put("content", data);
      response =
              HttpUtil.sendPatchRequest(
                      contentUpdateBaseUrl
                              + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_UPDATE_URL)
                              + collectionId, JsonUtil.serialize(request),
                      headerMap);
    } catch (Exception e) {
      logger.error(requestContext, "Error while doing system update to collection " + e.getMessage(), e);
    }
    return JsonKey.SUCCESS.equalsIgnoreCase(response);
  }
}
