package org.sunbird.userorg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpMethod;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.KeycloakRequiredActionLinkUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.sunbird.common.exception.ProjectCommonException.throwServerErrorException;
import static org.sunbird.common.models.util.JsonKey.BEARER;
import static org.sunbird.common.models.util.JsonKey.CONTENT;
import static org.sunbird.common.models.util.JsonKey.FILTERS;
import static org.sunbird.common.models.util.JsonKey.ID;
import static org.sunbird.common.models.util.JsonKey.RESPONSE;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_AUTHORIZATION;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_GET_MULTIPLE_USER_API;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_GET_ORGANISATION_API;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_GET_SINGLE_USER_API;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_USER_ORG_API_BASE_URL;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.errorProcessingRequest;
import static org.sunbird.common.responsecode.ResponseCode.resourceNotFound;
import static org.sunbird.learner.constants.CourseJsonKey.SUNBIRD_SEND_EMAIL_NOTIFICATION_API;

public class UserOrgServiceImpl implements UserOrgService {

  private ObjectMapper mapper = new ObjectMapper();
  private static final String FORWARD_SLASH = "/";
  private static final String X_AUTHENTICATED_USER_TOKEN = "x-authenticated-user-token";
  private LoggerUtil logger = new LoggerUtil(UserOrgServiceImpl.class);

  private static UserOrgService instance = null;

  public static synchronized UserOrgService getInstance() {
    if (instance == null) {
      instance = new UserOrgServiceImpl();
    }
    return instance;
  }

  private UserOrgServiceImpl() {}

