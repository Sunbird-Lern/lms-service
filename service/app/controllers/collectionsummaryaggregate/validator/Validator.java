package controllers.collectionsummaryaggregate.validator;

import org.apache.log4j.MDC;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

import java.util.List;
import java.util.Map;

public class Validator extends BaseRequestValidator {
    public void validate(Request request) {
        System.out.println("requestsss" + request.getRequest());
        System.out.println("req" + request);
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
