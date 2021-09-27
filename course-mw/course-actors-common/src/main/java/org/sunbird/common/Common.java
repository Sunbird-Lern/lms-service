package org.sunbird.common;

import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class Common {

    public static Map<String, String[]> getRequestHeadersInArray(Map<String, List<String>> requestHeaders) {
        Map<String, String[]> requestHeadersArray = new HashMap();
        requestHeaders.entrySet().forEach(entry -> requestHeadersArray.put(entry.getKey(), entry.getValue().toArray(new String[0])));
        return requestHeadersArray;
    }

    public static void handleFixedBatchIdRequest(Request request) {
        String courseIdKey = request.getRequest().containsKey(JsonKey.COURSE_ID)
                ? JsonKey.COURSE_ID
                : (request.getRequest().containsKey(JsonKey.ENROLLABLE_ITEM_ID)
                ? JsonKey.ENROLLABLE_ITEM_ID
                : JsonKey.COLLECTION_ID);
        String courseId = request.getRequest().getOrDefault(courseIdKey, "").toString();
        request.getRequest().put(JsonKey.COURSE_ID, courseId);
        //Till we add a type, we will use fixed batch identifier to prefix to course to get the batchId
        String fixedBatchId = request.getRequest().getOrDefault(JsonKey.FIXED_BATCH_ID, "").toString();
        String batchId = request.getRequest().getOrDefault(JsonKey.BATCH_ID, "").toString();
        if (!fixedBatchId.isEmpty()) {
            batchId = formBatchIdForFixedBatchId(courseId, fixedBatchId);
        }
        request.getRequest().put(JsonKey.BATCH_ID, batchId);
    }

    public static String formBatchIdForFixedBatchId(String courseId, String fixedBatchId) {
        return fixedBatchId + "-" + courseId;
    }
}
