package org.sunbird.learner.actors.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;
import static org.sunbird.common.responsecode.ResponseCode.CLIENT_ERROR;

public class HierarchyGenerationHelper {
    private static List<String> metadataToBeAdded = Arrays.stream((StringUtils.isNotBlank(getConfigValue(JsonKey.CONTENT_PROPS_TO_ADD)) ? getConfigValue(JsonKey.CONTENT_PROPS_TO_ADD) : "mimeType,contentType,name,code,description,keywords,framework,copyright,topic").split(",")).map(String::trim).collect(Collectors.toList());
    private static ObjectMapper mapper = new ObjectMapper();
    private LoggerUtil logger = new LoggerUtil(HierarchyGenerationHelper.class);

    public Map<String, Object> generateUpdateHierarchyRequest(Request request, String identifier) throws Exception {
        Map<String, Object> nodesModified = new HashMap<String, Object>();
        Map<String, Object> hierarchy = new HashMap<String, Object>();
        getRecursiveHierarchyRequest(identifier, (List<Map<String, Object>>)  request.getRequest().getOrDefault(SunbirdKey.HIERARCHY, new ArrayList<Map<String, Object>>()), nodesModified, hierarchy, true);
        Map<String, Object> updateRequest = new HashMap<String, Object>() {{
            put(SunbirdKey.REQUEST, new HashMap<String, Object>() {{
                put(SunbirdKey.DATA, new HashMap<String, Object>() {{
                    put(SunbirdKey.NODE_MODIFIED, nodesModified);
                    put(SunbirdKey.HIERARCHY, hierarchy);
                }});
            }});
        }};
        logger.info(request.getRequestContext(), "CourseManagementActor:generateUpdateHierarchyRequest : Request for course update Hierarchy : "
                + mapper.writeValueAsString(updateRequest));
        return updateRequest;
    }

    private  void getRecursiveHierarchyRequest(String parentId, List<Map<String, Object>> children, Map<String, Object> nodesModified,
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
                code = UUID.randomUUID().toString();
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
                putAll(cleanUpData(metadata));
                put("origin", metadata.get(SunbirdKey.IDENTIFIER));
                put("originData", getOriginData(metadata));
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

    private Map<String, Object> getOriginData(Map<String, Object> metadata) {
        return new HashMap<String, Object>() {{
            putAll(SunbirdKey.ORIGIN_METADATA_KEYS.stream().filter(key -> metadata.containsKey(key)).collect(Collectors.toMap(key-> key, key -> metadata.get(key))));
        }};
    }
}
