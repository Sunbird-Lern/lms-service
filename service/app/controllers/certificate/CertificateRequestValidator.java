package controllers.certificate;

import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.constants.CourseJsonKey;

import java.text.MessageFormat;
import java.util.Map;

public class CertificateRequestValidator extends BaseRequestValidator {

  public void validateIssueCertificateRequest(Request certRequestDto) {
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID);
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.BATCH_ID);
    validateParam(
        (String) certRequestDto.getRequest().get("certificate"),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.USER_ID);
  }

  public void validateAddCertificateRequest(Request certRequestDto) {
    validateParam(
            (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.COURSE_ID);
    validateParam(
            (String) certRequestDto.getRequest().get(CourseJsonKey.CERTIFICATE_NAME),
            ResponseCode.mandatoryParamsMissing,
            CourseJsonKey.CERTIFICATE_NAME);
    if(!certRequestDto.getRequest().containsKey(CourseJsonKey.TEMPLATE)){
      throw new ProjectCommonException(ResponseCode.mandatoryParamsMissing.getErrorCode(), MessageFormat.format(ResponseCode.mandatoryParamsMissing.getErrorMessage(), CourseJsonKey.TEMPLATE), ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  public void validateGetCertificateListRequest(Request certRequestDto) {
    validateParam(
            (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.COURSE_ID);
  }

}
