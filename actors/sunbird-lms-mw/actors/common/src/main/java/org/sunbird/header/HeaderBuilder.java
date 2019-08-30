package org.sunbird.header;

import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.HeaderParam;

import java.util.HashMap;
import java.util.Map;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.sunbird.common.models.util.JsonKey.BEARER;
import static org.sunbird.common.models.util.JsonKey.SUNBIRD_AUTHORIZATION;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

public class HeaderBuilder {

    private Map<String, String> headers;

    public HeaderBuilder() {
        this.headers = new HashMap<>();
    }

    public HeaderBuilder(Map<String, String> headers) {
        this.headers = new HashMap<>();
        this.headers.putAll(headers);
    }

    public HeaderBuilder add(String key, String value) {
        this.headers.put(key, value);
        return this;
    }


    public Map<String, String> build() {
        this.headers.put(AUTHORIZATION, BEARER + getConfigValue(SUNBIRD_AUTHORIZATION));
        this.headers.put("Content-Type", "application/json");
        if (ExecutionContext.getCurrent() != null
                && ExecutionContext.getCurrent().getRequestContext() != null) {
            if (ExecutionContext.getCurrent().getRequestContext().containsKey(JsonKey.DEVICE_ID)) {
                this.headers.put(
                        JsonKey.X_DEVICE_ID,
                        (String) ExecutionContext.getCurrent().getRequestContext().get(JsonKey.DEVICE_ID));
            }
            if (ExecutionContext.getCurrent().getRequestContext().containsKey(JsonKey.REQUEST_ID)) {
                this.headers.put(
                        JsonKey.MESSAGE_ID,
                        (String) ExecutionContext.getCurrent().getRequestContext().get(JsonKey.REQUEST_ID));
            }
        }
        return this.headers;
    }
}
