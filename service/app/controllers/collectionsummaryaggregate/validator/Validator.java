package controllers.collectionsummaryaggregate.validator;

import org.sunbird.keys.JsonKey;
import org.sunbird.validators.BaseRequestValidator;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;

import java.util.Map;

public class Validator extends BaseRequestValidator {
    public void validate(Request request) {
        Map<String, Object> filters = null;
        filters = (Map<String, Object>) request.getRequest().get(JsonKey.FILTERS);
        validateParam(
                (String) filters.get(JsonKey.COLLECTION_ID),
                ResponseCode.mandatoryParamsMissing,
                JsonKey.COURSE_ID + "/" + JsonKey.COLLECTION_ID);
        validateParam(
                (String) filters.get(JsonKey.BATCH_ID),
                ResponseCode.mandatoryParamsMissing,
                JsonKey.BATCH_ID);
    }

}
