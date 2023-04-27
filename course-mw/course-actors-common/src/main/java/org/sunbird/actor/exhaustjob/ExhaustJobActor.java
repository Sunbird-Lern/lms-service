package org.sunbird.actor.exhaustjob;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.util.ExhaustAPIUtil;
public class ExhaustJobActor extends BaseActor {
  private ObjectMapper mapper = new ObjectMapper();
  @Override
  public void onReceive(Request request) throws Throwable {
    if (request.getOperation().equalsIgnoreCase(ActorOperations.SUBMIT_JOB_REQUEST.getValue())) {
      submitJobRequest(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.LIST_JOB_REQUEST.getValue())) {
      listJobRequest(request);
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void submitJobRequest(Request request) {
      try{
        logger.info(request.getRequestContext(), "ExhaustJobActor:submitJobRequest: called ");
        ExhaustAPIUtil.submitJobRequest(request.getRequestContext(), request.getRequest(), context().dispatcher());
    } catch (Exception e) {
      logger.error(request.getRequestContext(), "ExhaustJobActor:submitJobRequest: Error occurred = " + e.getMessage(), e);
      sender().tell(e, self());
    }
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }
  private void listJobRequest(Request request) {
    try{
      logger.info(request.getRequestContext(), "ExhaustJobActor:listJobRequest: called ");
      ExhaustAPIUtil.listJobRequest(request.getRequestContext(), (String)request.getRequest().get(JsonKey.TAG), context().dispatcher());
    } catch (Exception e) {
      logger.error(request.getRequestContext(), "ExhaustJobActor:listJobRequest: Error occurred = " + e.getMessage(), e);
    }
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }
}
