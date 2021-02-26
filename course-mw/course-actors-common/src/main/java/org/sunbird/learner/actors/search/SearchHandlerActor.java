package org.sunbird.learner.actors.search;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
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
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.JsonUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.telemetry.util.TelemetryWriter;
import scala.concurrent.Future;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class will handle search operation for all different type of index and types
 *
 * @author Manzarul
 */
public class SearchHandlerActor extends BaseActor {

  private String topn = PropertiesCache.getInstance().getProperty(JsonKey.SEARCH_TOP_N);
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private static final String CREATED_BY = "createdBy";
  
  
  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void onReceive(Request request) throws Throwable {
    request.toLower();
    Util.initializeContext(request, TelemetryEnvKey.USER);

    // set request id fto thread loacl...

    if (request.getOperation().equalsIgnoreCase(ActorOperations.COMPOSITE_SEARCH.getValue())) {
      Instant instant = Instant.now();
      Map<String, Object> searchQueryMap = request.getRequest();
      Boolean showCreator = (Boolean) searchQueryMap.remove("creatorDetails");
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
      logger.info(request.getRequestContext(), "SearchHandlerActor:onReceive  request search instant duration="
              + (Instant.now().toEpochMilli() - instant.toEpochMilli()));
      Future<Map<String, Object>> resultF = esService.search(request.getRequestContext(), searchDto, types[0]);
      result = (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
      logger.info(request.getRequestContext(), 
          "SearchHandlerActor:onReceive search complete instant duration="
              + (Instant.now().toEpochMilli() - instant.toEpochMilli()));
      if (EsType.courseBatch.getTypeName().equalsIgnoreCase(filterObjectType)) {
        if (JsonKey.PARTICIPANTS.equalsIgnoreCase(
            (String) request.getContext().get(JsonKey.PARTICIPANTS))) {
          List<Map<String, Object>> courseBatchList =
              (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
          for (Map<String, Object> courseBatch : courseBatchList) {
            courseBatch.put(
                JsonKey.PARTICIPANTS,
                getParticipantList(request.getRequestContext(), (String) courseBatch.get(JsonKey.BATCH_ID)));
          }
        }
        Response response = new Response();
        if (result != null) {
          if (BooleanUtils.isTrue(showCreator))
            populateCreatorDetails(request.getRequestContext(), result);
          if (!searchQueryMap.containsKey(JsonKey.FIELDS))
            addCollectionId(result);
          response.put(JsonKey.RESPONSE, result);
        } else {
          result = new HashMap<>();
          response.put(JsonKey.RESPONSE, result);
        }
        sender().tell(response, self());
        // create search telemetry event here ...
        generateSearchTelemetryEvent(searchDto, types, result, request.getContext());
      }
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void populateCreatorDetails(RequestContext requestContext, Map<String, Object> result) throws Exception {
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.getOrDefault("content", new ArrayList<Map<String, Object>>());
    if(CollectionUtils.isNotEmpty(content)){
	    List<String> creatorIds = content.stream().filter(map -> map.containsKey(CREATED_BY)).map(map -> (String) map.get(CREATED_BY)).collect(Collectors.toList());
        Map<String, Object> creatorDetails = getCreatorDetails(requestContext, creatorIds);
        if(MapUtils.isNotEmpty(creatorDetails)){
	      content.stream().filter(map -> creatorDetails.containsKey((String) map.get(CREATED_BY))).map(map -> map.put("creatorDetails", creatorDetails.get((String) map.get(CREATED_BY)))).collect(Collectors.toList());
        }
    }
  }

  private Map<String, Object> getCreatorDetails(RequestContext requestContext, List<String> creatorIds) throws Exception {
    String userSearchUrl = ProjectUtil.getConfigValue(JsonKey.USER_SEARCH_BASE_URL) + "/private/user/v1/search";
    List<String> fields = Arrays.asList(ProjectUtil.getConfigValue(JsonKey.CREATOR_DETAILS_FIELDS).split(","));
    String reqStr = getUserSearchRequest(creatorIds, fields);
    logger.info(requestContext, "Calling user search to fetch creator details for IDs: " + creatorIds);
	  List<Map<String, Object>> tempResult = makePostRequest(requestContext, userSearchUrl, reqStr);
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
    return JsonUtil.serialize(reqMap);
  }

  private List<Map<String, Object>> makePostRequest(RequestContext requestContext, String url, String req) throws Exception {
    HttpUtilResponse resp = HttpUtil.doPostRequest(url, req, HttpUtil.getHeader(null));
    logger.info(requestContext, "Response from user search for creator details: " + resp.getStatusCode() + " and body: " + resp.getBody());
    Response response = getResponse(resp.getBody());
    return (List<Map<String, Object>>) ((Map<String, Object>) response.getResult().getOrDefault("response", new HashMap<String, Object>())).getOrDefault("content", new ArrayList<Map<String, Object>>());    
  }

  private Response getResponse(String body) {
		Response resp = new Response();
		try {
			resp = JsonUtil.deserialize(body, Response.class);
		} catch (Exception e) {
			throw new ProjectCommonException(
					ResponseCode.unableToParseData.getErrorCode(),
					ResponseCode.unableToParseData.getErrorMessage(),
					ResponseCode.SERVER_ERROR.getResponseCode());
		}
		return resp;
  }

  private List<String> getParticipantList(RequestContext requestContext, String id) {
    UserCoursesService userCourseService = new UserCoursesService();
    return userCourseService.getEnrolledUserFromBatch(requestContext, id);
  }

  private void generateSearchTelemetryEvent(
      SearchDTO searchDto, String[] types, Map<String, Object> result, Map<String, Object> context) {

    Map<String, Object> params = new HashMap<>();
    params.put(JsonKey.TYPE, String.join(",", types));
    params.put(JsonKey.QUERY, searchDto.getQuery());
    params.put(JsonKey.FILTERS, searchDto.getAdditionalProperties().get(JsonKey.FILTERS));
    params.put(JsonKey.SORT, searchDto.getSortBy());
    params.put(JsonKey.SIZE, result.get(JsonKey.COUNT));
    params.put(JsonKey.TOPN, generateTopnResult(result)); // need to get topn value from
    // response
    Request req = new Request();
    req.setRequest(telemetryRequestForSearch(context, params));
    TelemetryWriter.write(req);
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

  private void addCollectionId(Map<String, Object> result) {
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.getOrDefault(JsonKey.CONTENT, new ArrayList<Map<String, Object>>());
    if (CollectionUtils.isNotEmpty(content)) {
      content.stream().filter(map -> map.containsKey(JsonKey.COURSE_ID)).map(map -> map.put(JsonKey.COLLECTION_ID, map.get(JsonKey.COURSE_ID))).collect(Collectors.toList());
    }
  }
}
