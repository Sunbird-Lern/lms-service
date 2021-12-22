package controllers.enrollment.validator;

import org.apache.commons.collections.CollectionUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

import java.util.List;

public class CourseEnrollmentRequestValidator extends BaseRequestValidator {

  public CourseEnrollmentRequestValidator() {}

  public void validateEnrollCourse(Request courseRequestDto) {
    commonValidations(courseRequestDto);
  }

  public void validateUnenrollCourse(Request courseRequestDto) {
    commonValidations(courseRequestDto);
  }

  private void commonValidations(Request courseRequestDto) {
    validateParam(
        (String) courseRequestDto.getRequest().get(JsonKey.COURSE_ID),
        ResponseCode.mandatoryParamsMissing,
            JsonKey.COURSE_ID+"/"+JsonKey.ENROLLABLE_ITEM_ID+"/"+JsonKey.COLLECTION_ID);
    validateParam(
        (String) courseRequestDto.getRequest().get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
            JsonKey.BATCH_ID+"/"+JsonKey.FIXED_BATCH_ID);
    validateParam(
        (String) courseRequestDto.getRequest().get(JsonKey.USER_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.USER_ID);
  }

  public void validateCourseParticipant(Request courseRequestDto) {
    validateParam(
            (String) courseRequestDto.getRequest().get(JsonKey.COURSE_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.COURSE_ID+"/"+JsonKey.ENROLLABLE_ITEM_ID+"/"+JsonKey.COLLECTION_ID);
    validateParam(
            (String) courseRequestDto.getRequest().get(JsonKey.BATCH_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.BATCH_ID+"/"+JsonKey.FIXED_BATCH_ID);
  }

  public void validateEnrolledCourse(Request courseRequestDto) {
    validateParam(
        (String) courseRequestDto.getRequest().get(JsonKey.BATCH_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.BATCH_ID);
    validateParam(
        (String) courseRequestDto.getRequest().get(JsonKey.USER_ID),
        ResponseCode.mandatoryParamsMissing,
        JsonKey.USER_ID);
  }

  public void validateUserEnrolledCourse(Request courseRequestDto) {
    validateParam(
            (String) courseRequestDto.get(JsonKey.USER_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.USER_ID);
  }

  /**
   * Validates the attendance request DTO for batchId and contentId
   *
   * @param attendanceRequestDto
   */
  public void validateGetAttendance(Request attendanceRequestDto) {
    validateParam(
            (String) attendanceRequestDto.getRequest().get(JsonKey.BATCH_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.BATCH_ID);
    validateParam(
            (String) attendanceRequestDto.getRequest().get(JsonKey.CONTENT_ID),
            ResponseCode.mandatoryParamsMissing,
            JsonKey.CONTENT_ID);
  }

  /**
   * Validates the param for create attendance
   *
   * @param onlineProvider the online provider
   */
  public void validateCreateAttendance(String onlineProvider) {
    validateParam(onlineProvider, ResponseCode.mandatoryParamsMissing, JsonKey.ONLINE_PROVIDER);
  }

  /**
   * Validates the param for summary reports
   *
   * @param request the request
   */
  public void validateSummaryReport(Request request) {
    if (request.getRequest().containsKey(JsonKey.ORGANISATION_IDS)
            && !(request.getRequest().get(JsonKey.ORGANISATION_IDS) instanceof List)) {
      throw new ProjectCommonException(
              ResponseCode.dataTypeError.getErrorCode(),
              ResponseCode.dataTypeError.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
    } else if (CollectionUtils.isNotEmpty((List<String>) request.getRequest().get(JsonKey.ORGANISATION_IDS))) {
      validateListValues((List<String>) request.getRequest().get(JsonKey.ORGANISATION_IDS), JsonKey.ORGANISATION_IDS);
    } else {
      throw new ProjectCommonException(
              ResponseCode.invalidParameterValue.getErrorCode(),
              ResponseCode.invalidParameterValue.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
