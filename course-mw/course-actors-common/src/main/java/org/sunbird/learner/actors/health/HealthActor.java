/** */
package org.sunbird.learner.actors.health;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.Util;
import scala.concurrent.Future;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** @author Manzarul */
public class HealthActor extends BaseActor {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private Util.DbInfo pagesDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_MGMT_DB);
  private ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);
  private static final String LMS_SERVICE = "lms-service";
  public LoggerUtil logger = new LoggerUtil(this.getClass());

  @Override
  public void onReceive(Request message) throws Throwable {
    if (message instanceof Request) {
      try {
        Request actorMessage = message;
        Util.initializeContext(actorMessage, TelemetryEnvKey.USER, this.getClass().getName());

        // set request id fto thread loacl...
        if (actorMessage.getOperation().equalsIgnoreCase(ActorOperations.HEALTH_CHECK.getValue())) {
          checkAllComponentHealth();
        } else if (actorMessage.getOperation().equalsIgnoreCase(ActorOperations.ACTOR.getValue())) {
          actorhealthCheck();
        } else if (actorMessage.getOperation().equalsIgnoreCase(ActorOperations.ES.getValue())) {
          esHealthCheck();
        } else if (actorMessage
            .getOperation()
            .equalsIgnoreCase(ActorOperations.CASSANDRA.getValue())) {
          cassandraHealthCheck();
        } else {
          ProjectCommonException exception =
              new ProjectCommonException(
                  ResponseCode.invalidOperationName.getErrorCode(),
                  ResponseCode.invalidOperationName.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
        }
      } catch (Exception ex) {
        logger.error(null, ex.getMessage(), ex);
        sender().tell(ex, self());
      }
    } else {
      // Throw exception as message body
      ProjectCommonException exception =
          new ProjectCommonException(
              ResponseCode.invalidRequestData.getErrorCode(),
              ResponseCode.invalidRequestData.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
      sender().tell(exception, self());
    }
  }

  /** */
  private void esHealthCheck() {
    // check the elastic search
    boolean isallHealthy = true;
    Map<String, Object> finalResponseMap = new HashMap<>();
    List<Map<String, Object>> responseList = new ArrayList<>();
    responseList.add(ProjectUtil.createCheckResponse(JsonKey.ACTOR_SERVICE, false, null));
    try {
      Future<Boolean> esResponseF = esUtil.healthCheck();
      boolean esResponse = (boolean) ElasticSearchHelper.getResponseFromFuture(esResponseF);

      responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, esResponse, null));
      isallHealthy = esResponse;
    } catch (Exception e) {
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, true, e));
      isallHealthy = false;
      logger.error(null, "Elastic search health Error == ", e);
    }
    finalResponseMap.put(JsonKey.CHECKS, responseList);
    finalResponseMap.put(JsonKey.NAME, "ES health check api");
    if (isallHealthy) {
      finalResponseMap.put(JsonKey.Healthy, true);
    } else {
      finalResponseMap.put(JsonKey.Healthy, false);
    }
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
    sender().tell(response, self());
  }

  /** */
  private void cassandraHealthCheck() {
    Map<String, Object> finalResponseMap = new HashMap<>();
    List<Map<String, Object>> responseList = new ArrayList<>();
    boolean isallHealthy = true;
    responseList.add(ProjectUtil.createCheckResponse(LMS_SERVICE, false, null));
    responseList.add(ProjectUtil.createCheckResponse(JsonKey.ACTOR_SERVICE, false, null));
    try {
      cassandraOperation.getAllRecords(null, pagesDbInfo.getKeySpace(), pagesDbInfo.getTableName());
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, false, null));
    } catch (Exception e) {
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, true, e));
      isallHealthy = false;
    }
    finalResponseMap.put(JsonKey.CHECKS, responseList);
    finalResponseMap.put(JsonKey.NAME, "cassandra health check api");
    if (isallHealthy) {
      finalResponseMap.put(JsonKey.Healthy, true);
    } else {
      finalResponseMap.put(JsonKey.Healthy, false);
    }
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
    sender().tell(response, self());
  }

  /** */
  private void actorhealthCheck() {
    Map<String, Object> finalResponseMap = new HashMap<>();
    List<Map<String, Object>> responseList = new ArrayList<>();
    responseList.add(ProjectUtil.createCheckResponse(LMS_SERVICE, false, null));
    responseList.add(ProjectUtil.createCheckResponse(JsonKey.ACTOR_SERVICE, false, null));
    finalResponseMap.put(JsonKey.CHECKS, responseList);
    finalResponseMap.put(JsonKey.NAME, "Actor health check api");
    finalResponseMap.put(JsonKey.Healthy, true);
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
    sender().tell(response, self());
  }

  /** */
  private void checkAllComponentHealth() {
    boolean isallHealthy = true;
    Map<String, Object> finalResponseMap = new HashMap<>();
    List<Map<String, Object>> responseList = new ArrayList<>();
    responseList.add(ProjectUtil.createCheckResponse(LMS_SERVICE, false, null));
    responseList.add(ProjectUtil.createCheckResponse(JsonKey.ACTOR_SERVICE, false, null));
    try {
//      cassandraOperation.getAllRecords(null, pagesDbInfo.getKeySpace(), pagesDbInfo.getTableName());
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, false, null));
    } catch (Exception e) {
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.CASSANDRA_SERVICE, true, e));
      isallHealthy = false;
    }
    // check the elastic search
    try {
      Future<Boolean> responseF = esUtil.healthCheck();
      boolean response = (boolean) ElasticSearchHelper.getResponseFromFuture(responseF);
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, !response, null));
      isallHealthy = response;
    } catch (Exception e) {
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.ES_SERVICE, true, e));
      isallHealthy = false;
    }
    // check EKStep Util.
    try {
      String body = "{\"request\":{\"filters\":{\"identifier\":\"test\"}}}";
      Map<String, String> headers = new HashMap<>();
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
      String searchBaseUrl = ProjectUtil.getConfigValue(JsonKey.SEARCH_SERVICE_API_BASE_URL);
      String response =
              HttpUtil.sendPostRequest(
                      searchBaseUrl
                              + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
                      body,
                      headers);
      if (response.contains("OK")) {
        responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, false, null));
      } else {
        responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, null));
        isallHealthy = false;
      }
    } catch (Exception e) {
      responseList.add(ProjectUtil.createCheckResponse(JsonKey.EKSTEP_SERVICE, true, null));
      isallHealthy = false;
    }

    finalResponseMap.put(JsonKey.CHECKS, responseList);
    finalResponseMap.put(JsonKey.NAME, "Complete health check api");
    if (isallHealthy) {
      finalResponseMap.put(JsonKey.Healthy, true);
    } else {
      finalResponseMap.put(JsonKey.Healthy, false);
    }
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
    sender().tell(response, self());
  }
}