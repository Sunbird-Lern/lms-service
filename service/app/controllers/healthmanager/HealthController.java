/** */
package controllers.healthmanager;

import akka.actor.ActorRef;
import controllers.BaseController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;
import util.SignalHandler;
import org.apache.log4j.Logger;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.responsecode.ResponseCode;

/** @author Manzarul */
public class HealthController extends BaseController {
  private static List<String> list = new ArrayList<>();
  @Inject
  @Named("health-actor")
  private ActorRef healthActorRef;

  @Inject SignalHandler signalHandler;

  static {
    list.add("service");
    list.add("actor");
    list.add("cassandra");
    list.add("es");
    list.add("ekstep");
  }

  /**
   * This method will do the complete health check
   *
   * @return CompletionStage<Result>
   */
  public CompletionStage<Result> getHealth(Http.Request httpRequest) {
    try {
      handleSigTerm();
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.HEALTH_CHECK.getValue());
      reqObj.setRequestId(httpRequest.flash().get(JsonKey.REQUEST_ID));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      reqObj.setEnv(getEnvironment());
      return actorResponseHandler(healthActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      e.printStackTrace();
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }
  private void handleSigTerm() throws RuntimeException {
    if (signalHandler.isShuttingDown()) {
      ProjectLogger.log( "Application is shutting down, cant accept new request.", LoggerEnum.INFO);
      throw new ProjectCommonException(
              ResponseCode.serviceUnAvailable.getErrorCode(),
              ResponseCode.serviceUnAvailable.getErrorMessage(),
              ResponseCode.SERVICE_UNAVAILABLE.getResponseCode());
    }
  }
  /**
   * This method will do the health check for play service.
   *
   * @return CompletionStage<Result>
   */
  public CompletionStage<Result> getServiceHealth(Http.Request httpRequest) {
    ProjectLogger.log("Call to get play service health for service.", LoggerEnum.INFO.name());
    Map<String, Object> finalResponseMap = new HashMap<>();
    List<Map<String, Object>> responseList = new ArrayList<>();
    responseList.add(ProjectUtil.createCheckResponse(JsonKey.LEARNER_SERVICE, false, null));
    finalResponseMap.put(JsonKey.CHECKS, responseList);
    finalResponseMap.put(JsonKey.NAME, "Learner service health");
    finalResponseMap.put(JsonKey.Healthy, true);
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, finalResponseMap);
    response.setId("learner.service.health.api");
    response.setVer(getApiVersion(httpRequest.path()));
    response.setTs(httpRequest.flash().get(JsonKey.REQUEST_ID));
    try {
      handleSigTerm();
      return CompletableFuture.completedFuture(ok(play.libs.Json.toJson(response)));
    } catch (Exception e) {
      e.printStackTrace();
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

}
