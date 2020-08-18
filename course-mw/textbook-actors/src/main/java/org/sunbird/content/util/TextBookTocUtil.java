package org.sunbird.content.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.responsecode.ResponseCode;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.util.Objects.isNull;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.sunbird.common.exception.ProjectCommonException.throwServerErrorException;
import static org.sunbird.common.models.util.JsonKey.BEARER;
import static org.sunbird.common.models.util.JsonKey.EKSTEP_BASE_URL;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_AUTHORIZATION;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.SERVER_ERROR;
import static org.sunbird.common.responsecode.ResponseCode.errorProcessingRequest;

public class TextBookTocUtil {

  private static ObjectMapper mapper = new ObjectMapper();
  private static LoggerUtil logger=  new LoggerUtil(TextBookTocUtil.class);

  private static Map<String, String> getHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION, BEARER + getConfigValue(SUNBIRD_AUTHORIZATION));
    headers.put("Content-Type", "application/json");
    return headers;
  }

  public static Response getRelatedFrameworkById(String frameworkId) {
    logger.info(null, "TextBookTocUtil::getRelatedFrameworkById: frameworkId = " + frameworkId);
    Map<String, String> requestParams = new HashMap<>();
    requestParams.put("categories", "topic");
    return handleReadRequest(frameworkId, JsonKey.LEARNING_SERVICE_BASE_URL, JsonKey.FRAMEWORK_READ_API_URL, requestParams);
  }

  private static String requestParams(Map<String, String> params) {
    if (null != params) {
      StringBuilder sb = new StringBuilder();
      sb.append("?");
      int i = 0;
      for (Entry param : params.entrySet()) {
        if (i++ > 1) {
          sb.append("&");
        }
        sb.append(param.getKey()).append("=").append(param.getValue());
      }
      return sb.toString();
    } else {
      return "";
    }
  }

  public static Response readContent(String contentId, String url) {
    logger.info(null, "TextBookTocUtil::readContent: contentId = " + contentId);
    Map<String, String> requestParams = new HashMap<>();
    requestParams.put("mode", "edit");
    return handleReadRequest(contentId, url, requestParams);
  }

  private static Response handleReadRequest(
          String id, String basePath, String urlPath, Map<String, String> requestParams) {
    Map<String, String> headers = getHeaders();
    ObjectMapper mapper = new ObjectMapper();
     if (StringUtils.isBlank(basePath))
      basePath = getConfigValue(EKSTEP_BASE_URL);
     else
       basePath = getConfigValue(basePath);

    Response response = null;
    try {
      String requestUrl = basePath + getConfigValue(urlPath) + "/" + id + requestParams(requestParams);
      logger.info(null, "TextBookTocUtil:handleReadRequest: Sending GET Request | TextBook Id: " + id + ", Request URL: " + requestUrl);
      HttpResponse<String> httpResponse = Unirest.get(requestUrl).headers(headers).asString();

      if (StringUtils.isBlank(httpResponse.getBody())) {
        logger.error(null, "TextBookTocUtil:handleReadRequest: Received Empty Response | TextBook Id: " + id + ", Request URL: " + requestUrl, null);
        throwServerErrorException(
                ResponseCode.SERVER_ERROR, errorProcessingRequest.getErrorMessage());
      }
      logger.info(null, "Sized :TextBookTocUtil:handleReadRequest: " + " TextBook Id: " + id + " | Request URL: " + requestUrl + "  | size of response " + httpResponse.getBody().getBytes().length);

      response = mapper.readValue(httpResponse.getBody(), Response.class);
      if (!ResponseCode.OK.equals(response.getResponseCode())) {
        logger.error(null, "TextBookTocUtil:handleReadRequest: Response code is not ok | TextBook Id: " + id + "| Request URL: " + requestUrl, null);
        throw new ProjectCommonException(
                response.getResponseCode().name(),
                response.getParams().getErrmsg(),
                response.getResponseCode().getResponseCode());
      }
    } catch (IOException e) {
      logger.info(null, "TextBookTocUtil:handleReadRequest: Exception occurred with error message = " + e.getMessage(), e);
      throwServerErrorException(ResponseCode.SERVER_ERROR);
    } catch (UnirestException e) {
      logger.info(null, "TextBookTocUtil:handleReadRequest: Exception occurred with error message = " + e.getMessage(), e);
      throwServerErrorException(ResponseCode.SERVER_ERROR);
    }
    return response;
  }

  private static Response handleReadRequest(
      String id, String urlPath, Map<String, String> requestParams) {
    return handleReadRequest(id, null, urlPath, requestParams);
  }

  public static <T> T getObjectFrom(String s, Class<T> clazz) {
    if (StringUtils.isBlank(s)) {
      logger.error(null, "Invalid String cannot be converted to Map.", null);
      throw new ProjectCommonException(
          errorProcessingRequest.getErrorCode(),
          errorProcessingRequest.getErrorMessage(),
          SERVER_ERROR.getResponseCode());
    }

    try {
      return mapper.readValue(s, clazz);
    } catch (IOException e) {
      logger.error(null, "Error Mapping File input Mapping Properties.", e);
      throw new ProjectCommonException(
          errorProcessingRequest.getErrorCode(),
          errorProcessingRequest.getErrorMessage(),
          SERVER_ERROR.getResponseCode());
    }
  }

  public static <T> String serialize(T o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      logger.error(null, "Error Serializing Object To String", e);
      throw new ProjectCommonException(
          errorProcessingRequest.getErrorCode(),
          errorProcessingRequest.getErrorMessage(),
          SERVER_ERROR.getResponseCode());
    }
  }

  public static Object stringify(Object o) {
    if (isNull(o)) return "";
    if (o instanceof List) {
      List l = (List) o;
      if (!l.isEmpty() && l.get(0) instanceof String) {
        return String.join(",", l);
      }
      else if (l.isEmpty()) {
        return "";
      }
    }
    if (o instanceof String[]) {
      String[] l = (String[]) o;
      if (l.length > 0) {
        return String.join(",", l);
      }
      else {
        return "";
      }
    }
    return o;
  }
}
