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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;

/** @author Manzarul */
public class HealthController extends BaseController {
  private static List<String> list = new ArrayList<>();

  @Inject
  @Named("health-actor")
  private ActorRef healthActorRef;

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
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.HEALTH_CHECK.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      return actorResponseHandler(healthActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      e.printStackTrace();
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will do the health check for play service.
   *
   * @return CompletionStage<Result>
   */
  public CompletionStage<Result> getServiceHealth(Http.Request httpRequest) {
   logger.debug(null, "Call to get play service health for service.");
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
    response.setTs(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
    return CompletableFuture.completedFuture(ok(play.libs.Json.toJson(response)));
  }

}
