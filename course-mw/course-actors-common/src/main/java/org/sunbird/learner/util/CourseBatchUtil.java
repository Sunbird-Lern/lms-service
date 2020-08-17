package org.sunbird.learner.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.constants.CourseJsonKey;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.Map;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.sunbird.common.exception.ProjectCommonException.throwClientErrorException;
import static org.sunbird.common.exception.ProjectCommonException.throwServerErrorException;
import static org.sunbird.common.models.util.JsonKey.BEARER;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_AUTHORIZATION;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.errorProcessingRequest;

public class CourseBatchUtil {
  private static ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);
  private static ObjectMapper mapper = new ObjectMapper();
  private static LoggerUtil logger = new LoggerUtil(CourseBatchUtil.class);

  private CourseBatchUtil() {}

  public static void syncCourseBatchForeground(RequestContext requestContext, String uniqueId, Map<String, Object> req) {
    logger.info(requestContext, "CourseBatchManagementActor: syncCourseBatchForeground called for course batch ID = "
            + uniqueId);
    req.put(JsonKey.ID, uniqueId);
    req.put(JsonKey.IDENTIFIER, uniqueId);
    Future<String> esResponseF =
        esUtil.save(requestContext, ProjectUtil.EsType.courseBatch.getTypeName(), uniqueId, req);
    String esResponse = (String) ElasticSearchHelper.getResponseFromFuture(esResponseF);
    logger.info(requestContext, "CourseBatchManagementActor::syncCourseBatchForeground: Sync response for course batch ID = "
            + uniqueId
            + " received response = "
            + esResponse);
  }

  public static Map<String, Object> validateCourseBatch(RequestContext requestContext, String courseId, String batchId) {
    Future<Map<String, Object>> resultF =
        esUtil.getDataByIdentifier(requestContext, EsType.courseBatch.getTypeName(), batchId);
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (MapUtils.isEmpty(result)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "No such batchId exists");
    }
    if (StringUtils.isNotBlank(courseId)
        && !StringUtils.equals(courseId, (String) result.get(JsonKey.COURSE_ID))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "batchId is not linked with courseId");
    }
    return result;
  }

  public static Map<String, Object> validateTemplate(RequestContext requestContext, String templateId) {
    Response templateResponse = getTemplate(requestContext, templateId);
    if (templateResponse == null
        || MapUtils.isEmpty(templateResponse.getResult())
        || !templateResponse.getResult().containsKey(CourseJsonKey.CERTIFICATE)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "Invalid template Id: " + templateId);
    }
    Map<String, Object> template =
        (Map<String, Object>)
            ((Map<String, Object>) templateResponse.getResult().get(CourseJsonKey.CERTIFICATE))
                .get("template");
    if (MapUtils.isEmpty(template) || !templateId.equals(template.get(JsonKey.IDENTIFIER))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "Invalid template Id: " + templateId);
    }
    return template;
  }

  private static Response getTemplate(RequestContext requestContext, String templateId) {
    String certServiceBaseUrl = ProjectUtil.getConfigValue("sunbird_cert_service_base_url");
    String templateRelativeUrl = ProjectUtil.getConfigValue("sunbird_cert_template_url");
    Response response = null;
    HttpResponse<String> httpResponse = null;
    String responseBody = null;
    try {
      String certTempUrl = certServiceBaseUrl + templateRelativeUrl + "/" + templateId;
      logger.info(requestContext,"CourseBatchUtil:getTemplate certTempUrl : " + certTempUrl);
      httpResponse = Unirest.get(certTempUrl).headers(getdefaultHeaders()).asString();
      logger.info(requestContext,"CourseBatchUtil:getResponse Response Status : " + httpResponse.getStatus());

      if (httpResponse.getStatus() == 404)
        throwClientErrorException(
            ResponseCode.RESOURCE_NOT_FOUND, "Given cert template not found: " + templateId);

      if (StringUtils.isBlank(httpResponse.getBody())) {
        throwServerErrorException(
            ResponseCode.SERVER_ERROR, errorProcessingRequest.getErrorMessage());
      }
      responseBody = httpResponse.getBody();
      response = mapper.readValue(responseBody, Response.class);
      if (!ResponseCode.OK.equals(response.getResponseCode())) {
        throw new ProjectCommonException(
            response.getResponseCode().name(),
            response.getParams().getErrmsg(),
            response.getResponseCode().getResponseCode());
      }
    } catch (ProjectCommonException e) {
      logger.error(requestContext, 
          "CourseBatchUtil:getResponse ProjectCommonException:"
              + "Request , Status : "
              + e.getCode()
              + " "
              + e.getMessage()
              + ",Response Body :"
              + responseBody, e);
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      logger.error(requestContext, 
          "CourseBatchUtil:getResponse occurred with error message = "
              + e.getMessage()
              + ", Response Body : "
              + responseBody,
          e);
      throwServerErrorException(
          ResponseCode.SERVER_ERROR, "Exception while validating template with cert service");
    }
    return response;
  }

  private static Map<String, String> getdefaultHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION, BEARER + getConfigValue(SUNBIRD_AUTHORIZATION));
    headers.put("Content-Type", "application/json");
    return headers;
  }
}
