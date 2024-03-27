package org.sunbird.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.learner.util.Util;

import java.util.*;
import java.util.stream.Collectors;

import static org.sunbird.common.models.util.JsonKey.*;
import static org.sunbird.common.models.util.JsonKey.X_AUTH_TOKEN;

public class CourseRecommendationActor extends BaseActor {


    @Override
    public void onReceive(Request request) throws Throwable {

        Util.initializeContext(request, TelemetryEnvKey.RECOMMENDATION, this.getClass().getName());

        String requestedOperation = request.getOperation();
        switch (requestedOperation) {
            case "listRecommended":
                getRecommendedCourse(request);
                break;
            case "consumedCourseRecommended":
                getRecommendedCourseBasedOnConsumedCourses(request);
                break;
            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void getRecommendedCourse(Request request) throws Throwable {

        Response finalResponse;
        Map<String, Object> data = request.getRequest();
        int limit = (int) data.get(LIMIT);
        if (data.containsKey(TARGET_TAXONOMY_CATEGORY_4IDS)) {
            List<String> courseIds = new ArrayList<>();

            List<String> courseIdsOfCompetency = getRecommendedCourseBasedOnCompetency(request);
            courseIds.addAll(courseIdsOfCompetency);

            List<String> courseIdsOfConsumedCourses = getRecommendedCourseBasedOnConsumedCourses(request);
            courseIds.addAll(courseIdsOfConsumedCourses);

            Set<String> uniqueCourseIds = courseIds.stream().collect(Collectors.toSet());
            courseIds = new ArrayList<>(uniqueCourseIds);

            System.out.println("final courseIds:"+courseIds);

            finalResponse = contentSearchApiCall(courseIds, true, limit);
        } else {
            finalResponse = contentSearchApiCall(null, false, limit);
        }
        sender().tell(finalResponse, self());
    }

    private List<String> getRecommendedCourseBasedOnCompetency(Request request) throws Throwable {
        Map<String, Object> data = request.getRequest();

        int limit = (int) data.get(LIMIT);
        String userId = (String) request.getContext().getOrDefault(REQUESTED_FOR, request.getContext().get(REQUESTED_BY));
        Response response = getUserEnrolledCourses(request);
        List<String> competencyValue = (List<String>) data.get(TARGET_TAXONOMY_CATEGORY_4IDS);
        System.out.println("competencyValue:"+competencyValue);
        List<String> taxonomyCategory4IdsList = new ArrayList<>(competencyValue);
        System.out.println("taxonomyCategory4IdsList:"+taxonomyCategory4IdsList);
        List<String> keywordsList = searchUser(userId, limit, request);
        System.out.println("keywordsList:"+keywordsList);
        List<String> courseIds = getCourses(response, limit, request, keywordsList, taxonomyCategory4IdsList, null);
        return courseIds;
    }

    private List<String> getRecommendedCourseBasedOnConsumedCourses(Request request) throws Throwable {

        Map<String, Object> data = request.getRequest();
        int limit = (int) data.get(LIMIT);

        String userId = (String) request.getContext().getOrDefault(REQUESTED_FOR, request.getContext().get(REQUESTED_BY));
        Response response = getUserEnrolledCourses(request);

        List<String> competencyValue = (List<String>) response.get(COMPETENCY);
        List<String> taxonomyCategory4IdsList = new ArrayList<>(competencyValue);

        List<String> keywordsValue = (List<String>) response.get(KEYWORDS);
        List<String> keywordsList = new ArrayList<>(keywordsValue);

        List<String> topicsValue = (List<String>) response.get(TARGET_TAXONOMY_CATEGORY_3IDS);
        List<String> taxonomyCategory3IdsList = new ArrayList<>(topicsValue);

        List<String> courseIds = getCourses(response, limit, request, keywordsList, taxonomyCategory4IdsList, taxonomyCategory3IdsList);

        return courseIds;
    }


    private Response getUserEnrolledCourses(Request request) throws Throwable {

        ObjectMapper objectMapper = new ObjectMapper();
        Response response = new Response();

        String userId = (String) request.getContext().getOrDefault(REQUESTED_FOR, request.getContext().get(REQUESTED_BY));

        /***** To get the listOfUserEnrolledCourses *****/
        String urlString = "https://compass-dev.tarento.com/api/course/v1/user/enrollment/list/" + userId;

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk",
                X_AUTHENTICATED_USER_TOKEN, request.getContext().get(X_AUTH_TOKEN).toString()
        );
        HttpResponse<String> userEnrolledCourses = Unirest.get(urlString)
                .headers(headers)
                .asString();

        /***** Extracting the courseIds ****/
        JsonNode jsonNode = objectMapper.readTree(userEnrolledCourses.getBody());
        JsonNode courseNode = jsonNode.get(RESULT).get(COURSES);

        List<String> courseIds = new ArrayList<>();
        List<String> taxonomyCategory4IdsList = new ArrayList<>();
        List<String> taxonomyCategory3IdsList = new ArrayList<>();
        List<String> keywordsList = new ArrayList<>();

        if (courseNode.isArray()) {
            for (JsonNode course : courseNode) {
                String courseId = course.get(COURSE_ID).asText();
                courseIds.add(courseId);
                JsonNode taxonomyCategoryIdsNode = course.path(CONTENT).path(TARGET_TAXONOMY_CATEGORY_4IDS);
                if (taxonomyCategoryIdsNode.isArray() && taxonomyCategoryIdsNode.size() > 0) {
                    for (JsonNode idNode : taxonomyCategoryIdsNode) {
                        String idValue = idNode.asText();
                        if (!taxonomyCategory4IdsList.contains(idValue))
                            taxonomyCategory4IdsList.add(idValue);
                    }
                }

                JsonNode taxonomyCategory3IdsNode = course.path(CONTENT).path(TARGET_TAXONOMY_CATEGORY_3IDS);
                if (!taxonomyCategory3IdsNode.isMissingNode()) {
                    if (taxonomyCategory3IdsNode.isArray() && taxonomyCategory3IdsNode.size() > 0) {
                        for (JsonNode idNode : taxonomyCategory3IdsNode) {
                            String idValue = idNode.asText();
                            if (!taxonomyCategory3IdsList.contains(idValue))
                                taxonomyCategory3IdsList.add(idValue);
                        }
                    }
                }

                JsonNode keywords = course.path(CONTENT).path(KEYWORDS);
                if (!keywords.isMissingNode()) {
                    if (keywords.isArray() && keywords.size() > 0) {
                        for (JsonNode idNode : keywords) {
                            String idValue = idNode.asText();
                            if (!keywordsList.contains(idValue))
                                keywordsList.add(idValue);
                        }
                    }
                }
            }
        }
        response.put(COURSE_IDS, courseIds);
        response.put(COMPETENCY, taxonomyCategory4IdsList);
        response.put(TARGET_TAXONOMY_CATEGORY_3IDS, taxonomyCategory3IdsList);
        response.put(KEYWORDS, keywordsList);

        return response;
    }

    private List<String> getCourses(Response response, int limit, Request requestForm, List<String> keywordsList, List<String> taxonomyCategory4IdsList, List<String> taxonomyCategory3IdsList) throws Throwable {

        Request request;
        String requestBody;
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> courseIds;
        Gson gson = new Gson();
        ArrayNode combinedContent = objectMapper.createArrayNode();
        Response newResponse;

        /*** To get courseIds based on competency ***/
        request = formatRequest(false, true, limit, requestForm, keywordsList, taxonomyCategory4IdsList, taxonomyCategory3IdsList);
        requestBody = gson.toJson(request.getRequest());
        Response response1 = compositeSearchApiCall(requestBody);
        Object contentObject1 = response1.getResult().get(CONTENT);
        if (contentObject1 != null) {
            ArrayList<Object> contentArrayList1 = (ArrayList<Object>) contentObject1;
            ArrayNode contentNode1 = objectMapper.valueToTree(contentArrayList1);
            combinedContent.addAll(contentNode1);
        }


        /*** To get courseIds based on AreaOfInterest/Keywords ***/
        if (!keywordsList.isEmpty()) {
            request = formatRequest(true, false, limit, requestForm, keywordsList, taxonomyCategory4IdsList, taxonomyCategory3IdsList);
            requestBody = gson.toJson(request.getRequest());
            Response response2 = compositeSearchApiCall(requestBody);
            Object contentObject2 = response2.getResult().get(CONTENT);
            if (contentObject2 != null) {
                ArrayList<Object> contentArrayList2 = (ArrayList<Object>) contentObject2;
                ArrayNode contentNode2 = objectMapper.valueToTree(contentArrayList2);
                combinedContent.addAll(contentNode2);
            }
        }

        /*** To get courseIds based on Topics ***/
        if (!(taxonomyCategory3IdsList == null)) {
            request = formatRequest(false, false, limit, requestForm, keywordsList, taxonomyCategory4IdsList, taxonomyCategory3IdsList);
            requestBody = gson.toJson(request.getRequest());
            Response response3 = compositeSearchApiCall(requestBody);
            Object contentObject3 = response3.getResult().get(CONTENT);
            if (contentObject3 != null) {
                ArrayList<Object> contentArrayList3 = (ArrayList<Object>) contentObject3;
                ArrayNode contentNode3 = objectMapper.valueToTree(contentArrayList3);
                combinedContent.addAll(contentNode3);
            }
        }


        /*** combined courseIds ***/
        response.getResult().put(CONTENT, combinedContent);

        /*** To get list of recommended courses ***/
        courseIds = courseIdFilters(response);

        return courseIds;
    }

    private Response contentSearchApiCall(List<String> courseIds, boolean header, int limit) throws Throwable {
        Response response;
        Gson gson = new Gson();
        ObjectMapper objectMapper = new ObjectMapper();

        String baseContentreadUrl = ProjectUtil.getConfigValue(COMPASS_API_BASE_URL) + PropertiesCache.getInstance().getProperty(CONTENT_SEARCH_URL);
        System.out.println("baseContentreadUrl:"+baseContentreadUrl);

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk"
        );

        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        Map<String, Object> sortBy = new HashMap<>();
        Request request = new Request();
        List<String> status = new ArrayList<>();
        List<String> primaryCategory = new ArrayList<>();
        status.add(LIVE);
        filters.put(STATUS, status);

        if (header) {
            filters.put(IDENTIFIER, courseIds);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
        } else {
            primaryCategory.add(COURSE);
            primaryCategory.add(ASSESSMENT);
            filters.put(PRIMARYCATEGORY, primaryCategory);
            sortBy.put(TOTAL_NO_OF_RATINGS, DESC);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            requestMap.put(SORT_BY, sortBy);
            request.put(REQUEST, requestMap);
        }

        String requestBody = gson.toJson(request.getRequest());

        HttpResponse<String> coursesBasedOnCompetency = Unirest.post(baseContentreadUrl)
                .headers(headers)
                .body(requestBody)
                .asString();

        String jsonString = coursesBasedOnCompetency.getBody();
        response = objectMapper.readValue(jsonString, Response.class);
        System.out.println("responseOfContentSearchapi:"+response);
        return response;
    }

