package org.sunbird.learner.actors.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sunbird.common.models.util.JsonKey.EKSTEP_BASE_URL;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

public class EventContentUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<String> getChildEventIds(Request request, String eventSetId) throws JsonProcessingException, UnirestException {
        Response response = getContent(request, JsonKey.IDENTIFIER, eventSetId, new HashMap<>(), "/eventset/v4/hierarchy/{identifier}");
        if (response != null && response.getResponseCode().getResponseCode() == ResponseCode.OK.getResponseCode()) {
            Map<String, Object> result = (Map<String, Object>) response.getResult().getOrDefault(SunbirdKey.EVENT_SET, new HashMap<String, Object>());
            return (List<String>) result.getOrDefault("childNodes", new ArrayList<String>());
        }
        else
            return new ArrayList<>();
    }

    private static Response getContent(Request request, String pathId, String pathValue, Map<String, Object> queryMap, String uri) throws UnirestException, JsonProcessingException {
        String requestUrl = getConfigValue(EKSTEP_BASE_URL) + uri;
        Map<String, String> headers = new HashMap<>();
        headers.put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON);
        headers.put(SunbirdKey.X_CHANNEL_ID, request.getContext().getOrDefault(SunbirdKey.CHANNEL, "").toString());
        HttpResponse<String> httpResponse  = Unirest.get(requestUrl).headers(headers).routeParam(pathId, pathValue).queryString(queryMap).asString();
        Response response = null;
        if (null != httpResponse) {
            response = mapper.readValue(httpResponse.getBody(), Response.class);
        }
        return response;
    }

    public static Response postContent(Request request, String contentKey, String uri, Map<String, Object> contentMap, String pathId, String pathVal) throws UnirestException, JsonProcessingException {
        String requestUrl = getConfigValue(EKSTEP_BASE_URL) + uri;
        Map<String, String> headers = new HashMap<String, String>() {{
            put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON);
            put(SunbirdKey.X_CHANNEL_ID, (String) request.getContext().get(SunbirdKey.CHANNEL));
        }};
        Map<String, Object> requestMap = new HashMap<String, Object>() {{
            put(SunbirdKey.REQUEST, new HashMap<String, Object>() {{
                put(contentKey, contentMap);
            }});
        }};

        HttpResponse<String> updateResponse =
                Unirest.patch(requestUrl)
                        .headers(headers)
                        .routeParam(pathId, pathVal)
                        .body(mapper.writeValueAsString(requestMap))
                        .asString();

        Response response = null;
        if (null != updateResponse) response = mapper.readValue(updateResponse.getBody(), Response.class);
        return response;
    }

    public static Response deleteContent(Request request, String uri, String pathId, String pathVal) throws UnirestException, JsonProcessingException {
        String requestUrl = getConfigValue(EKSTEP_BASE_URL) + uri;
        Map<String, String> headers = new HashMap<String, String>() {{
            put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON);
            put(SunbirdKey.X_CHANNEL_ID, (String) request.getContext().get(SunbirdKey.CHANNEL));
        }};

        HttpResponse<String> updateResponse =
                Unirest.delete(requestUrl)
                        .headers(headers)
                        .routeParam(pathId, pathVal)
                        .asString();

        Response response = null;
        if (null != updateResponse) response = mapper.readValue(updateResponse.getBody(), Response.class);
        return response;
    }


}