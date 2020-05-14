package org.sunbird.learner.actors.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;;
import org.sunbird.common.models.util.JsonKey;
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
        Util.initializeContext(request, "COURSE_CREATE");
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
        if(null == request.get("course")){
           ProjectCommonException.throwClientErrorException(ResponseCode.RESOURCE_NOT_FOUND, "Please provide course");
        }
        contentMap.putAll((Map<String, Object>) request.get("course"));
        String requestUrl;
        if (request.getRequest().containsKey("source")) {
            contentMap.put("copyScheme", "TextBookToCourse");
            contentMap.put("courseType", "CurriculumCourse");
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/copy/" + request.get("source") + "?type=deep";
        } else {
            contentMap.put("courseType", "TrainingCourse");
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/create";
        }
        Map<String, String> headers = new HashMap<String, String>() {{
            put("Content-Type", "application/json");
            put("X-Channel-Id", (String) request.getContext().get("channel"));
        }};
        Map<String, Object> requestMap = new HashMap<String, Object>(){{
            put("request", new HashMap<String, Object>() {{
                put("content", contentMap);
            }});
        }};
        try {
            HttpResponse<String> updateResponse =
                    Unirest.post(requestUrl)
                            .headers(headers)
                            .body(mapper.writeValueAsString(requestMap))
                            .asString();

            if (null != updateResponse) {
                Response response = mapper.readValue(updateResponse.getBody(), Response.class);
                if (response.getResponseCode().getResponseCode() == ResponseCode.OK.getResponseCode()) {
                    if (request.getRequest().containsKey("source")) {
                        Map<String, Object> node_id = (Map<String, Object>) response.get("node_id");
                        response.put("identifier", node_id.get(request.get("source")));
                    }
                    response.getResult().remove("node_id");
                    response.getResult().put("course_id", response.get("identifier"));
                    response.getResult().put("identifier", response.get("identifier"));
                    response.getResult().put("versionKey", response.get("versionKey"));
                    sender().tell(response, self());
                } else {
                    Map<String, Object> resultMap =
                            Optional.ofNullable(response.getResult()).orElse(new HashMap<>());
                    String message = "Course creation failed ";
                    if (MapUtils.isNotEmpty(resultMap)) {
                        Object obj = Optional.ofNullable(resultMap.get(JsonKey.TB_MESSAGES)).orElse("");
                        if (obj instanceof List) {
                            message += ((List<String>) obj).stream().collect(Collectors.joining(";"));
                        } else if(StringUtils.isNotEmpty(response.getParams().getErrmsg())){
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