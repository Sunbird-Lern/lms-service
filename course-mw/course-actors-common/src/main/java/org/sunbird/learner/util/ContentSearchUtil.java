package org.sunbird.learner.util;

import org.apache.pekko.dispatch.Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.BaseRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.RestUtil;
import org.sunbird.common.request.RequestContext;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** @author Mahesh Kumar Gangula */
public class ContentSearchUtil {

  private static String contentSearchURL = null;
  private static LoggerUtil logger = new LoggerUtil(ContentSearchUtil.class);
  private static ObjectMapper mapper = new ObjectMapper();

  static {
    String baseUrl = System.getenv(JsonKey.SUNBIRD_API_MGR_BASE_URL);
    String searchPath = System.getenv(JsonKey.SUNBIRD_CS_SEARCH_PATH);
    if (StringUtils.isBlank(searchPath))
      searchPath = PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_CS_SEARCH_PATH);
    contentSearchURL = baseUrl + searchPath;
  }

  private static Map<String, String> getUpdatedHeaders(Map<String, String> headers) {
    if (headers == null) {
      headers = new HashMap<>();
    }
    headers.put(
        HttpHeaders.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.SUNBIRD_AUTHORIZATION));
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    headers.put("Connection", "Keep-Alive");
    return headers;
  }

  /*public static Future<Map<String, Object>> searchContent(
          RequestContext requestContext, String urlQueryString, String queryRequestBody, Map<String, String> headers, ExecutionContextExecutor ec) {
    return searchContent(requestContext,null, queryRequestBody, headers, ec);
  }*/

  public static Future<Map<String, Object>> searchContent( RequestContext requestContext, 
      String urlQueryString,
      String queryRequestBody,
      Map<String, String> headers,
      ExecutionContextExecutor ec) {
    String logMsgPrefix = "searchContent: ";

    Unirest.clearDefaultHeaders();
    String urlString =
        StringUtils.isNotBlank(urlQueryString)
            ? contentSearchURL + urlQueryString
            : contentSearchURL;
    BaseRequest request =
        Unirest.post(urlString).headers(getUpdatedHeaders(headers)).body(queryRequestBody);
    Future<HttpResponse<JsonNode>> response = RestUtil.executeAsync(request);

    return response.map(
        new Mapper<HttpResponse<JsonNode>, Map<String, Object>>() {
          @Override
          public Map<String, Object> apply(HttpResponse<JsonNode> response) {
            try {
              if (RestUtil.isSuccessful(response)) {
                JSONObject result = response.getBody().getObject().getJSONObject("result");
                Map<String, Object> resultMap = jsonToMap(result);
                Object contents = resultMap.get(JsonKey.CONTENT);
                resultMap.remove(JsonKey.CONTENT);
                resultMap.put(JsonKey.CONTENTS, contents);
                String resmsgId = RestUtil.getFromResponse(response, "params.resmsgid");
                String apiId = RestUtil.getFromResponse(response, "id");
                Map<String, Object> param = new HashMap<>();
                param.put(JsonKey.RES_MSG_ID, resmsgId);
                param.put(JsonKey.API_ID, apiId);
                resultMap.put(JsonKey.PARAMS, param);
                return resultMap;
              } else {
                logger.debug(requestContext, logMsgPrefix + "Search content failed. Error response = " + response.getBody());
                return null;
              }
            } catch (Exception e) {
              logger.error(requestContext, logMsgPrefix + "Exception occurred with error message = " + e.getMessage(), e);
              return null;
            }
          }
        },
        ec);
  }

  public static Map<String, Object> searchContentSync(
          RequestContext requestContext, String urlQueryString, String queryRequestBody, Map<String, String> headers) throws UnirestException {
    Unirest.clearDefaultHeaders();
    String urlString =
        StringUtils.isNotBlank(urlQueryString)
            ? contentSearchURL + urlQueryString
            : contentSearchURL;

    HttpResponse<String> searchResponse = Unirest.post(urlString).headers(getUpdatedHeaders(headers)).body(queryRequestBody).asString();
    try {
      if (null != searchResponse && searchResponse.getStatus() == 200) {
        Map<String, Object> responseData = new ObjectMapper().readValue(searchResponse.getBody(), Map.class);
        Map<String, Object> resultMap = (Map<String, Object>) responseData.get(JsonKey.RESULT);
        resultMap.put(JsonKey.CONTENTS, resultMap.get(JsonKey.CONTENT));
        resultMap.put(JsonKey.PARAMS, responseData.get(JsonKey.PARAMS));
        return resultMap;
      } else {
        logger.info(requestContext, "Composite search returned failed response :: " + searchResponse.getStatus());
        return new HashMap<>();
      }
    } catch (Exception e) {
      logger.error(requestContext, "Exception occurred while calling composite search service :: ", e);
      return new HashMap<>();
    }
  }

  public static Map<String, Object> jsonToMap(JSONObject object) throws JSONException {
    Map<String, Object> map = new HashMap<String, Object>();

    Iterator<String> keysItr = object.keys();
    while (keysItr.hasNext()) {
      String key = keysItr.next();
      Object value = object.get(key);

      if (value instanceof JSONArray) {
        value = toList((JSONArray) value);
      } else if (value instanceof JSONObject) {
        value = jsonToMap((JSONObject) value);
      }
      if (value == JSONObject.NULL) {
        value = null;
      }
      map.put(key, value);
    }
    return map;
  }

  public static List<Object> toList(JSONArray array) throws JSONException {
    List<Object> list = new ArrayList<Object>();
    for (int i = 0; i < array.length(); i++) {
      Object value = array.get(i);
      if (value instanceof JSONArray) {
        value = toList((JSONArray) value);
      } else if (value instanceof JSONObject) {
        value = jsonToMap((JSONObject) value);
      }
      list.add(value);
    }
    return list;
  }
}
