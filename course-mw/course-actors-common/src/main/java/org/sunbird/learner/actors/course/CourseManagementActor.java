package org.sunbird.learner.actors.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
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
import static org.sunbird.common.responsecode.ResponseCode.CLIENT_ERROR;
import static org.sunbird.common.models.util.JsonKey.CONTENT_PROPS_TO_ADD;

public class CourseManagementActor extends BaseActor {
    private static ObjectMapper mapper = new ObjectMapper();
    private static List<String> metadataToBeAdded = Arrays.stream((StringUtils.isNotBlank(getConfigValue(CONTENT_PROPS_TO_ADD)) ? getConfigValue(CONTENT_PROPS_TO_ADD) : "mimeType,contentType,name,code,description,keywords,framework,copyright,topic").split(",")).map(String::trim).collect(Collectors.toList());

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
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/copy/" + request.get(SunbirdKey.SOURCE) + "?type=deep";
        } else {
            requestUrl = getConfigValue(EKSTEP_BASE_URL) + "/content/v3/create";
        }
        Map<String, String> headers = new HashMap<String, String>() {{
            put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON);
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
                    handleHierarchyData(request, (String) response.getResult().get(SunbirdKey.IDENTIFIER), headers);
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
                        ResponseCode.SERVER_ERROR.getErrorCode(),
                        ResponseCode.SERVER_ERROR.getErrorMessage(),
                        ResponseCode.SERVER_ERROR.getResponseCode());
            }
        }
    }

    private void handleHierarchyData(Request request, String identifier, Map<String, String> headers) throws Exception {
        if (request.getRequest().containsKey(SunbirdKey.HIERARCHY)) {
            String url = "http://11.2.6.6/content" + "/content/v3/hierarchy/update";
            HttpResponse<String> updateResponse =
                    Unirest.patch(url)
                            .headers(headers)
                            .body(mapper.writeValueAsString(generateUpdateHierarchyRequest(request, identifier)))
                            .asString();
            if (null != updateResponse) {
                Response response = mapper.readValue(updateResponse.getBody(), Response.class);
                if (!StringUtils.equalsIgnoreCase(response.getResponseCode().name(), ResponseCode.OK.name())) {
                    ProjectLogger.log(
                            "Error occurred in : CourseManagementActor:handleHierarchyData : response code: "
                                    + response.getResponseCode() + " and response message " + response.getParams().getErrmsg(),
                            LoggerEnum.INFO);
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.customServerError,
                            MessageFormat.format(
                                    ResponseCode.customServerError.getErrorMessage(), response.getParams().getErrmsg()));
                }
            } else {
                ProjectLogger.log(
                        "Error because update hierarchy response was null : CourseManagementActor:handleHierarchyData",
                        LoggerEnum.INFO);
                ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR);
            }

        }
    }

    private Map<String, Object> generateUpdateHierarchyRequest(Request request, String identifier) throws Exception {
        Map<String, Object> nodesModified = new HashMap<String, Object>();
        Map<String, Object> hierarchy = new HashMap<String, Object>();
        getRecursiveHierarchyRequest(identifier, (List<Map<String, Object>>) request.get(SunbirdKey.HIERARCHY), nodesModified, hierarchy, true);
        Map<String, Object> updateRequest = new HashMap<String, Object>() {{
            put(SunbirdKey.REQUEST, new HashMap<String, Object>() {{
                put(SunbirdKey.DATA, new HashMap<String, Object>() {{
                    put(SunbirdKey.NODE_MODIFIED, nodesModified);
                    put(SunbirdKey.HIERARCHY, hierarchy);
                }});
            }});
        }};
        ProjectLogger.log(
                "CourseManagementActor:generateUpdateHierarchyRequest : Request for course update Hierarchy : "
                        + mapper.writeValueAsString(updateRequest),
                LoggerEnum.INFO.name());
        return updateRequest;
    }

    private void getRecursiveHierarchyRequest(String parentId, List<Map<String, Object>> children, Map<String, Object> nodesModified,
                                              Map<String, Object> hierarchy, Boolean root) {
        children.forEach(child -> {
            //Checking if mandatory params are present.
            SunbirdKey.MANDATORY_PARAMS_FOR_COURSE_UNITS.forEach(key -> {
                if(!child.containsKey(key))
                    throw new ProjectCommonException(
                            ResponseCode.CLIENT_ERROR.getErrorCode(),
                            key + " is a mandatory parameter for child of parent with id: " + parentId,
                            CLIENT_ERROR.getResponseCode());
            });
            //Creation of new code for new Units and population of nodes modified.
            String code = (String) child.get(SunbirdKey.IDENTIFIER);
            if (StringUtils.equalsIgnoreCase((String) child.get(SunbirdKey.VISIBILITY), SunbirdKey.VISIBILITY_PARENT)) {
                code = System.currentTimeMillis() + "";
                nodesModified.put(code, getNodeModifiedMap(child));
            }
            //Population of hierarchy.
            if (MapUtils.isEmpty(((Map<String, Object>) hierarchy.get(parentId))))
                hierarchy.put(parentId, getNodeHierarchyMap(root));
            ((List<String>) ((Map<String, Object>) hierarchy.get(parentId)).get(SunbirdKey.CHILDREN)).add(code);
            //Recursive call to get the rest of the hierarchy
            if (StringUtils.equalsIgnoreCase((String) child.get(SunbirdKey.MIME_TYPE), SunbirdKey.CONTENT_MIME_TYPE_COLLECTION)
                    && !StringUtils.equalsIgnoreCase((String) child.get(SunbirdKey.VISIBILITY), SunbirdKey.VISIBILITY_DEFAULT))
                getRecursiveHierarchyRequest(code,
                        (List<Map<String, Object>>) child.getOrDefault(SunbirdKey.CHILDREN, new ArrayList<Map<String, Object>>()),
                        nodesModified, hierarchy, false);
        });
    }

    private Map<String, Object> getNodeModifiedMap(Map<String, Object> metadata) {
        metadata.put(SunbirdKey.CONTENT_TYPE, "CourseUnit");
        return new HashMap<String, Object>() {{
            put(SunbirdKey.METADATA, new HashMap<String, Object>() {{
                cleanUpData(metadata);
                put("origin", metadata.get(SunbirdKey.IDENTIFIER));
                put("originData", new HashMap<String, Object>() {{
                    put("name", metadata.get(SunbirdKey.NAME));
                }});
            }});
            put(SunbirdKey.ROOT, false);
            put("isNew", true);
            put("setDefaultValue", false);
        }};
    }

    private Map<String, Object> getNodeHierarchyMap(Boolean root) {
        return new HashMap<String, Object>() {{
            put(SunbirdKey.CHILDREN, new ArrayList<String>());
            put(SunbirdKey.ROOT, root);
        }};
    }

    private Map<String, Object> cleanUpData(Map<String, Object> metadata) {
        return metadata.entrySet().stream().filter(entry -> metadataToBeAdded.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}