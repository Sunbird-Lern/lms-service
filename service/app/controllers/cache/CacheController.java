package controllers.cache;

import org.apache.pekko.actor.ActorRef;
import controllers.BaseController;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.keys.JsonKey;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class CacheController extends BaseController {

  @Inject
  @Named("cache-management-actor")
  private ActorRef actorRef;

  public CompletionStage<Result> clearCache(String mapName, Http.Request httpRequest) {
    return handleRequest(
        actorRef, ActorOperations.CLEAR_CACHE.getValue(), mapName, JsonKey.MAP_NAME, httpRequest);
  }
}
