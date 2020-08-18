package org.sunbird.common.models.util;

import net.logstash.logback.marker.Markers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryWriter;

import java.util.HashMap;
import java.util.Map;

public class LoggerUtil {

    private Logger logger;
    
    public  LoggerUtil(Class c) {
        logger = LoggerFactory.getLogger(c);
    }
    
    public void info(RequestContext requestContext, String message, Object data) {
        if(null != requestContext) {
            logger.info(Markers.appendEntries(requestContext.getContextMap()), message, data);    
        } else {
            logger.info(message, data);
        }
        
    }

    public void info(RequestContext requestContext, String message) {
        info(requestContext, message, null);
    }

    public void error(RequestContext requestContext, String message, Throwable e) {
        if(null != requestContext) {
            logger.error(Markers.appendEntries(requestContext.getContextMap()) ,message, e);
        } else {
            logger.error(message, e);
        }
    }
    
    public void error(RequestContext requestContext, String message, Throwable e, Map<String, Object> telemetryInfo) {
        if(null != requestContext) {
            logger.error(Markers.appendEntries(requestContext.getContextMap()) ,message, e);
        } else {
            logger.error(message, e);
        }
        telemetryProcess(requestContext, telemetryInfo, e);
    }

    public void warn(RequestContext requestContext, String message, Throwable e) {
        if(null != requestContext) {
            logger.warn(Markers.appendEntries(requestContext.getContextMap()), message, e);
        } else {
            logger.warn(message, e);
        }
        
    }
    
    public void debug(RequestContext requestContext, String message, Object data) {
        if(isDebugEnabled(requestContext)) {
            logger.debug(Markers.appendEntries(requestContext.getContextMap()), message, data);
        } else {
            logger.debug(message, data);
        }
    }

    public void debug(RequestContext requestContext, String message) {debug(requestContext, message, null);}

    private static boolean isDebugEnabled(RequestContext requestContext) {
        if(null != requestContext && StringUtils.equalsIgnoreCase("true", requestContext.getDebugEnabled())) {
        }
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
}
