package controllers.enrollment.validator;

import org.sunbird.common.models.util.*;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

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
}
