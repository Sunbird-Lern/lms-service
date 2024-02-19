package org.sunbird.recommend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.jclouds.json.Json;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.coursebatch.CourseBatchManagementActor;
import org.sunbird.learner.util.Util;

import java.util.*;

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
            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void getRecommendedCourse(Request request) throws Throwable {

        Response finalResponse;
        Map<String,Object> data = request.getRequest();

        int limit = (int) data.get(LIMIT);

        if (data.containsKey(COMPETENCY)) {
            String competency = data.get(COMPETENCY).toString();
            String userId = (String) request.getContext().getOrDefault(REQUESTED_FOR, request.getContext().get(REQUESTED_BY));
            Response response = getUserEnrolledCourses(request,competency);
            finalResponse = getCourses(response,limit,competency,userId,request);
        } else {
            finalResponse = contentSearchApiCall(null, false, limit);
        }
        sender().tell(finalResponse, self());
    }


    private Response getUserEnrolledCourses(Request request,String competency) throws Throwable {

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
        List<String> taxonomyCategoryIdsList = new ArrayList<>();

        int count = 0;
        if (courseNode.isArray()) {
            for (JsonNode course : courseNode) {
                String courseId = course.get(COURSE_ID).asText();
                courseIds.add(courseId);
                JsonNode taxonomyCategoryIdsNode = course.path(CONTENT).path(competency);
                if (taxonomyCategoryIdsNode.isArray() && taxonomyCategoryIdsNode.size() > 0) {
                    for (JsonNode idNode : taxonomyCategoryIdsNode) {
                        String idValue = idNode.asText();
                        if (!taxonomyCategoryIdsList.contains(idValue))
                            taxonomyCategoryIdsList.add(idValue);
                    }
                }
                count++;
            }
        }

        response.put(COURSE_IDS, courseIds);
        response.put(COMPETENCY, taxonomyCategoryIdsList);
        return response;

    }

    private Response getCourses(Response response, int limit,String competency,String userId,Request requestForm) throws Throwable {

        Request request ;
        String requestBody;
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> courseIds ;
        Gson gson = new Gson();
        ArrayNode combinedContent = objectMapper.createArrayNode();
        Response newResponse ;
        List<String> keywordsList = null;

        /*** To get based on competency ***/
        request = formatRequest(response, true, limit,competency,keywordsList);
        requestBody = gson.toJson(request.getRequest());
        Response response1 = compositeSearchApiCall(requestBody);
        Object contentObject1 = response1.getResult().get(CONTENT);
        if (contentObject1 != null) {
            ArrayList<Object> contentArrayList1 = (ArrayList<Object>) contentObject1;
            ArrayNode contentNode1 = objectMapper.valueToTree(contentArrayList1);
            combinedContent.addAll(contentNode1);
        }


        /*** To get based on AreaOfInterest ***/
        keywordsList = searchUser(userId, limit, requestForm);
        if(!keywordsList.isEmpty()) {
            request = formatRequest(response, false, limit, competency, keywordsList);
            requestBody = gson.toJson(request.getRequest());
            Response response2 = compositeSearchApiCall(requestBody);
            Object contentObject2 = response2.getResult().get(CONTENT);
            if (contentObject2 != null) {
                ArrayList<Object> contentArrayList2 = (ArrayList<Object>) contentObject2;
                ArrayNode contentNode2 = objectMapper.valueToTree(contentArrayList2);
                combinedContent.addAll(contentNode2);
            }
        }

        /*** To get based on user skills ***/
        response.getResult().put(CONTENT, combinedContent);

        /*** To get list of recommended courses ***/
        courseIds = courseIdFilters(response);
        newResponse = contentSearchApiCall(courseIds, true, limit);
        return newResponse;
    }

    private Response contentSearchApiCall(List<String> courseIds, boolean header, int limit) throws Throwable {
        Response response;
        Gson gson = new Gson();
        ObjectMapper objectMapper = new ObjectMapper();

        String urlString = "https://compass-dev.tarento.com/api/content/v1/search";

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

        HttpResponse<String> coursesBasedOnCompetency = Unirest.post(urlString)
                .headers(headers)
                .body(requestBody)
                .asString();

        String jsonString = coursesBasedOnCompetency.getBody();
        response = objectMapper.readValue(jsonString, Response.class);
        return response;
    }

    private Response compositeSearchApiCall(String requestBody) throws Throwable {

        ObjectMapper objectMapper = new ObjectMapper();
        String urlString = "https://compass-dev.tarento.com/api/composite/v1/search";

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk"
        );

        HttpResponse<String> coursesBasedOnCompetency = Unirest.post(urlString)
                .headers(headers)
                .body(requestBody)
                .asString();

        String jsonString = coursesBasedOnCompetency.getBody();
        Response response = objectMapper.readValue(jsonString, Response.class);
        return response;

    }

    private Request formatRequest(Response response, boolean competency, int limit,String competencyKey,List<String> keywordsList) {
        List<String> status = new ArrayList<>();
        status.add(LIVE);

        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> filters = new HashMap<>();
        Request request = new Request();

        if (competency) {
            List<String> competencyValue = (List<String>) response.getResult().get(COMPETENCY);
            List<String> taxonomyCategoryIdsList = new ArrayList<>(competencyValue);
            filters.put(competencyKey, taxonomyCategoryIdsList);
            filters.put(STATUS, status);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
            return request;
        } else {
            filters.put(KEYWORDS, keywordsList);
            filters.put(STATUS, status);
            requestMap.put(FILTERS, filters);
            requestMap.put(OFFSET, 0);
            requestMap.put(LIMIT, limit);
            request.put(REQUEST, requestMap);
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
                count ++;
            }
        }
        return courseIdsForContentSearch;
    }

    private List<String> searchUser (String userId,int limit,Request requestForm) throws  Throwable{

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
        String urlString = "https://compass-dev.tarento.com/api/user/v1/search";

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiI0WEFsdFpGMFFhc1JDYlFnVXB4b2RvU2tLRUZyWmdpdCJ9.mXD7cSvv3Le6o_32lJplDck2D0IIMHnv0uJKq98YVwk",
                X_AUTHENTICATED_USER_TOKEN, requestForm.getContext().get(X_AUTH_TOKEN).toString()
        );

        HttpResponse<String> userResponse = Unirest.post(urlString)
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

        if(!areaOfInterestArray.isEmpty()){
            JsonNode skillsArray = areaOfInterestArray.get(0).path(SKILLS);
            if (!skillsArray.isEmpty()) {
                for (JsonNode skill : skillsArray) {
                    keywordsList.add(skill.asText());
                }
            }
        }
        response.put(KEYWORDS,keywordsList);
        return keywordsList;

    }
}