    private Response compositeSearchApiCall(String requestBody) throws Throwable {
        ObjectMapper objectMapper = new ObjectMapper();

        String baseCompositeUrl = ProjectUtil.getConfigValue(COMPASS_API_BASE_URL) +"/composite/v1/search";
        System.out.println("baseCompositeUrl:"+baseCompositeUrl);

        String testingUrl = ProjectUtil.getConfigValue(COMPASS_API_BASE_URL) + PropertiesCache.getInstance().getProperty(COMPOSITE_SEARCH_URL);
        System.out.println("testingUrl:"+testingUrl);

        String testingUrl2 = ProjectUtil.getConfigValue(COMPASS_API_BASE_URL) + PropertiesCache.getInstance().getProperty(COMPOSITE_SEARCH_URL);
        System.out.println("testingUrl2:"+testingUrl2);

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk"
        );

        HttpResponse<String> coursesBasedOnCompetency = Unirest.post(baseCompositeUrl)
                .headers(headers)
                .body(requestBody)
                .asString();

        String jsonString = coursesBasedOnCompetency.getBody();
        Response response = objectMapper.readValue(jsonString, Response.class);
        return response;

    }

    private Request formatRequest(boolean keywords, boolean competency, int limit, Request requestForm, List<String> keywordsList, List<String> taxonomyCategory4IdsList, List<String> taxonomyCategory3IdsList) {
        List<String> status = new ArrayList<>();
        status.add(LIVE);

        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        Request request = new Request();

        if (competency) {
            filters.put(TARGET_TAXONOMY_CATEGORY_4IDS, taxonomyCategory4IdsList);
            filters.put(STATUS, status);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
            return request;
        }
        if (keywords) {
            filters.put(KEYWORDS, keywordsList);
            filters.put(STATUS, status);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
        } else {
            filters.put(TARGET_TAXONOMY_CATEGORY_3IDS, taxonomyCategory3IdsList);
            filters.put(STATUS, status);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
            return request;
        }

        return request;
    }

    private List<String> courseIdFilters(Response response) {
        List<String> courseIDsofUserEnrolledCourses = (List<String>) response.getResult().get(COURSE_IDS);
        List<String> courseIdsForContentSearch = new ArrayList<>();
        ArrayNode courseArray = (ArrayNode) response.getResult().get(CONTENT);
        int count = 0;

        for (JsonNode node : courseArray) {
            String courseId = node.get(IDENTIFIER).asText();
            if (!courseIDsofUserEnrolledCourses.contains(courseId)) {
                courseIdsForContentSearch.add(courseId);
                count++;
            }
        }
        return courseIdsForContentSearch;
    }

    private List<String> searchUser(String userId, int limit, Request requestForm) throws Throwable {

        Gson gson = new Gson();
        Response response = new Response();

        Request request = new Request();
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        List<String> keywordsList = new ArrayList<>();
        String designation;

        filters.put(USER_ID, userId);
        requestMap.put(FILTERS, filters);
        requestMap.put(LIMIT, limit);
        request.put(REQUEST, requestMap);

        String requestBody = gson.toJson(request.getRequest());

        ObjectMapper objectMapper = new ObjectMapper();

        String baseUserSearchUrl = ProjectUtil.getConfigValue(COMPASS_API_BASE_URL) + PropertiesCache.getInstance().getProperty(SUNBIRD_USER_SEARCH_URL);
        System.out.println("baseUserSearchUrl:"+baseUserSearchUrl);

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk",
                X_AUTHENTICATED_USER_TOKEN, requestForm.getContext().get(X_AUTH_TOKEN).toString()
        );

        HttpResponse<String> userResponse = Unirest.post(baseUserSearchUrl)
                .headers(headers)
                .body(requestBody)
                .asString();

        String jsonString = userResponse.getBody();
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        JsonNode responseObject = jsonNode.get(RESULT).get(RESPONSE);

        JsonNode professionalDetailsArray = responseObject.path(CONTENT).path(0).path(PROFILE_DETAILS).path(PROFESSIONAL_DETAILS);
        JsonNode areaOfInterestArray = responseObject.path("content").path(0).path(PROFILE_DETAILS).path(PROFESSIONAL_DETAILS);

        // Check if professionalDetails array is empty
        if (!professionalDetailsArray.isEmpty()) {
            designation = professionalDetailsArray.get(0).path(DESIGNATION).asText();
            keywordsList.add(designation);
        }

        if (!areaOfInterestArray.isEmpty()) {
            JsonNode skillsArray = areaOfInterestArray.get(0).path(SKILLS);
            if (!skillsArray.isEmpty()) {
                for (JsonNode skill : skillsArray) {
                    keywordsList.add(skill.asText());
                }
            }
        }
        response.put(KEYWORDS, keywordsList);
        return keywordsList;
    }


}
