package org.sunbird.userorg;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.sunbird.common.exception.ProjectCommonException.throwServerErrorException;
import static org.sunbird.common.models.util.JsonKey.*;
import static org.sunbird.common.models.util.LoggerEnum.ERROR;
import static org.sunbird.common.models.util.LoggerEnum.INFO;
import static org.sunbird.common.models.util.ProjectLogger.log;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.errorProcessingRequest;
import static org.sunbird.learner.constants.CourseJsonKey.SUNBIRD_SEND_EMAIL_NOTIFICATION_API;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpMethod;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.services.sso.SSOManager;
import org.sunbird.services.sso.SSOServiceFactory;

public class UserOrgServiceImpl implements UserOrgService {

  private SSOManager ssoManager = SSOServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  private static final String FORWARD_SLASH = "/";
  private static final String X_AUTHENTICATED_USER_TOKEN = "x-authenticated-user-token";

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
    log(
        "UserOrgServiceImpl:getResponse:Sending "
            + requestType
            + " Request, Request URL: "
            + requestUrl,
        INFO.name());
    try {
      String reqBody = mapper.writeValueAsString(requestMap);
      log("UserOrgServiceImpl:getResponse:Sending Request Body=" + reqBody, INFO.name());
      if (HttpMethod.POST.equals(requestType)) {
        httpResponse = Unirest.post(requestUrl).headers(headers).body(reqBody).asString();
      }
      if (HttpMethod.GET.equals(requestType)) {
        httpResponse = Unirest.get(requestUrl).headers(headers).asString();
      }
      log(
          "UserOrgServiceImpl:getResponse Response Status : "
              + (httpResponse != null ? httpResponse.getStatus() : null),
          ERROR.name());
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
      log(
          "UserOrgServiceImpl:getResponse ProjectCommonException:"
              + requestType
              + "Request , Status : "
              + e.getCode()
              + " "
              + e.getMessage()
              + ",Response Body :"
              + responseBody,
          ERROR.name());
      throw e;
    } catch (Exception e) {
      log(
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
  public Map<String, Object> getUserById(String id) {
    Map<String, Object> filterlist = new HashMap<>();
    filterlist.put(ID, id);
    Map<String, Object> requestMap = getRequestMap(filterlist);
    Map<String, String> headers = getdefaultHeaders();
    headers.put(X_AUTHENTICATED_USER_TOKEN, getAuthenticatedUserToken());
    String relativeUrl = getConfigValue(SUNBIRD_GET_SINGLE_USER_API) + FORWARD_SLASH + id;
    Response response = getUserOrgResponse(relativeUrl, HttpMethod.GET, requestMap, headers);
    if (response != null) {
      return (Map<String, Object>) response.get(RESPONSE);
    }
    return null;
  }

  @Override
  public List<Map<String, Object>> getUsersByIds(List<String> ids) {
    Map<String, Object> filterlist = new HashMap<>();
    filterlist.put(ID, ids);
    Map<String, Object> requestMap = getRequestMap(filterlist);
    return getUsersResponse(requestMap);
  }

  @Override
  public void sendEmailNotification(Map<String, Object> request) {
    Map<String, String> headers = getdefaultHeaders();
    headers.put(X_AUTHENTICATED_USER_TOKEN, getAuthenticatedUserToken());
    Response response =
        getUserOrgResponse(
            getConfigValue(SUNBIRD_SEND_EMAIL_NOTIFICATION_API), HttpMethod.POST, request, headers);
    if (response != null) {
      log(
          "UserOrgServiceImpl:sendEmailNotification Response" + response.get(RESPONSE),
          INFO.name());
    }
  }

  @Override
  public List<Map<String, Object>> getUsers(Map<String, Object> request) {
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.REQUEST, request);
    return getUsersResponse(requestMap);
  }

  private List<Map<String, Object>> getUsersResponse(Map<String, Object> requestMap) {
    Map<String, String> headers = getdefaultHeaders();
    headers.put(X_AUTHENTICATED_USER_TOKEN, getAuthenticatedUserToken());
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

  private String getAuthenticatedUserToken() {
    return ssoManager.login(
        getConfigValue(JsonKey.SUNBIRD_SSO_USERNAME), getConfigValue(JsonKey.SUNBIRD_SSO_PASSWORD));
  }
}
