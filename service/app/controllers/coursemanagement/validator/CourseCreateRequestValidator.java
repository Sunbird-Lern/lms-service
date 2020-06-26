package controllers.coursemanagement.validator;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;

import java.text.MessageFormat;
import java.util.Map;

import static org.sunbird.common.responsecode.ResponseCode.CLIENT_ERROR;
import static org.sunbird.common.responsecode.ResponseCode.SERVER_ERROR;

public class CourseCreateRequestValidator {
    public static void validateRequest(Request request) {
        try {
            String message = "";
            if (null == request.get(SunbirdKey.COURSE)) {
                message += "Error due to missing request body or course";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.missingData,
                        MessageFormat.format(
                                ResponseCode.missingData.getErrorMessage(), message));            }
            if (!request.getRequest().containsKey(SunbirdKey.SOURCE)) {
                if (!StringUtils.equals("Course", (String) ((Map<String, Object>) request.get(SunbirdKey.COURSE)).get(SunbirdKey.CONTENT_TYPE))) {
                    message += "contentType should be Course";
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.contentTypeMismatch,
                            MessageFormat.format(
                                    ResponseCode.contentTypeMismatch.getErrorMessage(), message));                }
                if (!StringUtils.equals(SunbirdKey.CONTENT_MIME_TYPE_COLLECTION, (String) ((Map<String, Object>) request.get(SunbirdKey.COURSE)).get(SunbirdKey.MIME_TYPE))) {
                    message += "mimeType should be collection";
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.mimeTypeMismatch,
                            MessageFormat.format(
                                    ResponseCode.mimeTypeMismatch.getErrorMessage(), message));                }
            }
            if (request.getRequest().containsKey(SunbirdKey.SOURCE) && request.getRequest().containsKey(SunbirdKey.HIERARCHY)) {
                message += "Error Source and Hierarchy both can't be sent in the same request.";
                ProjectCommonException.throwClientErrorException(
                        CLIENT_ERROR,
                        MessageFormat.format(
                                ResponseCode.CLIENT_ERROR.getErrorMessage(), message));
            }
        } catch (Exception ex) {
            throw ex;
        }
    }

}
