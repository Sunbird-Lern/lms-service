/** */
package org.sunbird.common.models.util;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections4.MapUtils;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.responsecode.ResponseCode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This utility method will handle external http call
 *
 * @author Manzarul
 */
public class HttpUtil {

  public static LoggerUtil logger = new LoggerUtil(HttpUtil.class);
  private HttpUtil() {}

  /**
   * Makes an HTTP request using GET method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param headers the Map <String,String>
   * @return An String object
   * @throws IOException thrown if any I/O error occurred
   */
  public static String sendGetRequest(String requestURL, Map<String, String> headers)
          throws UnirestException {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.get(requestURL).headers(headers).asString();
    if(200 == httpResponse.getStatus()) {
      long stopTime = System.currentTimeMillis();
      long elapsedTime = stopTime - startTime;
      logger.info(null, "Time taken to execute the request for Url : " + requestURL + " is: " + elapsedTime);
      return httpResponse.getBody();
    } else {
      logger.error(null, "Error while calling request: " + requestURL + " :: response " + httpResponse.getStatus() + " :: " + httpResponse.getBody(), null);
      return "";
    }
  }

  /**
   * Makes an HTTP request using POST method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param params A map containing POST data in form of key-value pairs
   * @return String
   * @throws IOException thrown if any I/O error occurred
   */
  public static String sendPostRequest(
      String requestURL, Map<String, String> params, Map<String, String> headers)
      throws Exception {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
    String str = httpResponse.getBody();
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info( null,
        "HttpUtil sendPostRequest method end at =="
            + stopTime
            + " for requestURL "
            + requestURL
            + " ,Total time elapsed = "
            + elapsedTime);
    return str;
  }


  /**
   * Makes an HTTP request using POST method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param params A map containing POST data in form of key-value pairs
   * @return An HttpURLConnection object
   * @throws IOException thrown if any I/O error occurred
   */
  public static String sendPostRequest(
      String requestURL, String params, Map<String, String> headers) throws Exception {
    long startTime = System.currentTimeMillis();
    HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
    String str = httpResponse.getBody();
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info( null,
        "HttpUtil sendPostRequest method end at =="
            + stopTime
            + " for requestURL "
            + requestURL
            + " ,Total time elapsed = "
            + elapsedTime);
    return str;
  }


  /**
   * Makes an HTTP request using POST method to the specified URL and in response it will return Map
   * of status code with post response in String format.
   *
   * @param requestURL the URL of the remote server
   * @param params A map containing POST data in form of key-value pairs
   * @return HttpUtilResponse
   * @throws IOException thrown if any I/O error occurred
   */
  public static HttpUtilResponse doPostRequest(
      String requestURL, String params, Map<String, String> headers) throws IOException {
    long startTime = System.currentTimeMillis();
    HttpUtilResponse response = new HttpUtilResponse();
    try {
      HttpResponse<String> httpResponse = Unirest.post(requestURL).headers(headers).body(params).asString();
      response = new HttpUtilResponse(httpResponse.getBody(), httpResponse.getStatus());
    } catch (Exception ex) {
      logger.error(null, "Exception occurred while reading body of POST call response : " , ex);
    }
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info(null, 
        "HttpUtil doPostRequest method end at =="
            + stopTime
            + " for requestURL "
            + requestURL
            + " ,Total time elapsed = "
            + elapsedTime);
    return response;
  }

  /**
   * Makes an HTTP request using PATCH method to the specified URL.
   *
   * @param requestURL the URL of the remote server
   * @param params A map containing POST data in form of key-value pairs
   * @return An HttpURLConnection object
   * @throws IOException thrown if any I/O error occurred
   */
  public static String sendPatchRequest(
      String requestURL, String params, Map<String, String> headers) {
    long startTime = System.currentTimeMillis();
    logger.info(null, 
        "HttpUtil sendPatchRequest method started at =="
            + startTime
            + " for requestURL and params "
            + requestURL
            + " param=="
            + params);

    try {
      HttpResponse<String> httpResponse = Unirest.patch(requestURL).headers(headers).body(params).asString();
      
      if (ResponseCode.OK.getResponseCode() == httpResponse.getStatus()) {
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        logger.info(null,
                "HttpUtil sendPatchRequest method end at =="
                + stopTime
                + " for requestURL "
                + requestURL
                + " ,Total time elapsed = "
                + elapsedTime);
        return ResponseCode.success.getErrorCode();
      }
      long stopTime = System.currentTimeMillis();
      long elapsedTime = stopTime - startTime;
      logger.info(null,
              "Patch request failure status code =="
              + httpResponse.getStatus()
              + stopTime
              + " for requestURL "
              + requestURL
              + " ,Total time elapsed = "
              + elapsedTime);
      return "Failure";
    } catch (Exception e) {
      logger.error(null, "HttpUtil call fails == " + e.getMessage(), e);
    }
    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    logger.info( null, 
        "HttpUtil sendPatchRequest method end at =="
            + stopTime
            + " for requestURL "
            + requestURL
            + " ,Total time elapsed = "
            + elapsedTime);
    return "Failure";
  }


  public static Map<String, String> getHeader(Map<String, String> input) throws Exception {
    return new HashMap<String, String>() {
      {
        put("Content-Type", "application/json");
        if (MapUtils.isNotEmpty(input)) putAll(input);
      }
    };
  }
}