package filters;

import akka.util.ByteString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryWriter;
import play.libs.streams.Accumulator;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Result;
import util.Attrs;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

public class AccessLogFilter extends EssentialFilter {

    private final Executor executor;
    private ObjectMapper objectMapper = new ObjectMapper();
    public LoggerUtil logger = new LoggerUtil(this.getClass());

    @Inject
    public AccessLogFilter(Executor executor) {
        super();
        this.executor = executor;
    }

    @Override
    public EssentialAction apply(EssentialAction next) {
        return EssentialAction.of(
                request -> {
                    long startTime = System.currentTimeMillis();
                    Accumulator<ByteString, Result> accumulator = next.apply(request);
                    return accumulator.map(
                            result -> {
                                long endTime = System.currentTimeMillis();
                                long requestTime = endTime - startTime;
                                try {
                                    org.sunbird.common.request.Request req = new org.sunbird.common.request.Request();
                                    Map<String, Object> params = new WeakHashMap<>();
                                    params.put(JsonKey.URL, request.uri());
                                    params.put(JsonKey.METHOD, request.method());
                                    params.put(JsonKey.LOG_TYPE, JsonKey.API_ACCESS);
                                    params.put(JsonKey.MESSAGE, "");
                                    params.put(JsonKey.METHOD, request.method());
                                    params.put(JsonKey.DURATION, requestTime);
                                    params.put(JsonKey.STATUS, result.status());
                                    params.put(JsonKey.LOG_LEVEL, JsonKey.INFO);
                                    String contextDetails = request.attrs().getOptional(Attrs.CONTEXT).orElse("");
                                    if(StringUtils.isNotBlank(contextDetails)) {
                                        Map<String, Object> context =
                                                objectMapper.readValue(
                                                        contextDetails, new TypeReference<Map<String, Object>>() {});
                                        req.setRequest(
                                                generateTelemetryRequestForController(
                                                        TelemetryEvents.LOG.getName(),
                                                        params,
                                                        (Map<String, Object>) context.get(JsonKey.CONTEXT)));
                                        TelemetryWriter.write(req);
                                    }
                                } catch (Exception ex) {
                                   logger.error(null, "AccessLogFilter:apply Exception in writing telemetry", ex);
                                }
                                return result;
                            },
                            executor);
                });
    }

    private Map<String, Object> generateTelemetryRequestForController(
            String eventType, Map<String, Object> params, Map<String, Object> context) {

        Map<String, Object> map = new HashMap<>();
        map.put(JsonKey.TELEMETRY_EVENT_TYPE, eventType);
        map.put(JsonKey.CONTEXT, context);
        map.put(JsonKey.PARAMS, params);
        return map;
    }
}
