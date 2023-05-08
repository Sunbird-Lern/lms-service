package org.sunbird.actor.base;

import akka.actor.UntypedAbstractActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

public abstract class BaseActor extends UntypedAbstractActor {

  public abstract void onReceive(Request request) throws Throwable;
  public LoggerUtil logger = new LoggerUtil(this.getClass());

  @Override
  public void onReceive(Object message) throws Throwable {
    if (message instanceof Request) {
      Request request = (Request) message;
      String operation = request.getOperation();
      logger.debug(request.getRequestContext(), "onReceive called for operation: " + operation);
      try {
        onReceive(request);
      } catch (Exception e) {
        logger.error(request.getRequestContext(), "Error while processing the message : " + operation, e);
        onReceiveException(operation, e);
      }
    } else {
      // Do nothing !
    }
  }

  protected void onReceiveException(String callerName, Exception exception) throws Exception {
    sender().tell(exception, self());
  }

  public void unSupportedMessage() throws Exception {
    ProjectCommonException exception =
        new ProjectCommonException(
            ResponseCode.invalidRequestData.getErrorCode(),
            ResponseCode.invalidRequestData.getErrorMessage(),
            ResponseCode.CLIENT_ERROR.getResponseCode());
    sender().tell(exception, self());
  }

  public void onReceiveUnsupportedOperation(String callerName) throws Exception {
    unSupportedMessage();
  }

  public Response successResponse() {
    Response response = new Response();
    response.put("response", "SUCCESS");
    return response;
  }
  
  public Response clientError(String message) {
    Response response = new Response();
    response.setResponseCode(ResponseCode.CLIENT_ERROR);
    ResponseParams params = new ResponseParams();
    params.setStatus(ResponseParams.StatusType.FAILED.name());
    params.setErr(ResponseCode.invalidRequestData.getErrorCode());
    params.setErrmsg(ResponseCode.invalidRequestData.getErrorMessage());
    response.setParams(params);
    response.put("response", message);
    return response;
  }
}
