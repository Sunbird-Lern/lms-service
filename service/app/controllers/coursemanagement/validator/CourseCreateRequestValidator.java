package controllers.coursemanagement.validator;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

import java.text.MessageFormat;
import java.util.Map;

import static org.sunbird.common.responsecode.ResponseCode.SERVER_ERROR;

public class CourseCreateRequestValidator {
    public static void validateRequest(Request request) {
        try {
            String message = "";
            if (null == request.get(JsonKey.COURSE)) {
                message += "Error due to missing request body or course";
                setErrorMessage(message);
            }
            if (!request.getRequest().containsKey(JsonKey.SOURCE)) {
                if (!StringUtils.equals("Course", (String) ((Map<String, Object>) request.get("course")).get(JsonKey.CONTENT_TYPE))) {
                    message += "contentType should be Course";
                    setErrorMessage(message);
                }
                if (!StringUtils.equals(JsonKey.CONTENT_MIME_TYPE_COLLECTION, (String) ((Map<String, Object>) request.get(JsonKey.COURSE)).get(JsonKey.MIME_TYPE))) {
                    message += "mimeType should be collection";
                    setErrorMessage(message);
                }
            }
        } catch (Exception ex) {
            if (ex instanceof ProjectCommonException) {
                throw ex;
            } else {
                throw new ProjectCommonException(
                        ResponseCode.CLIENT_ERROR.getErrorCode(),
                        ResponseCode.CLIENT_ERROR.getErrorMessage(),
                        SERVER_ERROR.getResponseCode());
            }
        }
    }

    private static void setErrorMessage(String message) {
        ProjectCommonException.throwClientErrorException(
                ResponseCode.missingData,
                MessageFormat.format(
                        ResponseCode.missingData.getErrorMessage(), message));
    }
}