  private static Map<String, String> getdefaultHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION, BEARER + getConfigValue(SUNBIRD_AUTHORIZATION));
    headers.put("Content-Type", "application/json");
    return headers;
  }

  private Response getUserOrgResponse(
      String requestAPI,
      HttpMethod requestType,
      Map<String, Object> requestMap,
      Map<String, String> headers) {
    Response response = null;
    String requestUrl = getConfigValue(SUNBIRD_USER_ORG_API_BASE_URL) + requestAPI;
    HttpResponse<String> httpResponse = null;
    String responseBody = null;
    logger.info( null,
        "UserOrgServiceImpl:getResponse:Sending "
            + requestType
            + " Request, Request URL: "
            + requestUrl);
    try {
      String reqBody = mapper.writeValueAsString(requestMap);
      logger.info(null, "UserOrgServiceImpl:getResponse:Sending Request Body=" + reqBody);
      if (HttpMethod.POST.equals(requestType)) {
        httpResponse = Unirest.post(requestUrl).headers(headers).body(reqBody).asString();
      }
      if (HttpMethod.GET.equals(requestType)) {
        httpResponse = Unirest.get(requestUrl).headers(headers).asString();
      }
      logger.info(null, 
          "UserOrgServiceImpl:getResponse Response Status : "
              + (httpResponse != null ? httpResponse.getStatus() : null));
      if (httpResponse == null || StringUtils.isBlank(httpResponse.getBody())) {
        throwServerErrorException(
            ResponseCode.SERVER_ERROR, errorProcessingRequest.getErrorMessage());
      }
      responseBody = httpResponse.getBody();
      response = mapper.readValue(responseBody, Response.class);
      if (response != null && !ResponseCode.OK.equals(response.getResponseCode())) {

        throw new ProjectCommonException(
            response.getResponseCode().name(),
            response.getParams().getErrmsg(),
            response.getResponseCode().getResponseCode());
      }
    } catch (ProjectCommonException e) {
      logger.error(null, 
          "UserOrgServiceImpl:getResponse ProjectCommonException:"
              + requestType
              + "Request , Status : "
              + e.getCode()
              + " "
              + e.getMessage()
              + ",Response Body :"
              + responseBody,e);
      throw e;
    } catch (Exception e) {
      logger.error(null,
          "UserOrgServiceImpl:getResponse:Exception occurred with error message = "
              + e.getMessage()
              + ", Response Body : "
              + responseBody,
          e);
      throwServerErrorException(ResponseCode.SERVER_ERROR);
    }
    return response;
  }

  private Map<String, Object> getRequestMap(Map<String, Object> filterlist) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> request = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, request);
    request.put(FILTERS, filterlist);
    return requestMap;
  }

  @Override
  public Map<String, Object> getOrganisationById(String id) {
    Map<String, Object> filterlist = new HashMap<>();
    filterlist.put(ID, id);
    List<Map<String, Object>> list = getOrganisations(filterlist);
    return !CollectionUtils.isEmpty(list) ? list.get(0) : null;
  }

  @Override
  public List<Map<String, Object>> getOrganisationsByIds(List<String> ids) {
    Map<String, Object> filterlist = new HashMap<>();
    filterlist.put(ID, ids);
    return getOrganisations(filterlist);
  }

  private List<Map<String, Object>> getOrganisations(Map<String, Object> filterlist) {
    Map<String, Object> requestMap = getRequestMap(filterlist);
    Map<String, String> headers = getdefaultHeaders();
    Response response =
        getUserOrgResponse(
            getConfigValue(SUNBIRD_GET_ORGANISATION_API), HttpMethod.POST, requestMap, headers);
    if (response != null) {
      Map<String, Object> orgMap = (Map<String, Object>) response.get(RESPONSE);
      if (orgMap != null) {
        return (List<Map<String, Object>>) orgMap.get(CONTENT);
      }
    }
    return null;
  }

  @Override
  public Map<String, Object> getUserById(String id, String authToken) {
    Map<String, Object> filterlist = new HashMap<>();
    filterlist.put(ID, id);
    Map<String, Object> requestMap = getRequestMap(filterlist);
    Map<String, String> headers = getdefaultHeaders();
    if(StringUtils.isNotBlank(authToken)) {
      headers.put(X_AUTHENTICATED_USER_TOKEN, authToken);
    } else {
      logger.error(null, "authToken is empty for gerUserById for ID: " + id, null);
    }
    String relativeUrl = getConfigValue(SUNBIRD_GET_SINGLE_USER_API) + FORWARD_SLASH + id;
    Response response = getUserOrgResponse(relativeUrl, HttpMethod.GET, requestMap, headers);
    if (response != null && ResponseCode.OK == response.getResponseCode()) {
      return (Map<String, Object>) response.get(RESPONSE);
    } else
    return new HashMap<>();
  }

  @Override
  public List<Map<String, Object>> getUsersByIds(List<String> ids, String authToken) {
    List<CompletableFuture<Map<String, Object>>> futures = ids.stream().map(id -> getUserDetail(id, authToken)).collect(Collectors.toList());
    return futures.stream().map(CompletableFuture::join).filter(map -> MapUtils.isNotEmpty(map)).collect(Collectors.toList());
  }

  @Override
  public void sendEmailNotification(Map<String, Object> request, String authToken) {
    Map<String, String> headers = getdefaultHeaders();
    if(StringUtils.isNotBlank(authToken)) {
      headers.put(X_AUTHENTICATED_USER_TOKEN, authToken);
    } else {
      logger.error(null, "authToken is empty for sendEmailNotification", null);
    }
    Response response =
        getUserOrgResponse(
            getConfigValue(SUNBIRD_SEND_EMAIL_NOTIFICATION_API), HttpMethod.POST, request, headers);
    if (response != null) {
      logger.info(null,
          "UserOrgServiceImpl:sendEmailNotification Response" + response.get(RESPONSE));
    }
  }

  @Override
  public List<Map<String, Object>> getUsers(Map<String, Object> request, String authToken) {
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, request);
    return getUsersResponse(requestMap, authToken);
  }

  private List<Map<String, Object>> getUsersResponse(Map<String, Object> requestMap, String authToken) {
    Map<String, String> headers = getdefaultHeaders();
    if(StringUtils.isNotBlank(authToken)) {
      headers.put(X_AUTHENTICATED_USER_TOKEN, authToken);
    } else {
      logger.error(null, "authToken is empty for getUsersResponse() for request : " + requestMap, null);
    }
    Response response =
        getUserOrgResponse(
            getConfigValue(SUNBIRD_GET_MULTIPLE_USER_API), HttpMethod.POST, requestMap, headers);
    if (response != null) {
      Map<String, Object> orgMap = (Map<String, Object>) response.get(RESPONSE);
      if (orgMap != null) {
        return (List<Map<String, Object>>) orgMap.get(CONTENT);
      }
    }
    return null;
  }

  private CompletableFuture<Map<String, Object>> getUserDetail(String userId, String authToken) {
    return CompletableFuture.supplyAsync(new Supplier<Map<String, Object>>() {
      @Override
      public Map<String, Object> get() {
        return getUserById(userId, authToken);
      }
    });
  }

  /**
   * Gets the users' data containing name, email and userId
   *
   * @param userIds the list of user ids
   * @return The users' data containing name, email and userId
   */
  @Override
  public List<Map<String, Object>> getUsersByIds(List<String> userIds) {
    Map<String, Object> filterList = new HashMap<>();
    filterList.put(JsonKey.USER_ID, userIds);
    Map<String, Object> requestMap = getRequestMap(filterList);
    ((Map<String, Object>) requestMap.get(JsonKey.REQUEST)).put(JsonKey.FIELDS, Arrays.asList(JsonKey.USER_ID, JsonKey.FIRST_NAME, JsonKey.LAST_NAME, JsonKey.EMAIL));
    ((Map<String, Object>) requestMap.get(JsonKey.REQUEST)).put(JsonKey.LIMIT, 500);
    return getUsersResponse(requestMap);
  }

  /**
   * Gets the users' data containing name, email and userId
   *
   * @param requestMap the request map
   * @return The users' data containing name, email and userId
   */
  private List<Map<String, Object>> getUsersResponse(Map<String, Object> requestMap) {
    Map<String, String> headers = getdefaultHeaders();
    Response response = getUserOrgResponse("/private/user/v1/search", HttpMethod.POST, requestMap, headers);
    if (response != null) {
      Map<String, Object> userMap = (Map<String, Object>) response.get(RESPONSE);
      if (userMap != null) {
        return (List<Map<String, Object>>) userMap.get(CONTENT);
      }
    }
    return null;
  }
}
