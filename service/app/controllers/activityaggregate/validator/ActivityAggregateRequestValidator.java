package controllers.activityaggregate.validator;

import org.sunbird.exception.ProjectCommonException;
import org.sunbird.keys.JsonKey;
import org.sunbird.response.ResponseCode;
import org.sunbird.request.Request;

import java.util.List;
import java.util.Map;

public class ActivityAggregateRequestValidator {

    public void validateUpdateActivityAggregates(Request request) {
        if (request.getRequest() == null || request.getRequest().isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                ResponseCode.mandatoryParamsMissing.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode()
            );
        }

        String userId = (String) request.get(JsonKey.USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                "userId is mandatory",
                ResponseCode.CLIENT_ERROR.getResponseCode()
            );
        }

        String courseId = (String) request.get(JsonKey.COURSE_ID);
        if (courseId == null || courseId.trim().isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                "courseId is mandatory",
                ResponseCode.CLIENT_ERROR.getResponseCode()
            );
        }

        String batchId = (String) request.get(JsonKey.BATCH_ID);
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new ProjectCommonException(
                ResponseCode.mandatoryParamsMissing.getErrorCode(),
                "batchId is mandatory",
                ResponseCode.CLIENT_ERROR.getResponseCode()
            );
        }

        List<Map<String, Object>> contents = (List<Map<String, Object>>) request.get(JsonKey.CONTENTS);
        if (contents != null && !contents.isEmpty()) {
            for (Map<String, Object> content : contents) {
                String contentId = (String) content.get(JsonKey.CONTENT_ID);
                if (contentId == null || contentId.trim().isEmpty()) {
                    throw new ProjectCommonException(
                        ResponseCode.mandatoryParamsMissing.getErrorCode(),
                        "contentId is mandatory in contents",
                        ResponseCode.CLIENT_ERROR.getResponseCode()
                    );
                }

                Object statusObj = content.get("status");
                if (statusObj == null) {
                    throw new ProjectCommonException(
                        ResponseCode.mandatoryParamsMissing.getErrorCode(),
                        "status is mandatory in contents",
                        ResponseCode.CLIENT_ERROR.getResponseCode()
                    );
                }

                int status;
                if (statusObj instanceof Number) {
                    status = ((Number) statusObj).intValue();
                } else {
                    throw new ProjectCommonException(
                        ResponseCode.invalidRequestData.getErrorCode(),
                        "status must be a number",
                        ResponseCode.CLIENT_ERROR.getResponseCode()
                    );
                }

                if (status < 0 || status > 2) {
                    throw new ProjectCommonException(
                        ResponseCode.invalidRequestData.getErrorCode(),
                        "status must be 0, 1, or 2",
                        ResponseCode.CLIENT_ERROR.getResponseCode()
                    );
                }
            }
        }
    }
}
