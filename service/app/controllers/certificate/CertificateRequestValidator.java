package controllers.certificate;

import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.constants.CourseJsonKey;

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
            (String) certRequestDto.getRequest().get(CourseJsonKey.CERTIFICATE),
            ResponseCode.mandatoryParamsMissing,
            CourseJsonKey.CERTIFICATE);
    validateParam(
            (String) certRequestDto.getRequest().get(CourseJsonKey.TEMPLATE),
            ResponseCode.mandatoryParamsMissing,
            CourseJsonKey.TEMPLATE);
  }

}
