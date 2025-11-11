/** */
package controllers.search;

import org.apache.pekko.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
     logger.info(null, "making a call to data synch api = " + requestData);
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateSyncRequest(reqObj);
      String operation = (String) reqObj.getRequest().get(JsonKey.OPERATION_FOR);
      reqObj.setOperation(ActorOperations.SYNC.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
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
