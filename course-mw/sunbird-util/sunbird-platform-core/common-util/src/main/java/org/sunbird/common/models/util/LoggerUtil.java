package org.sunbird.common.models.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryWriter;

import java.util.Map;

public class LoggerUtil {

    private Logger logger;
    private String infoLevel = "INFO";
    private String debugLevel = "DEBUG";
    private String errorLevel = "ERROR";
    private String warnLevel = "WARN";
    private Logger defaultLogger;
    private final ObjectMapper mapper = new ObjectMapper();

    public LoggerUtil(Class c) {
        logger = LoggerFactory.getLogger(c);
        defaultLogger = LoggerFactory.getLogger("defaultLogger");
    }

    public void info(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param) {
        if (requestContext != null) {
            requestContext.setLoggerLevel(infoLevel);
            logger.info(jsonMapper(requestContext, message, object, param));
        } else defaultLogger.info(message);
    }

    public void info(RequestContext requestContext, String message) {
        info(requestContext, message, null, null);
    }

    public void info(String message) {
        info(null, message, null, null);
    }

    public void debug(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param) {
        if (isDebugEnabled(requestContext)) {
            requestContext.setLoggerLevel(debugLevel);
            logger.info(jsonMapper(requestContext, message, object, param));
        } else defaultLogger.debug(message);
    }

    public void debug(RequestContext requestContext, String message) {
        debug(requestContext, message, null, null);
    }

    public void debug(String message) {
        debug(null, message, null, null);
    }

    public void error(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param, Throwable e) {
        if (requestContext != null) {
            requestContext.setLoggerLevel(errorLevel);
            logger.error(jsonMapper(requestContext, message, object, param), e);
        } else defaultLogger.error(message, e);
    }

    public void error(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param, Throwable e, Map<String, Object> telemetryInfo) {
        if (requestContext != null) {
            requestContext.setLoggerLevel(errorLevel);
            logger.error(jsonMapper(requestContext, message, object, param), e);
        } else defaultLogger.error(message, e);
        telemetryProcess(requestContext, telemetryInfo, e);
    }

    public void error(RequestContext requestContext, String message, Throwable e) {
        error(requestContext, message, null, null, e);
    }

    public void error(String message, Throwable e) {
        error(null, message, null, null, e);
    }

    public void error(RequestContext requestContext, String message, Throwable e, Map<String, Object> telemetryInfo) {
        error(requestContext, message, null, null, e, telemetryInfo);
    }

    public void warn(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param, Throwable e) {
        if (requestContext != null) {
            requestContext.setLoggerLevel(warnLevel);
            logger.warn((jsonMapper(requestContext, message, object, param)), e);
        } else defaultLogger.warn(message, e);
    }

    public void warn(RequestContext requestContext, String message, Throwable e) {
        warn(requestContext, message, null, null, e);
    }

    public void warn(String message, Throwable e) {
        warn(null, message, null, null, e);
    }

    private static boolean isDebugEnabled(RequestContext requestContext) {
        return (null != requestContext && StringUtils.equalsIgnoreCase("true", requestContext.getDebugEnabled()));
    }

    private void telemetryProcess(RequestContext requestContext, Map<String, Object> telemetryInfo, Throwable e) {
        ProjectCommonException projectCommonException = null;
        if (e instanceof ProjectCommonException) {
            projectCommonException = (ProjectCommonException) e;
        } else {
            projectCommonException =
                    new ProjectCommonException(
                            ResponseCode.internalError.getErrorCode(),
                            ResponseCode.internalError.getErrorMessage(),
                            ResponseCode.SERVER_ERROR.getResponseCode());
        }
        Request request = new Request(requestContext);
        telemetryInfo.put(JsonKey.TELEMETRY_EVENT_TYPE, TelemetryEvents.ERROR.getName());

        Map<String, Object> params = (Map<String, Object>) telemetryInfo.get(JsonKey.PARAMS);
        params.put(JsonKey.ERROR, projectCommonException.getCode());
        params.put(JsonKey.STACKTRACE, generateStackTrace(e.getStackTrace()));
        request.setRequest(telemetryInfo);
        //		lmaxWriter.submitMessage(request);
        TelemetryWriter.write(request);
    }

    private String generateStackTrace(StackTraceElement[] elements) {
        StringBuilder builder = new StringBuilder("");
        for (StackTraceElement element : elements) {
            builder.append(element.toString());
        }
        return builder.toString();
    }

    private String jsonMapper(RequestContext requestContext, String message, Map<String, Object> object, Map<String, Object> param) {
        try {
            return mapper.writeValueAsString(new CustomLogFormat(requestContext, message, object, param).getEventMap());
        } catch (JsonProcessingException e) {
            error(requestContext, e.getMessage(), e);
        }
        return "";
    }
}
