package org.sunbird.learner.actors.cache;

import org.sunbird.actor.base.BaseActor;
import org.sunbird.cache.CacheFactory;
import org.sunbird.cache.interfaces.Cache;
import org.sunbird.response.Response;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerEnum;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.logging.ProjectLogger;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;

public class CacheManagementActor extends BaseActor {
  private Cache cache = CacheFactory.getInstance();

  @Override
  public void onReceive(Request request) throws Throwable {
    System.out.println(
        "Actor dispatcher parent=>" + getContext().getParent().path() + ", self=>" + self().path());
    if (request.getOperation().equalsIgnoreCase(ActorOperations.CLEAR_CACHE.getValue())) {
      clearCache(request);
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void clearCache(Request request) {
    String mapName = (String) request.getContext().get(JsonKey.MAP_NAME);
    logger.info(request.getRequestContext(), "CacheManagementActor:clearCache: mapName = " + mapName);
    try {
      if (!JsonKey.ALL.equals(mapName)) {
        cache.clear(mapName);
      } else {
        cache.clearAll();
      }

      Response response = new Response();
      response.setResponseCode(ResponseCode.success);

      sender().tell(response, self());
    } catch (Exception e) {
      logger.error(request.getRequestContext(), "CacheManagementActor:clearCache: Error occurred for mapName = "
              + mapName + " error = " + e.getMessage(), e);
      sender().tell(e, self());
    }
  }
}
