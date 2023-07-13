package org.sunbird.learner.actors.qrcodedownload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.RequestContext;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.util.ContentSearchUtil;

import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

public class QRCodeDownloadManager {
    public LoggerUtil logger = new LoggerUtil(this.getClass());
    private static final List<String> fields = Arrays.asList("identifier", "dialcodes", "name","channel");
    private static final Map<String, String> filtersHelperMap =
            new HashMap<String, String>() {
                {
                    put(JsonKey.USER_IDs, JsonKey.CREATED_BY);
                    put(JsonKey.STATUS, JsonKey.STATUS);
                    put(JsonKey.CONTENT_TYPE, JsonKey.CONTENT_TYPE);
                }
            };
    private static final int SEARCH_CONTENTS_LIMIT = Integer.parseInt(StringUtils.isNotBlank(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_QRCODE_COURSES_LIMIT)) ? ProjectUtil.getConfigValue(JsonKey.SUNBIRD_QRCODE_COURSES_LIMIT) : "2000");

    /**
     * Search call to Learning Platform composite search engine
     *
     *
     * @param requestContext
     * @param requestMap
     * @param headers
     * @return
     */
    public Map<String, Object> searchCourses(
            RequestContext requestContext, Map<String, Object> requestMap, Map<String, String> headers) throws UnirestException {
        String request = prepareSearchRequest (requestContext, requestMap);
        return ContentSearchUtil.searchContentSync(requestContext, null, request, headers);
    }

    /**
     * Request Preparation for search Request for getting courses created by user and dialcodes linked
     * to them.
     *
     *
     * @param requestContext
     * @param requestMap
     * @return
     */
    private String prepareSearchRequest(RequestContext requestContext, Map<String, Object> requestMap) {
        Map<String, Object> searchRequestMap =
                new HashMap<String, Object>() {
                    {
                        put(
                                JsonKey.FILTERS,
                                requestMap
                                        .keySet()
                                        .stream()
                                        .filter(key -> filtersHelperMap.containsKey(key))
                                        .collect(
                                                Collectors.toMap(
                                                        key -> filtersHelperMap.get(key), key -> requestMap.get(key))));
                        put(JsonKey.FIELDS, fields);
                        put(JsonKey.EXISTS, JsonKey.DIAL_CODES);
                        put(JsonKey.SORT_BY, new HashMap<String, String>() {{
                            put(SunbirdKey.LAST_PUBLISHED_ON, JsonKey.DESC);
                        }});
                        //TODO: Limit should come from request, need to facilitate this change.
                        put(JsonKey.LIMIT, SEARCH_CONTENTS_LIMIT);
                    }
                };
        Map<String, Object> request =
                new HashMap<String, Object>() {
                    {
                        put(JsonKey.REQUEST, searchRequestMap);
                    }
                };
        String requestJson = null;
        try {
            requestJson = new ObjectMapper().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            logger.error(requestContext, "QRCodeDownloadManagement:prepareSearchRequest: Exception occurred with error message = "
                    + e.getMessage(), e);
        }
        return requestJson;
    }

    /**
     * Fetch QR code Urls for the given dialcodes
     *
     * @param dialCodes
     * @return
     */
    public Map<String, String> getQRCodeImageURLs(Set<String> dialCodes, String channel) {
        Map<String, String> headers = new HashMap<>();
        String params = "{\"request\": {\"search\":{\"identifier\": [\""+String.join("\",\"",dialCodes)+"\"]}}}";
        try {
            String dialServiceUrl = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DIAL_SERVICE_BASE_URL);
            headers.put(JsonKey.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.EKSTEP_AUTHORIZATION));
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            headers.put("X-Channel-ID", channel);
            headers.remove(HttpHeaders.ACCEPT_ENCODING.toLowerCase());
            headers.put(HttpHeaders.ACCEPT_ENCODING.toLowerCase(), "UTF-8");
            if (org.apache.commons.lang3.StringUtils.isBlank(headers.get(JsonKey.AUTHORIZATION))) {
                headers.put(
                        JsonKey.AUTHORIZATION,
                        PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION));
            }
            logger.info(null, "QRCodeDownloadManager:: getQRCodeImageUrl:: invoking DIAL service for QR Code Images:: " + params);
            String response = HttpUtil.sendPostRequest(dialServiceUrl + PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_DIAL_SERVICE_SEARCH_URL), params, headers);
            Map<String, Object> data = new ObjectMapper().readValue(response, Map.class);
            logger.info(null, "QRCodeDownloadManager:: getQRCodeImageUrl:: QR Code List response:: ", null, (Map<String, Object>) data.get(JsonKey.PARAMS));
            if (MapUtils.isNotEmpty(data)) {
                Map<String, Object> resultData = (Map<String, Object>) data.get(JsonKey.RESULT);
                logger.info(null,"QRCodeDownloadManager:: getQRCodeImageUrl:: Total number of images fetched : " + ((List) resultData.get("dialcodes")).size());
                if (MapUtils.isNotEmpty(resultData)) {
                    List<Map<String, Object>> qrCodeImagesList = (List) resultData.get("dialcodes");
                    Map<String, String> resMap = new HashMap<>();

                    for(Map<String, Object> qrImageObj : qrCodeImagesList) {
                        if(qrImageObj.get("imageUrl") != null )
                            resMap.put(qrImageObj.get("identifier").toString(), qrImageObj.get("imageUrl").toString());
                        else
                            resMap.put(qrImageObj.get("identifier").toString(), "");
                    }
                    return resMap;
                }
            } else {
                logger.info(null, "QRCodeDownloadManager:: getQRCodeImageUrl::  No data found");
            }
        } catch (Exception e) {
            logger.error(null, "QRCodeDownloadManager:: getQRCodeImageUrl:: Error found during qr image list:: " + e.getMessage(), e);
        }
        return new HashMap<>();
    }

}
