package org.sunbird.learner.actors.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.learner.util.Util;

import java.util.HashMap;
import java.util.Map;

import static org.sunbird.common.models.util.JsonKey.EKSTEP_BASE_URL;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

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

    private void createCourse(Request request) throws Exception{
        String requestURL;
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.putAll((Map<String,Object>)request.get("course"));
        if(request.getRequest().containsKey("source")){
            contentMap.put("copyScheme", "TextBookToCourse");
            contentMap.put("courseType", "CurriculumCourse");
            requestURL = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/copy/" + request.get("source") + "?type=deep";
        } else {
            contentMap.put("courseType", "TrainingCourse");
            requestURL = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/create";
        }
        String reqBody = "{\"request\": {\"content\": " + mapper.writeValueAsString(contentMap) + "}}";
        Map<String, String> headers = new HashMap<String, String>(){{
            put("Content-Type", "application/json");
            put("X-Channel-Id", (String) request.getContext().get("channel"));
        }};
        String res = HttpUtil.sendPostRequest(requestURL, reqBody, headers);
        Response response = new Response();
        Map<String, Object> responseMap = mapper.readValue(res, Map.class);
        Map<String,Object> result = (Map<String,Object>) responseMap.get("result");
        if(request.getRequest().containsKey("source")) {
            Map<String,Object> node_id = (Map<String,Object>) result.get("node_id");
            result.put("identifier", node_id.get(request.get("source")));
        }
        response.getResult().put("course_id", result.get("identifier"));
        response.getResult().put("identifier", result.get("identifier"));
        response.getResult().put("versionKey", result.get("versionKey"));
        sender().tell(response, self());
    }
}