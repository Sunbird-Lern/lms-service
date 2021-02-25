package controllers.eventmanagement.validator;

import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;

import java.text.MessageFormat;

import static org.sunbird.common.models.util.JsonKey.FIXED_BATCH_ID;

public class EventRequestValidator {
    public static void validateRequest(Request request) {
        try {
            String message = "";
            if (null == request.get(SunbirdKey.IDENTIFIER)) {
                message += "Error due to missing request identifier";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.missingData,
                        MessageFormat.format(
                                ResponseCode.missingData.getErrorMessage(), message));
            }
            if (null == request.get(FIXED_BATCH_ID)) {
                message += "request should have fixedBatchId";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.missingData,
                        MessageFormat.format(
                                ResponseCode.missingData.getErrorMessage(), message));
            }
            if (!request.getRequest().getOrDefault(SunbirdKey.OBJECT_TYPE, "").equals("EventSet")) {
                message += "context objectType should be EventSet";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.contentTypeMismatch,
                        MessageFormat.format(
                                ResponseCode.contentTypeMismatch.getErrorMessage(), message));
            }
        } catch (Exception ex) {
            throw ex;
        }
    }

    public static void validateFixedBatchId(Request request) {
        if (null == request.get(FIXED_BATCH_ID)) {
            String message = "Error due to missing fixedBatchId";
            ProjectCommonException.throwClientErrorException(
                    ResponseCode.missingData,
                    MessageFormat.format(
                            ResponseCode.missingData.getErrorMessage(), message));
        }
    }


}