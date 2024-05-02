package controllers.certificate;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.constants.CourseJsonKey;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

public class CertificateRequestValidator extends BaseRequestValidator {

  public void validateIssueCertificateRequest(Request certRequestDto) {
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID+"/"+JsonKey.COLLECTION_ID);
    validateParam(
        (String) certRequestDto.getRequest().get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.BATCH_ID);
  }

  public void validateAddCertificateRequest(Request certRequestDto) {
    Map<String, Object> batch =
        (Map<String, Object>) certRequestDto.getRequest().get(JsonKey.BATCH);
    validateParam(
        (String) batch.get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID);
    validateParam(
        (String) batch.get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.BATCH_ID);
    if (!batch.containsKey(CourseJsonKey.TEMPLATE)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), CourseJsonKey.TEMPLATE),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (!(batch.get(CourseJsonKey.TEMPLATE) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.TEMPLATE, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    Map<String, Object> template = (Map<String, Object>) batch.get(CourseJsonKey.TEMPLATE);
    validateParam(
        (String) template.get(JsonKey.IDENTIFIER),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.IDENTIFIER);
    validateTemplateCriteria(template);
    if (template.containsKey(CourseJsonKey.ISSUER)
        && !(template.get(CourseJsonKey.ISSUER) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.ISSUER, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (template.containsKey(CourseJsonKey.SIGNATORY_LIST)
        && !(template.get(CourseJsonKey.SIGNATORY_LIST) instanceof List)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.SIGNATORY_LIST, "List"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (template.containsKey(CourseJsonKey.SIGNATORY_LIST)) {
      List<Object> signatoryList = (List<Object>) template.get(CourseJsonKey.SIGNATORY_LIST);
      signatoryList.forEach(signatory->{
        Map<String, String> data = (Map<String, String>) signatory;
        if(MapUtils.isNotEmpty(data)){
          CourseJsonKey.SIGNATORY_LIST_ATTRIBUTES.forEach(attr->{
            if (StringUtils.isBlank(data.get(attr))){
              ProjectCommonException.throwClientErrorException(
                      ResponseCode.invalidData,
                      "Signatory list missing attribute" );
            }
          });
        }
      });
    }
    if (template.containsKey(CourseJsonKey.ADDITIONAL_PROPS)
        && !(template.get(CourseJsonKey.ADDITIONAL_PROPS) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.ADDITIONAL_PROPS, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  public void validateDeleteCertificateRequest(Request certRequestDto) {
    Map<String, Object> batch =
        (Map<String, Object>) certRequestDto.getRequest().get(JsonKey.BATCH);
    validateParam(
        (String) batch.get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.COURSE_ID);
    validateParam(
        (String) batch.get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.BATCH_ID);
    if (!batch.containsKey(CourseJsonKey.TEMPLATE)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), CourseJsonKey.TEMPLATE),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (!(batch.get(CourseJsonKey.TEMPLATE) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.TEMPLATE, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    Map<String, Object> template = (Map<String, Object>) batch.get(CourseJsonKey.TEMPLATE);
    validateParam(
        (String) template.get(JsonKey.IDENTIFIER),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.IDENTIFIER);
  }

  public void validateTemplateCriteria(Map<String, Object> template) {
    if (!template.containsKey(JsonKey.CRITERIA)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.CRITERIA),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (!(template.get(JsonKey.CRITERIA) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), JsonKey.CRITERIA, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    Map<String, Object> criteria = (Map<String, Object>) template.get(JsonKey.CRITERIA);
    if (!(criteria.containsKey(CourseJsonKey.ENROLLMENT))
        && !(criteria.containsKey(JsonKey.ASSESSMENT))) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), "Enrollment or Status"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (criteria.containsKey(CourseJsonKey.ENROLLMENT)
        && !(criteria.get(CourseJsonKey.ENROLLMENT) instanceof Map)) {
      throw new ProjectCommonException(
          ResponseCode.dataTypeError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.ENROLLMENT, "Map"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (criteria.containsKey(CourseJsonKey.ENROLLMENT)
        && !((Map<String, Object>) criteria.get(CourseJsonKey.ENROLLMENT))
            .containsKey(JsonKey.STATUS)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          MessageFormat.format(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.STATUS),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (criteria.containsKey(CourseJsonKey.ENROLLMENT)
        && ((Map<String, Object>) criteria.get(CourseJsonKey.ENROLLMENT)).size() > 1) {
      throw new ProjectCommonException(
          ResponseCode.invalidPropertyError.getErrorCode(),
          MessageFormat.format(
              ResponseCode.invalidPropertyError.getErrorMessage(), "for Enrollment"),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (criteria.containsKey(JsonKey.ASSESSMENT)) {
      if (!(criteria.get(JsonKey.ASSESSMENT) instanceof Map)) {
        throw new ProjectCommonException(
            ResponseCode.dataTypeError.getErrorCode(),
            MessageFormat.format(
                ResponseCode.dataTypeError.getErrorMessage(), JsonKey.ASSESSMENT, "Map"),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      Map<String, Object> assessment = (Map<String, Object>) criteria.get(JsonKey.ASSESSMENT);
      if (!assessment.containsKey(CourseJsonKey.SCORE)) {
        throw new ProjectCommonException(
            ResponseCode.mandatoryParamsMissing.getErrorCode(),
            MessageFormat.format(
                ResponseCode.mandatoryParamsMissing.getErrorMessage(), CourseJsonKey.SCORE),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      if (assessment.size() > 1) {
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            MessageFormat.format(
                ResponseCode.invalidPropertyError.getErrorMessage(), "for Assessment"),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      if (!(assessment.get(CourseJsonKey.SCORE) instanceof Map)) {
        throw new ProjectCommonException(
            ResponseCode.dataTypeError.getErrorCode(),
            MessageFormat.format(
                ResponseCode.dataTypeError.getErrorMessage(), CourseJsonKey.SCORE, "Map"),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      Map<String, Object> score = (Map<String, Object>) assessment.get(CourseJsonKey.SCORE);
      if (!(score.containsKey(">=") || score.containsKey("<=") || score.containsKey("eq"))) {
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            MessageFormat.format(ResponseCode.invalidPropertyError.getErrorMessage(), "for Score"),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
    }
    if (criteria.containsKey(JsonKey.USER)
            && !(criteria.get(JsonKey.USER) instanceof Map)) {
      throw new ProjectCommonException(
              ResponseCode.dataTypeError.getErrorCode(),
              MessageFormat.format(
                      ResponseCode.dataTypeError.getErrorMessage(), JsonKey.USER, "Map"),
              ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    if (criteria.containsKey(JsonKey.USER)
            && !((Map<String, Object>) criteria.get(JsonKey.USER))
            .containsKey(JsonKey.ROOT_ORG_ID)) {
      throw new ProjectCommonException(
              ResponseCode.mandatoryParamsMissing.getErrorCode(),
              MessageFormat.format(
                      ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.ROOT_ORG_ID),
              ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
