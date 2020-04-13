package org.sunbird.learner.actors.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.BooleanUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.KeycloakRequiredActionLinkUtil;
import org.sunbird.dto.SearchDTO;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.Util;
import org.sunbird.telemetry.util.TelemetryLmaxWriter;
import org.sunbird.telemetry.util.TelemetryUtil;
import scala.concurrent.Future;

/**
 * This class will handle search operation for all different type of index and types
 *
 * @author Manzarul
 */
public class SearchHandlerActor extends BaseActor {

  private String topn = PropertiesCache.getInstance().getProperty(JsonKey.SEARCH_TOP_N);
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private ObjectMapper mapper = new ObjectMapper();

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void onReceive(Request request) throws Throwable {
    request.toLower();
    Util.initializeContext(request, TelemetryEnvKey.USER);
    // set request id fto thread loacl...
    ExecutionContext.setRequestId(request.getRequestId());

    if (request.getOperation().equalsIgnoreCase(ActorOperations.COMPOSITE_SEARCH.getValue())) {
      Instant instant = Instant.now();
      Map<String, Object> searchQueryMap = request.getRequest();
      Boolean getCreatorFlag = (Boolean) searchQueryMap.remove("creatorDetails");
      Object objectType =
          ((Map<String, Object>) searchQueryMap.get(JsonKey.FILTERS)).get(JsonKey.OBJECT_TYPE);
      String[] types = null;
      if (objectType != null && objectType instanceof List) {
        List<String> list = (List) objectType;
        types = list.toArray(new String[list.size()]);
      }
      ((Map<String, Object>) searchQueryMap.get(JsonKey.FILTERS)).remove(JsonKey.OBJECT_TYPE);
      String filterObjectType = "";
      for (String type : types) {
        if (EsType.courseBatch.getTypeName().equalsIgnoreCase(type)) {
          filterObjectType = EsType.courseBatch.getTypeName();
        }
      }
      if (!searchQueryMap.containsKey(JsonKey.LIMIT)) {
        // set default limit for course bath as 30
        searchQueryMap.put(JsonKey.LIMIT, 30);
      }
      SearchDTO searchDto = Util.createSearchDto(searchQueryMap);

      Map<String, Object> result = null;
      ProjectLogger.log(
          "SearchHandlerActor:onReceive  request search instant duration="
              + (Instant.now().toEpochMilli() - instant.toEpochMilli()),
          LoggerEnum.INFO.name());
      Future<Map<String, Object>> resultF = esService.search(searchDto, types[0]);
      result = (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
      ProjectLogger.log(
          "SearchHandlerActor:onReceive search complete instant duration="
              + (Instant.now().toEpochMilli() - instant.toEpochMilli()),
          LoggerEnum.INFO.name());
      if (EsType.courseBatch.getTypeName().equalsIgnoreCase(filterObjectType)) {
        if (JsonKey.PARTICIPANTS.equalsIgnoreCase(
            (String) request.getContext().get(JsonKey.PARTICIPANTS))) {
          List<Map<String, Object>> courseBatchList =
              (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
          for (Map<String, Object> courseBatch : courseBatchList) {
            courseBatch.put(
                JsonKey.PARTICIPANTS,
                getParticipantList((String) courseBatch.get(JsonKey.BATCH_ID)));
          }
        }
        Response response = new Response();
        if (result != null) {
          if (BooleanUtils.isTrue(getCreatorFlag))
            populateCreatorDetails(result);
          response.put(JsonKey.RESPONSE, result);
        } else {
          result = new HashMap<>();
          response.put(JsonKey.RESPONSE, result);
        }
        sender().tell(response, self());
        // create search telemetry event here ...
        generateSearchTelemetryEvent(searchDto, types, result);
      }
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void populateCreatorDetails(Map<String, Object> result) throws Exception {
	  List<Map<String, Object>> content = (List<Map<String, Object>>) ((Map<String, Object>) result.getOrDefault("response", new HashMap<String, Object>())).getOrDefault("content", new ArrayList<Map<String, Object>>());
    if(CollectionUtils.isNotEmpty(content)){
	    List<String> creatorIds = content.stream().filter(map -> map.containsKey("createdBy")).map(map -> (String) map.get("createdBy")).collect(Collectors.toList());
        Map<String, Object> creatorDetails = getCreatorDetails(creatorIds);
        if(MapUtils.isNotEmpty(creatorDetails)){
	      content.stream().filter(map -> creatorDetails.containsKey((String) map.get("createdBy"))).map(map -> map.put("creatorDetails", creatorDetails.get((String) map.get("createdBy")))).collect(Collectors.toList());
        }
    }
  }

  private Map<String, Object> getCreatorDetails(List<String> creatorIds) throws Exception {
    String userSearchUrl = ProjectUtil.getConfigValue(JsonKey.USER_SEARCH_BASE_URL) + "/v1/user/search";
    List<String> fields = Arrays.asList(ProjectUtil.getConfigValue(JsonKey.CREATOR_DETAILS_FIELDS).split(","));
    if(!fields.contains("id"))
        fields.add("id");
    String reqStr = getUserSearchRequest(creatorIds, fields);
	  List<Map<String, Object>> tempResult = makePostRequest(userSearchUrl, reqStr);
	  return CollectionUtils.isNotEmpty(tempResult) ? tempResult.stream().collect(Collectors.toMap(s -> (String) s.remove("id"), s -> s)) : new HashMap<String, Object>();
  }

  private String getUserSearchRequest(List<String> creatorIds, List<String> fields) throws Exception {
    Map<String, Object> reqMap = new HashMap<String, Object>() {{
      put("request", new HashMap<String, Object>() {{
        put("filters", new HashMap<String, Object>() {{
          put("id", creatorIds);
        }});
        put("fields", fields);
      }});
    }};
    return mapper.writeValueAsString(reqMap);
  }

  private List<Map<String, Object>> makePostRequest(String url, String req) throws Exception {
		Map<String, String> headers = new HashMap<String, String>() {{
			put(JsonKey.CONTENT_TYPE, "application/json");
			put(JsonKey.X_AUTHENTICATED_USER_TOKEN, KeycloakRequiredActionLinkUtil.getAdminAccessToken());
		}};
		HttpUtilResponse resp = HttpUtil.doPostRequest(url, req, headers);
		Response response = getResponse(resp.getBody());
		return (List<Map<String, Object>>) ((Map<String, Object>) response.getResult().getOrDefault("response", new HashMap<String, Object>())).getOrDefault("content", new ArrayList<Map<String, Object>>());
  }

  private Response getResponse(String body) {
		Response resp = new Response();
		try {
			resp = mapper.readValue(body, Response.class);
		} catch (Exception e) {
			throw new ProjectCommonException(
					ResponseCode.unableToParseData.getErrorCode(),
					ResponseCode.unableToParseData.getErrorMessage(),
					ResponseCode.SERVER_ERROR.getResponseCode());
		}
		return resp;
  }

  private List<String> getParticipantList(String id) {
    UserCoursesService userCourseService = new UserCoursesService();
    return userCourseService.getEnrolledUserFromBatch(id);
  }

  private void generateSearchTelemetryEvent(
      SearchDTO searchDto, String[] types, Map<String, Object> result) {

    Map<String, Object> telemetryContext = TelemetryUtil.getTelemetryContext();

    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.TYPE, String.join(",", types));
    params.put(JsonKey.QUERY, searchDto.getQuery());
    params.put(JsonKey.FILTERS, searchDto.getAdditionalProperties().get(JsonKey.FILTERS));
    params.put(JsonKey.SORT, searchDto.getSortBy());
    params.put(JsonKey.SIZE, result.get(JsonKey.COUNT));
    params.put(JsonKey.TOPN, generateTopnResult(result)); // need to get topn value from
    // response
    Request req = new Request();
    req.setRequest(telemetryRequestForSearch(telemetryContext, params));
    TelemetryLmaxWriter.getInstance().submitMessage(req);
  }

  private List<Map<String, Object>> generateTopnResult(Map<String, Object> result) {

    List<Map<String, Object>> userMapList = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
    Integer topN = Integer.parseInt(topn);

    List<Map<String, Object>> list = new ArrayList<>();
    if (topN < userMapList.size()) {
      for (int i = 0; i < topN; i++) {
        Map<String, Object> m = new HashMap<>();
        m.put(JsonKey.ID, userMapList.get(i).get(JsonKey.ID));
        list.add(m);
      }
    } else {

      for (int i = 0; i < userMapList.size(); i++) {
        Map<String, Object> m = new HashMap<>();
        m.put(JsonKey.ID, userMapList.get(i).get(JsonKey.ID));
        list.add(m);
      }
    }
    return list;
  }

  private static Map<String, Object> telemetryRequestForSearch(
      Map<String, Object> telemetryContext, Map<String, Object> params) {
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CONTEXT, telemetryContext);
    map.put(JsonKey.PARAMS, params);
    map.put(JsonKey.TELEMETRY_EVENT_TYPE, "SEARCH");
    return map;
  }
}
