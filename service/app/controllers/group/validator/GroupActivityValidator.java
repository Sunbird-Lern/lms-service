package controllers.group.validator;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;
import java.text.MessageFormat;

public class GroupActivityValidator {

    public static void validateRequest(Request request) {
        try {
            String message = "";
            if(null == request || MapUtils.isEmpty(request.getRequest())){
                message += "Error due to missing request body";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.missingData,
                        MessageFormat.format(ResponseCode.missingData.getErrorMessage(), message));
            }
            if (StringUtils.isBlank((String)request.get(SunbirdKey.GROUPID))) {
                    message += "Error due to missing groupId";
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.groupIdMismatch,
                            MessageFormat.format(ResponseCode.groupIdMismatch.getErrorMessage(), message));
            }
            if (StringUtils.isBlank((String)request.get(SunbirdKey.ACTIVITYID))) {
                message += "Error due to missing activityId";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.activityIdMismatch,
                        MessageFormat.format(ResponseCode.activityIdMismatch.getErrorMessage(), message));
            }
            if (StringUtils.isBlank((String)request.get(SunbirdKey.ACTIVITYTYPE))) {
                message += "Error due to missing activity type";
                ProjectCommonException.throwClientErrorException(
                        ResponseCode.activityTypeMismatch,
                        MessageFormat.format(ResponseCode.activityTypeMismatch.getErrorMessage(), message));
            }
        } catch (Exception ex) {
            throw ex;
        }
    }
}
