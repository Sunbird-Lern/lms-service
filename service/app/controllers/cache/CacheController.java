package controllers.cache;

import akka.actor.ActorRef;
import controllers.BaseController;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import play.mvc.Http;
import play.mvc.Result;

public class CacheController extends BaseController {

  private ActorRef actorRef;

  @Inject
  public CacheController(@Named("cache-management-actor") ActorRef actorRef) {
    this.actorRef = actorRef;
  }

  public CompletionStage<Result> clearCache(String mapName, Http.Request httpRequest) {
    return handleRequest(
        actorRef, ActorOperations.CLEAR_CACHE.getValue(), mapName, JsonKey.MAP_NAME, httpRequest);
  }
}
