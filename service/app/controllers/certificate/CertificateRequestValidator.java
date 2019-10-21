package controllers.certificate;

import java.text.MessageFormat;
import java.util.Map;
import org.sunbird.common.exception.ProjectCommonException;
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
        (String) certRequestDto.getRequest().get(JsonKey.NAME),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.NAME);
    if (!certRequestDto.getRequest().containsKey(CourseJsonKey.TEMPLATE)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), CourseJsonKey.TEMPLATE),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    } else if (!(certRequestDto.getRequest().get(CourseJsonKey.TEMPLATE) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.TEMPLATE, "object"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    if (certRequestDto.getRequest().containsKey(JsonKey.FILTERS)
        && !(certRequestDto.getRequest().get(JsonKey.FILTERS) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), JsonKey.FILTERS, "object"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  public void validateGetCertificateListRequest(Request certRequestDto) {
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID);
  }

  public void validateDeleteCertificateRequest(Request certRequestDto) {
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID);
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.NAME),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.NAME);
  }
}
