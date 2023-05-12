package controllers.exhaustjob.validator;


import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

public class ExhaustJobRequestValidator extends BaseRequestValidator {
  private static final int ERROR_CODE = ResponseCode.CLIENT_ERROR.getResponseCode();

  public void validateCreateExhaustJobRequest(Request request) {

    validateParam(
        (String) request.getRequest().get(JsonKey.TAG),
        ResponseCode.mandatoryParamsMissing, JsonKey.TAG);
    validateParam(
            (String) request.getRequest().get(JsonKey.DATASET),
            ResponseCode.mandatoryParamsMissing, JsonKey.DATASET);
    if(StringUtils.isBlank((String)request.getRequest().get(JsonKey.REQUESTED_BY))){
      request.getRequest().put(JsonKey.REQUESTED_BY,request.getContext().get(JsonKey.REQUESTED_BY));
    }

  }

}
