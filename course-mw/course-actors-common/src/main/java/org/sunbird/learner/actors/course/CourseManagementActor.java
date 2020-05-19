package org.sunbird.learner.actors.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;;
import org.sunbird.keys.*;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.util.Util;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.sunbird.common.models.util.JsonKey.EKSTEP_BASE_URL;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.SERVER_ERROR;

public class CourseManagementActor extends BaseActor {
    private static ObjectMapper mapper = new ObjectMapper();

    @Override
    public void onReceive(Request request) throws Throwable {
        Util.initializeContext(request, TelemetryEnvKey.COURSE_CREATE);
        ExecutionContext.setRequestId(request.getRequestId());
        String requestedOperation = request.getOperation();
        switch (requestedOperation) {
            case "createCourse":
                createCourse(request);
                break;

            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void createCourse(Request request) throws Exception {
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.putAll((Map<String, Object>) request.get(SunbirdKey.COURSE));
        String requestUrl;
        if (request.getRequest().containsKey(SunbirdKey.SOURCE)) {
            if(!((Map<String, Object>) request.get(SunbirdKey.COURSE)).containsKey(SunbirdKey.COPY_SCHEME)) {
                contentMap.put(SunbirdKey.COPY_SCHEME, SunbirdKey.TEXT_BOOK_TO_COURSE);
            }
            contentMap.put(SunbirdKey.COURSE_TYPE, SunbirdKey.CURRICULUM_COURSE);
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/copy/" + request.get(SunbirdKey.SOURCE) + "?type=deep";
        } else {
            contentMap.put(SunbirdKey.COURSE_TYPE, SunbirdKey.TRAINING_COURSE);
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/create";
        }
        Map<String, String> headers = new HashMap<String, String>() {{
            put(SunbirdKey.CONTENT_TYPE, SunbirdKey.APPLICATION_JSON);
            put(SunbirdKey.X_CHANNEL_ID, (String) request.getContext().get(SunbirdKey.CHANNEL));
        }};
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put(SunbirdKey.REQUEST, new HashMap<String, Object>() {{
                put(SunbirdKey.CONTENT, contentMap);
            }});
        }};
        try {
            HttpResponse<String> updateResponse =
                    Unirest.post(requestUrl)
                            .headers(headers)
                            .body(mapper.writeValueAsString(requestMap))
                            .asString();
            ProjectLogger.log(
                    "CourseManagementActor:createCourse : Request for course create : "
                            + mapper.writeValueAsString(requestMap),
                    LoggerEnum.INFO.name());

            ProjectLogger.log(
                    "Sized: CourseManagementActor:createCourse : size of request : "
                            + mapper.writeValueAsString(requestMap).getBytes().length,
                    LoggerEnum.INFO);
            if (null != updateResponse) {
                Response response = mapper.readValue(updateResponse.getBody(), Response.class);
                ProjectLogger.log(
                        "Sized: CourseManagementActor:createCourse : size of response : "
                                + updateResponse.getBody().getBytes().length,
                        LoggerEnum.INFO);
                if (response.getResponseCode().getResponseCode() == ResponseCode.OK.getResponseCode()) {
                    if (request.getRequest().containsKey(SunbirdKey.SOURCE)) {
                        Map<String, Object> node_id = (Map<String, Object>) response.get(SunbirdKey.NODE_ID);
                        response.put(SunbirdKey.IDENTIFIER, node_id.get(request.get(SunbirdKey.SOURCE)));
                    }
                    response.getResult().remove(SunbirdKey.NODE_ID);
                    response.getResult().put(SunbirdKey.COURSE_ID, response.get(SunbirdKey.IDENTIFIER));
                    response.getResult().put(SunbirdKey.IDENTIFIER, response.get(SunbirdKey.IDENTIFIER));
                    response.getResult().put(SunbirdKey.VERSION_KEY, response.get(SunbirdKey.VERSION_KEY));
                    sender().tell(response, self());
                } else {
                    Map<String, Object> resultMap =
                            Optional.ofNullable(response.getResult()).orElse(new HashMap<>());
                    String message = "Course creation failed ";
                    if (MapUtils.isNotEmpty(resultMap)) {
                        Object obj = Optional.ofNullable(resultMap.get(SunbirdKey.TB_MESSAGES)).orElse("");
                        if (obj instanceof List) {
                            message += ((List<String>) obj).stream().collect(Collectors.joining(";"));
                        } else if (StringUtils.isNotEmpty(response.getParams().getErrmsg())) {
                            message += response.getParams().getErrmsg();
                        } else {
                            message += String.valueOf(obj);
                        }
                    }
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.customServerError,
                            MessageFormat.format(
                                    ResponseCode.customServerError.getErrorMessage(), message));
                }
            } else {
                ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR);
            }
        } catch (Exception ex) {
            ProjectLogger.log("CourseManagementActor:createCourse : course create error ", ex);
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

}