package org.sunbird.actor.exhaustjob;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.datasecurity.EncryptionService;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.util.ExhaustAPIUtil;

import java.util.HashMap;
import java.util.Map;

public class ExhaustJobActor extends BaseActor {
  private ObjectMapper mapper = new ObjectMapper();
  private EncryptionService encryptionService =
          org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.getEncryptionServiceInstance(
                  null);
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
    Response res = new Response();
      try{
        logger.info(request.getRequestContext(), "ExhaustJobActor:submitJobRequest: called ");
        Map<String,Object> requestMap = request.getRequest();
        String passwordStr = (String) requestMap.get("encryptionKey");
        try {
          passwordStr = encryptionService.encryptData(passwordStr);
          requestMap.put("encryptionKey", passwordStr);
        } catch (Exception e) {
          ProjectCommonException.throwClientErrorException(
                  ResponseCode.internalError, null);
        }
        Map<String, Object> requestMapNew = new HashMap<>();
        requestMapNew.put(JsonKey.REQUEST, requestMap);
        String queryRequestBody = mapper.writeValueAsString(requestMapNew);

        res = ExhaustAPIUtil.submitJobRequest(request.getRequestContext(), queryRequestBody, getExhaustAPIHeaders(request.getContext()), context().dispatcher());
    } catch (Exception e) {
      logger.error(request.getRequestContext(), "ExhaustJobActor:submitJobRequest: Error occurred = " + e.getMessage(), e);
        ProjectCommonException exception =
                new ProjectCommonException(
                        ResponseCode.SERVER_ERROR.getErrorCode(),
                        ResponseCode.SERVER_ERROR.getErrorMessage(),
                        ResponseCode.SERVER_ERROR.getResponseCode());
        throw exception;
    }
    sender().tell(res, self());
  }

  private Map getExhaustAPIHeaders(Map requestHeader){
    Map headerNew = new HashMap<String,Object>();
    headerNew.put(JsonKey.X_CHANNEL_ID, requestHeader.get(JsonKey.CHANNEL));
    headerNew.put(JsonKey.X_AUTHENTICATED_USERID, requestHeader.get(JsonKey.REQUESTED_BY));
    headerNew.put(JsonKey.X_AUTHENTICATED_USER_TOKEN, requestHeader.get(JsonKey.X_AUTH_TOKEN));
    return headerNew;
  }
  private void listJobRequest(Request request) {
    Response res = new Response();
    try{
      logger.info(request.getRequestContext(), "ExhaustJobActor:listJobRequest: called ");
      res = ExhaustAPIUtil.listJobRequest(request.getRequestContext(), (String)request.getRequest().get(JsonKey.TAG),  getExhaustAPIHeaders(request.getContext()), context().dispatcher());
    } catch (Exception e) {
      logger.error(request.getRequestContext(), "ExhaustJobActor:listJobRequest: Error occurred = " + e.getMessage(), e);
      ProjectCommonException exception =
              new ProjectCommonException(
                      ResponseCode.SERVER_ERROR.getErrorCode(),
                      ResponseCode.SERVER_ERROR.getErrorMessage(),
                      ResponseCode.SERVER_ERROR.getResponseCode());
      throw exception;
    }
    sender().tell(res, self());
  }
}
