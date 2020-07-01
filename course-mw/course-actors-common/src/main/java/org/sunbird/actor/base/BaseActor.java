package org.sunbird.actor.base;

import akka.actor.UntypedAbstractActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

public abstract class BaseActor extends UntypedAbstractActor {

  public abstract void onReceive(Request request) throws Throwable;

  @Override
  public void onReceive(Object message) throws Throwable {
    if (message instanceof Request) {
      Request request = (Request) message;
      String operation = request.getOperation();
      ProjectLogger.log("BaseActor: onReceive called for operation: " + operation, LoggerEnum.INFO);
      try {
        onReceive(request);
      } catch (Exception e) {
        e.printStackTrace();
        onReceiveException(operation, e);
      }
    } else {
      // Do nothing !
    }
  }

  protected void onReceiveException(String callerName, Exception exception) throws Exception {
    ProjectLogger.log(
        "Exception in message processing for: "
            + callerName
            + " :: message: "
            + exception.getMessage(),
        exception);
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
    ProjectLogger.log(callerName + ": unsupported message");
    unSupportedMessage();
  }

  public Response successResponse() {
    Response response = new Response();
    response.put("response", "SUCCESS");
    return response;
  }
}
