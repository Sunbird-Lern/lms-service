/** */
package controllers.search;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import play.mvc.Http;
import play.mvc.Result;

/**
 * This controller will handle all the request related user and organization search.
 *
 * @author Manzarul
 */
public class SearchController extends BaseController {

  @Inject
  @Named("es-sync-actor")
  private ActorRef esSyncActorRef;

  /**
   * This method will do data Sync form Cassandra db to Elasticsearch.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> sync(Http.Request httpRequest) {
    try {
      JsonNode requestData = httpRequest.body().asJson();
      ProjectLogger.log("making a call to data synch api = " + requestData, LoggerEnum.INFO.name());
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateSyncRequest(reqObj);
      String operation = (String) reqObj.getRequest().get(JsonKey.OPERATION_FOR);
      reqObj.setOperation(ActorOperations.SYNC.getValue());
      reqObj.setRequestId(httpRequest.flash().get(JsonKey.REQUEST_ID));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      reqObj.setEnv(getEnvironment());
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.DATA, reqObj.getRequest());
      reqObj.setRequest(map);
      return actorResponseHandler(esSyncActorRef, reqObj, timeout, null, httpRequest);

    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }
}
