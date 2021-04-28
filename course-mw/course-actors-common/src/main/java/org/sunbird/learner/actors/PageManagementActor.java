package org.sunbird.learner.actors;

import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.pattern.Patterns;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.cacheloader.PageCacheLoaderService;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.JsonUtil;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.ContentSearchUtil;
import org.sunbird.learner.util.Util;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.Promise;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.TimeZone;

import static org.sunbird.common.models.util.JsonKey.ID;

/**
 * This actor will handle page management operation .
 *
 * @author Amit Kumar
 */
public class PageManagementActor extends BaseActor {

  private Util.DbInfo pageDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_MGMT_DB);
  private Util.DbInfo sectionDbInfo = Util.dbInfoMap.get(JsonKey.SECTION_MGMT_DB);
  private Util.DbInfo pageSectionDbInfo = Util.dbInfoMap.get(JsonKey.PAGE_SECTION_DB);
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private static final String DYNAMIC_FILTERS = "dynamicFilters";
  private static List<String> userProfilePropList = Arrays.asList("board");
  private LoggerUtil logger = new LoggerUtil(PageManagementActor.class);
  private static final SimpleDateFormat DATE_FORMAT = ProjectUtil.getDateFormatter();

  static {
    DATE_FORMAT.setTimeZone(
            TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
  }
  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.PAGE, this.getClass().getName());

    if(request.getOperation().equalsIgnoreCase(ActorOperations.GET_DIAL_PAGE_DATA.getValue())) {
      getDIALPageData(request);
    } else if(request.getOperation().equalsIgnoreCase(ActorOperations.GET_PAGE_DATA.getValue())) {
      getPageData(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_PAGE_SETTING.getValue())) {
      getPageSetting(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.CREATE_PAGE.getValue())) {
      createPage(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_PAGE.getValue())) {
      updatePage(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_PAGE_SETTINGS.getValue())) {
      getPageSettings(request.getRequestContext());
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.CREATE_SECTION.getValue())) {
      createPageSection(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.UPDATE_SECTION.getValue())) {
      updatePageSection(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_SECTION.getValue())) {
      getSection(request);
    } else if (request.getOperation().equalsIgnoreCase(ActorOperations.GET_ALL_SECTION.getValue())) {
      getAllSections(request.getRequestContext());
    } else {
      logger.error(request.getRequestContext(), "PageManagementActor: Invalid operation request : " + request.getOperation(), null);
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void getAllSections(RequestContext requestContext) {
    Response response = null;
    response =
        cassandraOperation.getAllRecords(requestContext, sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> result =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    for (Map<String, Object> map : result) {
      removeUnwantedData(map, "");
    }
    Response sectionMap = new Response();
    sectionMap.put(JsonKey.SECTIONS, response.get(JsonKey.RESPONSE));
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void getSection(Request actorMessage) throws Exception {
    Response response = null;
    Map<String, Object> req = actorMessage.getRequest();
    String sectionId = (String) req.get(JsonKey.ID);
    Map<String, Object> sectionMap =
        PageCacheLoaderService.getDataFromCache(
            ActorOperations.GET_SECTION.getValue(), sectionId, Map.class);

    if (sectionMap == null) {
      response =
          cassandraOperation.getRecordByIdentifier(
                  actorMessage.getRequestContext(), sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionId, null);
      List<Map<String, Object>> result =
          (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
      if (!(result.isEmpty())) {
        Map<String, Object> map = result.get(0);
        removeUnwantedData(map, "");
        Response section = new Response();
        section.put(JsonKey.SECTION, JsonUtil.convertWithDateFormat(response.get(JsonKey.RESPONSE), Map.class, DATE_FORMAT));
        PageCacheLoaderService.putDataIntoCache(
            ActorOperations.GET_SECTION.getValue(), sectionId, response.get(JsonKey.RESPONSE));
        sender().tell(section, self());
        return;
      } else {
        ProjectCommonException.throwClientErrorException(ResponseCode.sectionDoesNotExist);
      }
    } else {
      response = new Response();
      response.put(JsonKey.SECTION, JsonUtil.convertWithDateFormat(sectionMap, Map.class, DATE_FORMAT));
    }
    sender().tell(response, self());
  }

  private void updatePageSection(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      try {
        sectionMap.put(
            JsonKey.SEARCH_QUERY, mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while processing search query " + e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      try {
        sectionMap.put(
            JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while processing display " + e.getMessage(), e);
      }
    }
    sectionMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getTimeStamp());
    sectionMap = CassandraUtil.changeCassandraColumnMapping(sectionMap);

    if (!StringUtils.isBlank((String) sectionMap.get(JsonKey.ID))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.ID, (String) sectionMap.get(JsonKey.ID));
      Response res =
              cassandraOperation.getRecordsByProperties(
                      actorMessage.getRequestContext(), sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        Map<String, Object> pageSection = ((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).get(0);
        pageSection.put(JsonKey.CREATED_DATE, createdDateCheck(pageSection));
      }
    }

    sectionMap = CassandraUtil.changeCassandraColumnMapping(sectionMap);
    Response response =
        cassandraOperation.updateRecord(
                actorMessage.getRequestContext(), sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionMap);
    sender().tell(response, self());
    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) sectionMap.get(JsonKey.ID),
            TelemetryEnvKey.PAGE_SECTION,
            JsonKey.CREATE,
            null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject, actorMessage.getContext());
    // update DataCacheHandler section map with updated page section data
    updateSectionDataCache(response, sectionMap);
  }

  private void createPageSection(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    @SuppressWarnings("unchecked")
    Map<String, Object> sectionMap = (Map<String, Object>) req.get(JsonKey.SECTION);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (null != sectionMap.get(JsonKey.SEARCH_QUERY)) {
      try {
        sectionMap.put(
            JsonKey.SEARCH_QUERY, mapper.writeValueAsString(sectionMap.get(JsonKey.SEARCH_QUERY)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while processing search Query " + e.getMessage(), e);
      }
    }
    if (null != sectionMap.get(JsonKey.SECTION_DISPLAY)) {
      try {
        sectionMap.put(
            JsonKey.SECTION_DISPLAY,
            mapper.writeValueAsString(sectionMap.get(JsonKey.SECTION_DISPLAY)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while processing Section display", e);
      }
    }
    sectionMap.put(JsonKey.ID, uniqueId);
    sectionMap.put(JsonKey.STATUS, ProjectUtil.Status.ACTIVE.getValue());
    sectionMap.put(JsonKey.CREATED_DATE, ProjectUtil.getTimeStamp());
    sectionMap = CassandraUtil.changeCassandraColumnMapping(sectionMap);
    Response response =
        cassandraOperation.insertRecord(
                actorMessage.getRequestContext(), sectionDbInfo.getKeySpace(), sectionDbInfo.getTableName(), sectionMap);
    response.put(JsonKey.SECTION_ID, uniqueId);
    sender().tell(response, self());
    targetObject =
        TelemetryUtil.generateTargetObject(
            uniqueId, TelemetryEnvKey.PAGE_SECTION, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject, actorMessage.getContext());
    // update DataCacheHandler section map with new page section data
    updateSectionDataCache(response, sectionMap);
  }

  private void updateSectionDataCache(Response response, Map<String, Object> sectionMap) {
    new Thread(
            () -> {
              if ((JsonKey.SUCCESS).equalsIgnoreCase((String) response.get(JsonKey.RESPONSE))) {
                PageCacheLoaderService.putDataIntoCache(
                    ActorOperations.GET_SECTION.getValue(),
                    (String) sectionMap.get(JsonKey.ID),
                    sectionMap);
              }
            })
        .start();
  }

  @SuppressWarnings("unchecked")
  private void getPageData(Request actorMessage) throws Exception {
    String sectionQuery = null;
    Map<String, Object> filterMap = new HashMap<>();
    Map<String, Object> req = (Map<String, Object>) actorMessage.getRequest().get(JsonKey.PAGE);
    String pageName = (String) req.get(JsonKey.PAGE_NAME);
    String source = (String) req.get(JsonKey.SOURCE);
    String orgId = (String) req.get(JsonKey.ORGANISATION_ID);
    String urlQueryString = (String) actorMessage.getContext().get(JsonKey.URL_QUERY_STRING);
    Map<String, Object> sectionFilters = (Map<String, Object>) req.getOrDefault(JsonKey.SECTIONS, new HashMap<>());
    Map<String, String> headers =
        (Map<String, String>) actorMessage.getRequest().get(JsonKey.HEADER);
    filterMap.putAll(req);
    filterMap.keySet().removeAll(Arrays.asList(JsonKey.PAGE_NAME, JsonKey.SOURCE, JsonKey.ORG_CODE, JsonKey.FILTERS, JsonKey.CREATED_BY, JsonKey.SECTIONS));
    Map<String, Object> reqFilters = (Map<String, Object>) req.get(JsonKey.FILTERS);

    Map<String, Object> pageMap = getPageMapData(actorMessage.getRequestContext(), pageName, orgId);
    if (null == pageMap && StringUtils.isNotBlank(orgId)) pageMap = getPageMapData(actorMessage.getRequestContext(), pageName, "NA");

    if (null == pageMap) {
      throw new ProjectCommonException(
          ResponseCode.pageDoesNotExist.getErrorCode(),
          ResponseCode.pageDoesNotExist.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    if (source.equalsIgnoreCase(ProjectUtil.Source.WEB.getValue())) {
      if (null != pageMap && null != pageMap.get(JsonKey.PORTAL_MAP)) {
        sectionQuery = (String) pageMap.get(JsonKey.PORTAL_MAP);
      }
    } else {
      if (null != pageMap && null != pageMap.get(JsonKey.APP_MAP)) {
        sectionQuery = (String) pageMap.get(JsonKey.APP_MAP);
      }
    }
    List<Map<String,Object>> arr = null;
    try {
      arr = mapper.readValue(sectionQuery, new TypeReference<List<Map<String, Object>>>(){});
    } catch (Exception e) {
      logger.error(actorMessage.getRequestContext(), "PageManagementActor:getPageData: Exception occurred with error message =  "
              + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.errorInvalidPageSection.getErrorCode(),
          ResponseCode.errorInvalidPageSection.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    try {
      List<String> ignoredSections = new ArrayList<>();
      List<Future<Map<String, Object>>> sectionList = getSectionData(actorMessage.getRequestContext(), arr, reqFilters, urlQueryString, headers, sectionFilters, filterMap, ignoredSections);
      Future<Iterable<Map<String, Object>>> sectionsFuture = Futures.sequence(sectionList, getContext().dispatcher());
      Map<String, Object> finalPageMap = pageMap;
        Future<Response> response =
          sectionsFuture.map(
              new Mapper<Iterable<Map<String, Object>>, Response>() {
                @Override
                public Response apply(Iterable<Map<String, Object>> sections) {
                  ArrayList<Map<String, Object>> sectionList = Lists.newArrayList(sections);
                  Map<String, Object> result = new HashMap<>();
                  result.put(JsonKey.NAME, finalPageMap.get(JsonKey.NAME));
                  result.put(JsonKey.ID, finalPageMap.get(JsonKey.ID));
                  result.put(JsonKey.SECTIONS, sectionList);
                  result.put("ignoredSections", ignoredSections);
                  Response response = new Response();
                  response.put(JsonKey.RESPONSE, result);
                  logger.debug(actorMessage.getRequestContext(), "PageManagementActor:getPageData:apply: Response before caching it = "
                          + response);
                  return response;
                }
              },
              getContext().dispatcher());
      Patterns.pipe(response, getContext().dispatcher()).to(sender());

    } catch (Exception e) {
      logger.error(actorMessage.getRequestContext(), "PageManagementActor:getPageData: Exception occurred with error message = "
              + e.getMessage(), e);
    }
  }

  private List<Future<Map<String, Object>>> getSectionData(RequestContext requestContext, List<Map<String, Object>> sectionList, Map<String, Object> reqFilters, String urlQueryString, Map<String, String> headers, Map<String, Object> sectionFilters, Map<String, Object> filterMap, List<String> ignoredSections) throws Exception {
    List<Future<Map<String, Object>>> data = new ArrayList<>();
    if(CollectionUtils.isNotEmpty(sectionList)) {
      for(Map<String, Object> section : sectionList){
        String sectionId = (String) section.get(ID);
        Map<String, Object> sectionData = new HashMap<String, Object>(PageCacheLoaderService.getDataFromCache(ActorOperations.GET_SECTION.getValue(),sectionId,Map.class));
        if(MapUtils.isNotEmpty(sectionData)){
          String dynamicFilters = (String) sectionData.getOrDefault(DYNAMIC_FILTERS, "optional");
          Map<String, Object> sectionFilter = (Map<String, Object>) sectionFilters.get(sectionId);
          if(MapUtils.isEmpty(sectionFilter) && StringUtils.equalsIgnoreCase("required", dynamicFilters)){
            ProjectCommonException.throwClientErrorException(ResponseCode.errorInvalidPageSection,"Section level filers are mandatory for this section: " + sectionId);
          }
          if( MapUtils.isEmpty(sectionFilter) && StringUtils.equalsIgnoreCase("ignore", dynamicFilters)){
            ignoredSections.add(sectionId);
            continue;
          }
          Future<Map<String, Object>> contentFuture = getContentData(requestContext, sectionData, reqFilters, headers, filterMap, urlQueryString, section.get(JsonKey.GROUP), section.get(JsonKey.INDEX), sectionFilters, context().dispatcher());
          data.add(contentFuture);
        }
      }
    }
    return data;
  }

  @SuppressWarnings("unchecked")
  private void getPageSetting(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    String pageName = (String) req.get(JsonKey.ID);
    String organisationId =
        (StringUtils.isNotBlank((String) req.get(JsonKey.ORGANISATION_ID)))
            ? (String) req.get(JsonKey.ORGANISATION_ID)
            : "NA";
    Response response =
        PageCacheLoaderService.getDataFromCache(
            ActorOperations.GET_PAGE_SETTING.name(),
            organisationId + ":" + pageName,
            Response.class);
    if (response == null) {
      response =
          cassandraOperation.getRecordsByProperty(
                  actorMessage.getRequestContext(), pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), JsonKey.PAGE_NAME, pageName, null);
      List<Map<String, Object>> result =
          (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
      if (!(result.isEmpty())) {
        Map<String, Object> pageDO = result.get(0);
        if (!StringUtils.equalsIgnoreCase("NA", organisationId)) {
          List<Map<String, Object>> resp =
              result
                  .stream()
                  .filter(
                      res ->
                          (StringUtils.equalsIgnoreCase(
                              organisationId, (String) res.get(JsonKey.ORGANISATION_ID))))
                  .collect(Collectors.toList());
          if (CollectionUtils.isNotEmpty(resp)) {
            pageDO = resp.get(0);
          } else {
            throw new ProjectCommonException(
                ResponseCode.pageDoesNotExist.getErrorCode(),
                ResponseCode.pageDoesNotExist.getErrorMessage(),
                ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
          }
        }
        Map<String, Object> responseMap = getPageSetting(actorMessage.getRequestContext(), pageDO);
        response.getResult().put(JsonKey.PAGE, responseMap);
        response.getResult().remove(JsonKey.RESPONSE);
      }

      PageCacheLoaderService.putDataIntoCache(
          ActorOperations.GET_PAGE_SETTING.name(), pageName, response);
    }
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void getPageSettings(RequestContext requestContext) {
    Response response =
        PageCacheLoaderService.getDataFromCache(
            ActorOperations.GET_PAGE_SETTINGS.name(), JsonKey.PAGE, Response.class);
    List<Map<String, Object>> pageList = new ArrayList<>();
    if (response == null) {
      response =
          cassandraOperation.getAllRecords(requestContext, pageDbInfo.getKeySpace(), pageDbInfo.getTableName());
      List<Map<String, Object>> result =
          (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
      for (Map<String, Object> pageDO : result) {
        Map<String, Object> responseMap = getPageSetting(requestContext, pageDO);
        pageList.add(responseMap);
      }
      response.getResult().put(JsonKey.PAGE, pageList);
      response.getResult().remove(JsonKey.RESPONSE);

      sender().tell(response, self());
      PageCacheLoaderService.putDataIntoCache(
          ActorOperations.GET_PAGE_SETTINGS.name(), JsonKey.PAGE, response);
      return;
    }
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void updatePage(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    if (StringUtils.isBlank((String) pageMap.get(JsonKey.ORGANISATION_ID))) {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    if (!StringUtils.isBlank((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res =
          cassandraOperation.getRecordsByProperties(
                  actorMessage.getRequestContext(), pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        Map<String, Object> page = ((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).get(0);
        pageMap.put(JsonKey.CREATED_DATE, createdDateCheck(page));
        if (!(((String) page.get(JsonKey.ID)).equals(pageMap.get(JsonKey.ID)))) {
          ProjectCommonException exception =
              new ProjectCommonException(
                  ResponseCode.pageAlreadyExist.getErrorCode(),
                  ResponseCode.pageAlreadyExist.getErrorMessage(),
                  ResponseCode.CLIENT_ERROR.getResponseCode());
          sender().tell(exception, self());
          return;
        }
      }
    }
    pageMap.put(JsonKey.UPDATED_DATE, ProjectUtil.getTimeStamp());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while updating portal map data " + e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "Exception occurred while updating app map data " + e.getMessage(), e);
      }
    }
    pageMap = CassandraUtil.changeCassandraColumnMapping(pageMap);
    Response response =
        cassandraOperation.updateRecord(
                actorMessage.getRequestContext(), pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), pageMap);
    sender().tell(response, self());

    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) pageMap.get(JsonKey.ID), JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject, actorMessage.getContext());
    // update DataCacheHandler page map with updated page data
    updatePageDataCacheHandler(response, pageMap);
  }

  @SuppressWarnings("unchecked")
  private void createPage(Request actorMessage) {
    Map<String, Object> req = actorMessage.getRequest();
    Map<String, Object> pageMap = (Map<String, Object>) req.get(JsonKey.PAGE);
    // object of telemetry event...
    Map<String, Object> targetObject = new HashMap<>();
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    // default value for orgId
    String orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
    if (StringUtils.isNotBlank(orgId)) {
      validateOrg(orgId);
    } else {
      pageMap.put(JsonKey.ORGANISATION_ID, "NA");
    }
    String uniqueId = ProjectUtil.getUniqueIdFromTimestamp(actorMessage.getEnv());
    if (!StringUtils.isBlank((String) pageMap.get(JsonKey.PAGE_NAME))) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE_NAME, pageMap.get(JsonKey.PAGE_NAME));
      map.put(JsonKey.ORGANISATION_ID, pageMap.get(JsonKey.ORGANISATION_ID));

      Response res =
          cassandraOperation.getRecordsByProperties(
                  actorMessage.getRequestContext(), pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), map);
      if (!((List<Map<String, Object>>) res.get(JsonKey.RESPONSE)).isEmpty()) {
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.pageAlreadyExist.getErrorCode(),
                ResponseCode.pageAlreadyExist.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }
    }
    pageMap.put(JsonKey.ID, uniqueId);
    pageMap.put(JsonKey.CREATED_DATE, ProjectUtil.getTimeStamp());
    if (null != pageMap.get(JsonKey.PORTAL_MAP)) {
      try {
        pageMap.put(JsonKey.PORTAL_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.PORTAL_MAP)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "createPage: " + e.getMessage(), e);
      }
    }
    if (null != pageMap.get(JsonKey.APP_MAP)) {
      try {
        pageMap.put(JsonKey.APP_MAP, mapper.writeValueAsString(pageMap.get(JsonKey.APP_MAP)));
      } catch (IOException e) {
        logger.error(actorMessage.getRequestContext(), "createPage: " + e.getMessage(), e);
      }
    }
    pageMap = CassandraUtil.changeCassandraColumnMapping(pageMap);
    Response response =
        cassandraOperation.insertRecord(
                actorMessage.getRequestContext(), pageDbInfo.getKeySpace(), pageDbInfo.getTableName(), pageMap);
    response.put(JsonKey.PAGE_ID, uniqueId);
    sender().tell(response, self());
    targetObject = TelemetryUtil.generateTargetObject(uniqueId, JsonKey.PAGE, JsonKey.CREATE, null);
    TelemetryUtil.telemetryProcessingCall(
        actorMessage.getRequest(), targetObject, correlatedObject, actorMessage.getContext());

    updatePageDataCacheHandler(response, pageMap);
  }

  private void updatePageDataCacheHandler(Response response, Map<String, Object> pageMap) {
    // update DataCacheHandler page map with new page data
    new Thread(
            () -> {
              if (JsonKey.SUCCESS.equalsIgnoreCase((String) response.get(JsonKey.RESPONSE))) {
                String orgId = "NA";
                if (pageMap.containsKey(JsonKey.ORGANISATION_ID)) {
                  orgId = (String) pageMap.get(JsonKey.ORGANISATION_ID);
                }
                PageCacheLoaderService.putDataIntoCache(
                    ActorOperations.GET_PAGE_DATA.getValue(),
                    orgId + ":" + (String) pageMap.get(JsonKey.PAGE_NAME),
                    pageMap);
              }
            })
        .start();
  }

  @SuppressWarnings("unchecked")
  private Future<Map<String, Object>> getContentData(
          RequestContext requestContext, Map<String, Object> section,
          Map<String, Object> reqFilters,
          Map<String, String> headers,
          Map<String, Object> filterMap,
          String urlQueryString,
          Object group,
          Object index,
          Map<String, Object> sectionFilters,
          ExecutionContextExecutor ec)
      throws Exception {

    Map<String, Object> searchQueryMap =
        mapper.readValue((String) section.get(JsonKey.SEARCH_QUERY), HashMap.class);
    if (MapUtils.isEmpty(searchQueryMap)) {
      searchQueryMap = new HashMap<String, Object>();
      searchQueryMap.put(JsonKey.REQUEST, new HashMap<String, Object>());
    }
    Map<String, Object> request = (Map<String, Object>) searchQueryMap.get(JsonKey.REQUEST);

    for (Entry<String, Object> entry : filterMap.entrySet()) {
      if (!entry.getKey().equalsIgnoreCase(JsonKey.FILTERS)) {
        request.put(entry.getKey(), entry.getValue());
      }
    }
    request.put("limit", 10);

    Map<String, Object> filters = (Map<String, Object>) request.getOrDefault(JsonKey.FILTERS, new HashMap<String, Object>());
    if(sectionFilters.containsKey(section.get(ID))){
      applySectionLevelFilters((String)section.get(ID), sectionFilters, filters);
    } else {
      applyFilters(filters, reqFilters);
    }
    
    String queryRequestBody = mapper.writeValueAsString(searchQueryMap);
    if (StringUtils.isBlank(queryRequestBody)) {
      queryRequestBody = (String) section.get(JsonKey.SEARCH_QUERY);
    }

    Future<Map<String, Object>> result = null;
    String dataSource = (String) section.get(JsonKey.DATA_SOURCE);
    section.put(JsonKey.GROUP, group);
    section.put(JsonKey.INDEX, index);
    if (StringUtils.isEmpty(dataSource) || JsonKey.CONTENT.equalsIgnoreCase(dataSource)) {
      result = ContentSearchUtil.searchContent(requestContext, urlQueryString, queryRequestBody, headers, ec);
      final String finalQueryRequestBody = queryRequestBody;
      return result.map(
          new Mapper<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> apply(Map<String, Object> result) {
              if (MapUtils.isNotEmpty(result)) {
                section.putAll(result);
                Map<String, Object> tempMap = (Map<String, Object>) result.get(JsonKey.PARAMS);
                section.remove(JsonKey.PARAMS);
                section.put(JsonKey.RES_MSG_ID, tempMap.get(JsonKey.RES_MSG_ID));
                section.put(JsonKey.API_ID, tempMap.get(JsonKey.API_ID));
                removeUnwantedData(section, "getPageData");
                section.put(JsonKey.SEARCH_QUERY, finalQueryRequestBody);
                logger.debug(requestContext, "PageManagementActor:getContentData:apply: section = " + section);
              }
              return section;
            }
          },
          getContext().dispatcher());
    } else {
      Map<String, Object> esResponse =
          searchFromES(requestContext, (Map<String, Object>) searchQueryMap.get(JsonKey.REQUEST), dataSource);
      section.put(JsonKey.COUNT, esResponse.get(JsonKey.COUNT));
      section.put(JsonKey.CONTENTS, esResponse.get(JsonKey.CONTENT));
      removeUnwantedData(section, "getPageData");
      final Promise promise = Futures.promise();
      promise.success(section);
      result = promise.future();
      return result;
    }
  }

  private void applySectionLevelFilters(String sectionId, Map<String, Object> sectionFilters, Map<String, Object> filters) {
    Map<String, Object> sectionFilter = (Map<String, Object>) sectionFilters.get(sectionId);
    if(MapUtils.isNotEmpty(sectionFilter)){
      filters.putAll((Map<String, Object>) sectionFilter.getOrDefault(JsonKey.FILTERS, new HashMap<String, Object>()));
    }
  }

  private Map<String, Object> searchFromES(RequestContext requestContext, Map<String, Object> map, String dataSource) {
    SearchDTO searcDto = new SearchDTO();
    searcDto.setQuery((String) map.get(JsonKey.QUERY));
    searcDto.setLimit((Integer) map.get(JsonKey.LIMIT));
    searcDto.getAdditionalProperties().put(JsonKey.FILTERS, map.get(JsonKey.FILTERS));
    searcDto.setSortBy((Map<String, Object>) map.get(JsonKey.SORT_BY));
    String type = "";
    if (JsonKey.BATCH.equalsIgnoreCase(dataSource)) {
      type = ProjectUtil.EsType.courseBatch.getTypeName();
    } else {
      return null;
    }

    Future<Map<String, Object>> resultF = esService.search(requestContext, searcDto, type);
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    return result;
  }

  @SuppressWarnings("unchecked")
  /**
   * combine both requested page filters with default page filters.
   *
   * @param filters
   * @param reqFilters
   */
  private void applyFilters(Map<String, Object> filters, Map<String, Object> reqFilters) {
    if (null != reqFilters) {
      reqFilters.entrySet().forEach(entry -> {
        if (filters.containsKey(entry.getKey())) {
          String key = entry.getKey();
          if (entry.getValue() instanceof List) {
            if (filters.get(key) instanceof List) {
              Set<Object> set = new HashSet<>((List<Object>) filters.get(key));
              set.addAll((List<Object>) entry.getValue());
              ((List<Object>) filters.get(key)).clear();
              ((List<Object>) filters.get(key)).addAll(set);
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, entry.getValue());
            } else {
              List<Object> list = new ArrayList<>();
              list.addAll((List<Object>) entry.getValue());
              if (!(((List<Object>) entry.getValue()).contains(filters.get(key)))) {
                list.add(filters.get(key));
              }
              filters.put(key, list);
            }
          } else if (entry.getValue() instanceof Map) {
            filters.put(key, entry.getValue());
          } else {
            if (filters.get(key) instanceof List) {
              if (!(((List<Object>) filters.get(key)).contains(entry.getValue()))) {
                ((List<Object>) filters.get(key)).add(entry.getValue());
              }
            } else if (filters.get(key) instanceof Map) {
              filters.put(key, entry.getValue());
            } else {
              List<Object> list = new ArrayList<>();
              list.add(filters.get(key));
              list.add(entry.getValue());
              filters.put(key, list);
            }
          }
        } else {
          filters.put(entry.getKey(), entry.getValue());
        }
      });
    }
  }
  
  private Map<String, Object> getPageSetting(RequestContext requestContext, Map<String, Object> pageDO) {

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put(JsonKey.NAME, pageDO.get(JsonKey.NAME));
    responseMap.put(JsonKey.ID, pageDO.get(JsonKey.ID));

    if (null != pageDO.get(JsonKey.ORGANISATION_ID)) {
      responseMap.put(JsonKey.ORGANISATION_ID, pageDO.get(JsonKey.ORGANISATION_ID));
    }

    if (pageDO.containsKey(JsonKey.APP_MAP) && null != pageDO.get(JsonKey.APP_MAP)) {
      responseMap.put(JsonKey.APP_SECTIONS, parsePage(requestContext, pageDO, JsonKey.APP_MAP));
    }
    if (pageDO.containsKey(JsonKey.PORTAL_MAP) && null != pageDO.get(JsonKey.PORTAL_MAP)) {
      responseMap.put(JsonKey.PORTAL_SECTIONS, parsePage(requestContext, pageDO, JsonKey.PORTAL_MAP));
    }
    return responseMap;
  }

  private void removeUnwantedData(Map<String, Object> map, String from) {
    map.remove(JsonKey.CREATED_DATE);
    map.remove(JsonKey.CREATED_BY);
    map.remove(JsonKey.UPDATED_DATE);
    map.remove(JsonKey.UPDATED_BY);
    if (from.equalsIgnoreCase("getPageData")) {
      map.remove(JsonKey.STATUS);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parsePage(RequestContext requestContext, Map<String, Object> pageDO, String mapType) {
    List<Map<String, Object>> sections = new ArrayList<>();
    String sectionQuery = (String) pageDO.get(mapType);
    try {
      Object[] arr = mapper.readValue(sectionQuery, Object[].class);
      for (Object obj : arr) {
        Map<String, Object> sectionMap = (Map<String, Object>) obj;
        Response sectionResponse =
            cassandraOperation.getRecordByIdentifier(
                    requestContext, pageSectionDbInfo.getKeySpace(),
                pageSectionDbInfo.getTableName(),
                (String) sectionMap.get(JsonKey.ID), null);

        List<Map<String, Object>> sectionResult =
            (List<Map<String, Object>>) sectionResponse.getResult().get(JsonKey.RESPONSE);
        if (null != sectionResult && !sectionResult.isEmpty()) {
          sectionResult.get(0).put(JsonKey.GROUP, sectionMap.get(JsonKey.GROUP));
          sectionResult.get(0).put(JsonKey.INDEX, sectionMap.get(JsonKey.INDEX));
          removeUnwantedData(sectionResult.get(0), "");
          sections.add(sectionResult.get(0));
        }
      }
    } catch (Exception e) {
      logger.error(requestContext, "parsePage: " + e.getMessage(), e);
    }
    return sections;
  }

  private void validateOrg(String orgId) {
    Map<String, Object> result = userOrgService.getOrganisationById(orgId);
    if (MapUtils.isEmpty(result) || !orgId.equals(result.get(ID))) {
      throw new ProjectCommonException(
          ResponseCode.invalidOrgId.getErrorCode(),
          ResponseCode.invalidOrgId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private Map<String, Object> getPageMapData(RequestContext requestContext, String pageName, String orgId) {
    /** if orgId is not then consider default page */
    if (StringUtils.isBlank(orgId)) {
      orgId = "NA";
    }
    logger.info(requestContext, "Fetching data from Cache for " + orgId + ":" + pageName);
    Map<String, Object> pageMapData =
        PageCacheLoaderService.getDataFromCache(
            ActorOperations.GET_PAGE_DATA.getValue(), orgId + ":" + pageName, Map.class);

    return pageMapData;
  }
  
  private void getDIALPageData(Request request) {
    Map<String, Object> req = (Map<String, Object>) request.getRequest().get(JsonKey.PAGE);
    String pageName = (String) req.get(JsonKey.PAGE_NAME);
    String source = (String) req.get(JsonKey.SOURCE);
    String orgId = (String) req.get(JsonKey.ORGANISATION_ID);
    String urlQueryString = (String) request.getContext().get(JsonKey.URL_QUERY_STRING);
    Map<String, Object> sectionFilters = (Map<String, Object>) req.getOrDefault(JsonKey.SECTIONS, new HashMap<>());
    Map<String, String> headers = (Map<String, String>) request.getRequest().get(JsonKey.HEADER);

    Map<String, Object> filterMap = new HashMap<>();
    filterMap.putAll(req);
    filterMap.keySet().removeAll(Arrays.asList(JsonKey.PAGE_NAME, JsonKey.SOURCE, JsonKey.ORG_CODE, JsonKey.FILTERS, JsonKey.CREATED_BY, JsonKey.SECTIONS));
    
    Map<String, Object> reqFilters = (Map<String, Object>) req.get(JsonKey.FILTERS);
    Map<String, Object> userProfile = (Map<String, Object>) req.getOrDefault("userProfile", new HashMap<String, Object>());

    Map<String, Object> pageMap = getPageMapData(request.getRequestContext(), pageName, orgId);
    if (null == pageMap && StringUtils.isNotBlank(orgId)) pageMap = getPageMapData(request.getRequestContext(), pageName, "NA");

    if (null == pageMap) {
      throw new ProjectCommonException(
              ResponseCode.pageDoesNotExist.getErrorCode(),
              ResponseCode.pageDoesNotExist.getErrorMessage(),
              ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }

    String sectionQuery = null;
      if (source.equalsIgnoreCase(ProjectUtil.Source.WEB.getValue())) {
        sectionQuery = (String) pageMap.getOrDefault(JsonKey.PORTAL_MAP, "");
      } else {
        sectionQuery = (String) pageMap.getOrDefault(JsonKey.APP_MAP, "");
      }  
    
    
    try {
      List<Map<String,Object>> arr = mapper.readValue(sectionQuery, new TypeReference<List<Map<String, Object>>>(){});
      List<String> ignoredSections = new ArrayList<>();
      List<Future<Map<String, Object>>> sectionList = getSectionData(request.getRequestContext(), arr, reqFilters, urlQueryString, headers, sectionFilters, filterMap, ignoredSections);
      Future<Iterable<Map<String, Object>>> sectionsFuture = Futures.sequence(sectionList, getContext().dispatcher());
      Map<String, Object> finalPageMap = pageMap;
      Future<Response> response =
              sectionsFuture.map(
                      new Mapper<Iterable<Map<String, Object>>, Response>() {
                        @Override
                        public Response apply(Iterable<Map<String, Object>> sections) {
                          List<Map<String, Object>> sectionList = getUserProfileData(request.getRequestContext(), Lists.newArrayList(sections), userProfile);
                          Map<String, Object> result = new HashMap<>();
                          result.put(JsonKey.NAME, finalPageMap.get(JsonKey.NAME));
                          result.put(JsonKey.ID, finalPageMap.get(JsonKey.ID));
                          result.put(JsonKey.SECTIONS, sectionList);
                          result.put("ignoredSections", ignoredSections);
                          Response response = new Response();
                          response.put(JsonKey.RESPONSE, result);
                          logger.debug(request.getRequestContext(), "PageManagementActor:getPageData:apply: Response before caching it = "
                                  + response);
                          return response;
                        }
                      },
                      getContext().dispatcher());
      Patterns.pipe(response, getContext().dispatcher()).to(sender());
    }
    catch (Exception e) {
      logger.error(request.getRequestContext(), "PageManagementActor:getPageData: Exception occurred with error message = "
              + e.getMessage(), e);
    }
  }

  private List<Map<String, Object>> getUserProfileData(RequestContext requestContext, List<Map<String, Object>> sectionList, Map<String, Object> userProfile) {
    List<Map<String, Object>> filteredSectionsContents = sectionList.stream().filter(section -> CollectionUtils.isNotEmpty((List<Map<String, Object>>) section.get("contents"))).collect(Collectors.toList());
    List<Map<String, Object>> filteredSectionsCollections = sectionList.stream().filter(section -> (Integer) section.getOrDefault("collectionsCount", 0) > 0).collect(Collectors.toList());
    // if user profile is empty - take only origin content (collections or contents)
    if (MapUtils.isEmpty(userProfile)) {
      if(CollectionUtils.isNotEmpty(filteredSectionsCollections)){
        filterData(filteredSectionsCollections, new HashMap<String, Object>(), "collections");
      } else if (CollectionUtils.isNotEmpty(filteredSectionsContents)) {
        filterData(filteredSectionsContents, new HashMap<String, Object>(), "contents");
      }
    } else {
      Map<String, Object> filteredUserProfile = userProfilePropList.stream().collect(Collectors.toMap(key -> key, key -> userProfile.get(key)));
      filteredUserProfile.values().removeIf(Objects::isNull);
      if (MapUtils.isNotEmpty(filteredUserProfile)) {
        if(CollectionUtils.isNotEmpty(filteredSectionsCollections)){
          filterData(filteredSectionsCollections, filteredUserProfile, "collections");
        } else if (CollectionUtils.isNotEmpty(filteredSectionsContents)) {
          filterData(filteredSectionsContents, filteredUserProfile, "contents");
        }
      }
    }
    logger.info(requestContext, "PageManagementActor:getUserProfileData ::::: final value returned = " + sectionList);
    return sectionList;
  }

  private void filterData(List<Map<String, Object>> filteredSections, Map<String, Object> filteredUserProfile, String param) {
    if (CollectionUtils.isNotEmpty(filteredSections)) {
      for (Map<String, Object> section : filteredSections) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) section.get(param);
        List<Map<String, Object>> originData = data.stream().filter(content -> (!((String) content.getOrDefault("originData", "")).contains("shallow"))).collect(Collectors.toList());
        List<Map<String, Object>> shallowCopiedData = data.stream().filter(content -> ((String) content.getOrDefault("originData", "")).contains("shallow")).collect(Collectors.toList());
        if (MapUtils.isNotEmpty(filteredUserProfile)) {
          List<Map<String, Object>> filteredShallowCopied = shallowCopiedData.stream().filter(content -> {
            List<String> matchedProps = new ArrayList<>();
            filteredUserProfile.entrySet().forEach(entry -> {
              List<String> userProfileVal = getStringListFromObj(entry.getValue());
              List<String> contentVal = getStringListFromObj(content.getOrDefault(entry.getKey(), ""));
              if (CollectionUtils.containsAny(contentVal, userProfileVal)) matchedProps.add(entry.getKey());
            });
            return matchedProps.containsAll(filteredUserProfile.keySet());
          }).collect(Collectors.toList());
          // if user profile matched with data - take only shallow copied data (contents or collections.)
          if (CollectionUtils.isNotEmpty(filteredShallowCopied)) {
            section.put(param, filteredShallowCopied);
            String key = StringUtils.equalsIgnoreCase("collections", param)? "collectionsCount" : "count";
            section.put(key, filteredShallowCopied.size());
          } else {
            // if user profile not matched with data (e.g: board) - take only origin data (contents or collections)
            section.put(param, originData);
            String key = StringUtils.equalsIgnoreCase("collections", param)? "collectionsCount" : "count";
            section.put(key, originData.size());
          }
        } else {
          section.put(param, originData);
          String key = StringUtils.equalsIgnoreCase("collections", param)? "collectionsCount" : "count";
          section.put(key, originData.size());
        }
      }
    }
  }

  private List<String> getStringListFromObj(Object obj) {
    if(obj instanceof List){
      return (List<String>) obj;
    } else {
      return Arrays.asList((String)obj);
    }
  }

  // Remove this implementation after deprecating text date columns
  private Date createdDateCheck(Map<String, Object> page) {
    try {
      if (page.containsKey(JsonKey.CREATED_DATE) && page.get(JsonKey.CREATED_DATE) == null) {
        return DATE_FORMAT.parse((String) page.get(JsonKey.OLD_CREATED_DATE));
      }
    } catch (ParseException e) {
      logger.error(null, "PageManagementActor:createdDateCheck: Exception occurred with error message = " + e.getMessage(), e);
    }
    return (Date) page.get(JsonKey.CREATED_DATE);
  }
}
