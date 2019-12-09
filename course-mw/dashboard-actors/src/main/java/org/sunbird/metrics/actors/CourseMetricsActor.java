package org.sunbird.metrics.actors;

import static org.sunbird.common.models.util.ProjectUtil.isNotNull;
import static org.sunbird.common.models.util.ProjectUtil.isNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.CloudStorageUtil;
import org.sunbird.common.util.CloudStorageUtil.CloudStorageType;
import org.sunbird.dto.SearchDTO;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.ContentSearchUtil;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

public class CourseMetricsActor extends BaseMetricsActor {

  protected static final String CONTENT_ID = "content_id";
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
  private static ObjectMapper mapper = new ObjectMapper();
  private static final String COMPLETE_PERCENT = "completionPercentage";

  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);

  @Override
  public void onReceive(Request request) throws Throwable {
    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "courseProgressMetrics":
        courseProgressMetrics(request);
        break;
      case "courseConsumptionMetrics":
        courseConsumptionMetrics(request);
        break;
      case "courseProgressMetricsV2":
        courseProgressMetricsV2(request);
        break;
      case "courseProgressMetricsReport":
        courseProgressMetricsReport(request);
        break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
    }
  }

  private void courseProgressMetricsV2(Request actorMessage) {
    ProjectLogger.log("CourseMetricsActor: courseProgressMetrics called.", LoggerEnum.INFO.name());
    Integer limit = (Integer) actorMessage.getContext().get(JsonKey.LIMIT);
    String sortBy = (String) actorMessage.getContext().get(JsonKey.SORTBY);
    String batchId = (String) actorMessage.getContext().get(JsonKey.BATCH_ID);
    Integer offset = (Integer) actorMessage.getContext().get(JsonKey.OFFSET);
    String userName = (String) actorMessage.getContext().get(JsonKey.USERNAME);
    String sortOrder = (String) actorMessage.getContext().get(JsonKey.SORT_ORDER);

    String requestedBy = (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY);
    validateUserId(requestedBy);
    Map<String, Object> courseBatchResult = validateAndGetCourseBatch(batchId);
    Map<String, Object> filter = new HashMap<>();
    filter.put(JsonKey.BATCH_ID, batchId);

    SearchDTO searchDTO = new SearchDTO();
    if (!StringUtils.isEmpty(userName)) {
      searchDTO.setQuery(userName);
      searchDTO.setQueryFields(Arrays.asList(JsonKey.NAME));
    }
    searchDTO.setLimit(limit);
    searchDTO.setOffset(offset);
    if (!StringUtils.isEmpty(sortBy)) {
      Map<String, Object> sortMap = new HashMap<>();
      sortBy = getSortyBy(sortBy);
      if (StringUtils.isEmpty(sortOrder)) {
        sortMap.put(sortBy, JsonKey.ASC);
      } else {
        sortMap.put(sortBy, sortOrder);
      }
      searchDTO.setSortBy(sortMap);
    }

    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filter);

    Future<Map<String, Object>> resultF =
        esService.search(searchDTO, EsType.cbatchstats.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (isNull(result) || result.size() == 0) {
      ProjectLogger.log(
          "CourseMetricsActor:courseProgressMetricsV2: No search results found.",
          LoggerEnum.INFO.name());
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId);
    }
    List<Map<String, Object>> esContents = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
    Map<String, Object> courseProgressResult = new HashMap<>();
    List<Map<String, Object>> userData = new ArrayList<>();
    Map<String, Object> usersCertificates = new HashMap<>();
    if (CollectionUtils.isEmpty(esContents)) {
      courseProgressResult.put(JsonKey.SHOW_DOWNLOAD_LINK, false);
    } else {
      courseProgressResult.put(JsonKey.SHOW_DOWNLOAD_LINK, true);
      List<String> userIds =
          esContents
              .stream()
              .map(statUser -> (String) statUser.get(JsonKey.USER_ID))
              .collect(Collectors.toList());
      usersCertificates = getUsersCertificates(batchId, userIds);
      for (Map<String, Object> esContent : esContents) {
        Map<String, Object> map = new HashMap<>();
        map.put(JsonKey.USER_NAME, esContent.get(JsonKey.NAME));
        map.put(JsonKey.MASKED_PHONE, esContent.get(JsonKey.MASKED_PHONE));
        map.put(JsonKey.ORG_NAME, esContent.get(JsonKey.ROOT_ORG_NAME));
        map.put(JsonKey.PROGRESS, esContent.get(JsonKey.COMPLETED_PERCENT));
        map.put(JsonKey.ENROLLED_ON, esContent.get(JsonKey.ENROLLED_ON));
        if (MapUtils.isNotEmpty(usersCertificates)
            && usersCertificates.containsKey(esContent.get(JsonKey.USER_ID))) {
          map.put(
              CourseJsonKey.CERTIFICATES, usersCertificates.get(esContent.get(JsonKey.USER_ID)));
        }
        userData.add(map);
      }
    }

    courseProgressResult.put(JsonKey.COUNT, courseBatchResult.get(JsonKey.PARTICIPANT_COUNT));
    courseProgressResult.put(CourseJsonKey.CERTIFICATE_COUNT, usersCertificates.size());
    courseProgressResult.put(JsonKey.DATA, userData);
    courseProgressResult.put(JsonKey.START_DATE, courseBatchResult.get(JsonKey.START_DATE));
    courseProgressResult.put(JsonKey.END_DATE, courseBatchResult.get(JsonKey.END_DATE));
    courseProgressResult.put(
        JsonKey.COMPLETED_COUNT, courseBatchResult.get(JsonKey.COMPLETED_COUNT));
    courseProgressResult.put(
        JsonKey.REPORT_UPDATED_ON, courseBatchResult.get(JsonKey.REPORT_UPDATED_ON));
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    response.getResult().putAll(courseProgressResult);
    sender().tell(response, self());
  }

  private Map<String, Object> getUsersCertificates(String batchId, List<String> userIds) {
    SearchDTO searchDTO = new SearchDTO();
    searchDTO.setLimit(userIds.size());
    Map<String, Object> filter = new HashMap<>();
    filter.put(JsonKey.BATCH_ID, batchId);
    filter.put(JsonKey.USER_ID, userIds);
    Map<String, String> mandatoryNestedFieldsByPath = new HashMap<>();
    mandatoryNestedFieldsByPath.put(
        CourseJsonKey.CERTIFICATES_DOT_NAME, CourseJsonKey.CERTIFICATES);
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filter);
    searchDTO.getAdditionalProperties().put(JsonKey.NESTED_EXISTS, mandatoryNestedFieldsByPath);
    searchDTO.setFields(Arrays.asList(JsonKey.USER_ID, CourseJsonKey.CERTIFICATES));
    Future<Map<String, Object>> resultF =
        esService.search(searchDTO, EsType.usercourses.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    ProjectLogger.log(
        "CourseMetricsActor:getUsersCertificates: result=" + result, LoggerEnum.INFO.name());
    Map<String, Object> resultMap = new HashMap<>();
    if (MapUtils.isNotEmpty(result)
        && CollectionUtils.isNotEmpty((List<Map<String, Object>>) result.get(JsonKey.CONTENT))) {
      List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
      resultMap =
          contents
              .stream()
              .collect(
                  Collectors.toMap(
                      user -> (String) user.get(JsonKey.USER_ID),
                      user -> user.get(CourseJsonKey.CERTIFICATES)));
    }
    return resultMap;
  }

  private Map<String, Object> validateAndGetCourseBatch(String batchId) {
    if (StringUtils.isBlank(batchId)) {
      ProjectLogger.log(
          "CourseMetricsActor:validateAndGetCourseBatch: batchId is invalid (blank).",
          LoggerEnum.INFO.name());
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId);
    }
    // check batch exist in ES or not
    Future<Map<String, Object>> courseBatchResultF =
        esService.getDataByIdentifier(EsType.courseBatch.getTypeName(), batchId);
    Map<String, Object> courseBatchResult =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(courseBatchResultF);
    if (isNull(courseBatchResult) || courseBatchResult.size() == 0) {
      ProjectLogger.log(
          "CourseMetricsActor:validateAndGetCourseBatch: batchId not found.",
          LoggerEnum.INFO.name());
      ProjectCommonException.throwClientErrorException(ResponseCode.invalidCourseBatchId);
    }
    return courseBatchResult;
  }

  private void validateUserId(String requestedBy) {
    Map<String, Object> requestedByInfo = userOrgService.getUserById(requestedBy);
    if (isNull(requestedByInfo)
        || StringUtils.isBlank((String) requestedByInfo.get(JsonKey.FIRST_NAME))) {
      throw new ProjectCommonException(
          ResponseCode.invalidUserId.getErrorCode(),
          ResponseCode.invalidUserId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private void courseProgressMetricsReport(Request actorMessage) {

    ProjectLogger.log(
        "CourseMetricsActor: courseProgressMetricsReport called.", LoggerEnum.INFO.name());
    SimpleDateFormat simpleDateFormat = ProjectUtil.getDateFormatter();
    simpleDateFormat.setLenient(false);

    String requestedBy = (String) actorMessage.get(JsonKey.REQUESTED_BY);
    Map<String, Object> requestedByInfo = userOrgService.getUserById(requestedBy);
    if (isNull(requestedByInfo)
        || StringUtils.isBlank((String) requestedByInfo.get(JsonKey.FIRST_NAME))) {
      throw new ProjectCommonException(
          ResponseCode.invalidUserId.getErrorCode(),
          ResponseCode.invalidUserId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }

    String batchId = (String) actorMessage.getRequest().get(JsonKey.BATCH_ID);
    validateAndGetCourseBatch(batchId);
    String courseMetricsContainer =
        ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_METRICS_CONTANER);
    String courseMetricsReportFolder =
        ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_METRICS_REPORT_FOLDER);
    String reportPath = courseMetricsReportFolder + File.separator + "report-" + batchId + ".csv";

    // check assessment report location exist in ES or not

    Map<String, Object> filter = new HashMap<>();
    filter.put(JsonKey.BATCH_ID, batchId);
    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filter);

    Future<Map<String, Object>> assessmentBatchResultF =
        esService.search(searchDTO, EsType.cbatchassessment.getTypeName());
    Map<String, Object> assessmentBatchResult =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(assessmentBatchResultF);
    String assessmentReportSignedUrl = null;
    ProjectLogger.log(
        "CourseMetricsActor:courseProgressMetricsReport: assessmentBatchResult="
            + assessmentBatchResult,
        LoggerEnum.INFO.name());
    if (MapUtils.isNotEmpty(assessmentBatchResult)
        && CollectionUtils.isNotEmpty(
            (List<Map<String, Object>>) assessmentBatchResult.get(JsonKey.CONTENT))) {
      List<Map<String, Object>> content =
          (List<Map<String, Object>>) assessmentBatchResult.get(JsonKey.CONTENT);
      Map<String, Object> batchData = content.get(0);
      String reportLocation = (String) batchData.get(JsonKey.ASSESSMENT_REPORT_BLOB_URL);
      ProjectLogger.log(
          "CourseMetricsActor:courseProgressMetricsReport: reportLocation=" + reportLocation,
          LoggerEnum.INFO.name());
      if (isNotNull(reportLocation)) {
        String courseAssessmentsReportFolder =
            ProjectUtil.getConfigValue(JsonKey.SUNBIRD_ASSESSMENT_REPORT_FOLDER);
        String courseAssessmentsreportPath =
            courseAssessmentsReportFolder + File.separator + "report-" + batchId + ".csv";
        ProjectLogger.log(
            "CourseMetricsActor:courseProgressMetricsReport: courseMetricsContainer="
                + courseMetricsContainer
                + ", courseAssessmentsreportPath="
                + courseAssessmentsreportPath,
            LoggerEnum.INFO.name());
        assessmentReportSignedUrl =
            CloudStorageUtil.getSignedUrl(
                CloudStorageType.AZURE, courseMetricsContainer, courseAssessmentsreportPath);
      }
    }

    ProjectLogger.log(
        "CourseMetricsActor:courseProgressMetricsReport: courseMetricsContainer="
            + courseMetricsContainer
            + ", reportPath="
            + reportPath,
        LoggerEnum.INFO.name());
    String signedUrl =
        CloudStorageUtil.getSignedUrl(CloudStorageType.AZURE, courseMetricsContainer, reportPath);

    Response response = new Response();
    response.put(JsonKey.SIGNED_URL, signedUrl);
    Map<String, Object> reports = new HashMap<>();
    reports.put(JsonKey.PROGRESS_REPORT_SIGNED_URL, signedUrl);
    if (isNotNull(assessmentReportSignedUrl)) {
      reports.put(JsonKey.ASSESSMENT_REPORT_SIGNED_URL, assessmentReportSignedUrl);
    }
    response.put(JsonKey.REPORTS, reports);
    response.put(
        JsonKey.DURATION, ProjectUtil.getConfigValue(JsonKey.DOWNLOAD_LINK_EXPIRY_TIMEOUT));
    sender().tell(response, self());
  }

  @SuppressWarnings("unchecked")
  private void courseProgressMetrics(Request actorMessage) {
    ProjectLogger.log("CourseMetricsActor: courseProgressMetrics called.", LoggerEnum.INFO.name());
    Request request = new Request();
    String periodStr = (String) actorMessage.getRequest().get(JsonKey.PERIOD);
    String batchId = (String) actorMessage.getRequest().get(JsonKey.BATCH_ID);

    String requestedBy = (String) actorMessage.get(JsonKey.REQUESTED_BY);
    Map<String, Object> requestedByInfo = userOrgService.getUserById(requestedBy);

    if (isNull(requestedByInfo)
        || StringUtils.isBlank((String) requestedByInfo.get(JsonKey.FIRST_NAME))) {
      throw new ProjectCommonException(
          ResponseCode.invalidUserId.getErrorCode(),
          ResponseCode.invalidUserId.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    Map<String,Object> courseBatchResult = validateAndGetCourseBatch(batchId);
    String courseId = (String) courseBatchResult.get(JsonKey.COURSE_ID);
    Map<String, Object> course = getcontentForCourse(actorMessage, courseId);
    if (ProjectUtil.isNull(course)) {
      ProjectCommonException exception =
          new ProjectCommonException(
              ResponseCode.invalidCourseId.getErrorCode(),
              ResponseCode.invalidCourseId.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
      sender().tell(exception, self());
      return;
    }
    // get start and end time ---
    Map<String, String> dateRangeFilter = new HashMap<>();

    request.setId(actorMessage.getId());
    request.setContext(actorMessage.getContext());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.PERIOD, periodStr);
    Map<String, Object> filter = new HashMap<>();
    filter.put(JsonKey.BATCH_ID, batchId);
    filter.put(JsonKey.ACTIVE, true);
    if (!("fromBegining".equalsIgnoreCase(periodStr))) {
      Map<String, String> dateRange = getDateRange(periodStr);
      dateRangeFilter.put(GTE, (String) dateRange.get(STARTDATE));
      dateRangeFilter.put(
          LTE, (dateRange.get(ENDDATE)) + JsonKey.END_TIME_IN_HOUR_MINUTE_SECOND);
      ProjectLogger.log(
          "CourseMetricsActor:courseProgressMetrics Date range is : " + dateRangeFilter,
          LoggerEnum.INFO.name());
      filter.put(JsonKey.DATE_TIME, dateRangeFilter);
    }

    List<String> coursefields = new ArrayList<>();
    coursefields.add(JsonKey.USER_ID);
    coursefields.add(JsonKey.COURSE_ENROLL_DATE);
    coursefields.add(JsonKey.BATCH_ID);
    coursefields.add(JsonKey.DATE_TIME);
    coursefields.add(JsonKey.PROGRESS);
    coursefields.add(CourseJsonKey.CONTENT_STATUS);
    Future<Map<String, Object>> resultF =
        esService.search(
            createESRequest(filter, null, coursefields), EsType.usercourses.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    List<Map<String, Object>> esContent = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);

    if (CollectionUtils.isNotEmpty(esContent)) {
      List<String> userIds = new ArrayList<>();
      for (Map<String, Object> entry : esContent) {
        String userId = (String) entry.get(JsonKey.USER_ID);
        userIds.add(userId);
      }

      Set<String> uniqueUserIds = new HashSet<>(userIds);
      Map<String, Object> userfilter = new HashMap<>();
      userfilter.put(JsonKey.ID, uniqueUserIds.stream().collect(Collectors.toList()));
      List<String> userfields = new ArrayList<>();
      userfields.add(JsonKey.USER_ID);
      userfields.add(JsonKey.USERNAME);
      userfields.add(JsonKey.ROOT_ORG_ID);
      userfields.add(JsonKey.FIRST_NAME);
      userfields.add(JsonKey.LAST_NAME);
      Map<String, Object> userRequest = new HashMap<>();
      userRequest.put(JsonKey.FILTERS, userfilter);
      userRequest.put(JsonKey.FIELDS, userfields);
      List<Map<String, Object>> useresContent = userOrgService.getUsers(userRequest);
      Map<String, Map<String, Object>> userInfoCache = new HashMap<>();
      Set<String> orgSet = new HashSet<>();
      if (CollectionUtils.isNotEmpty(useresContent)) {
        for (Map<String, Object> map : useresContent) {
          String userId = (String) map.get(JsonKey.USER_ID);
          map.put("user", userId);
          map.put(JsonKey.USERNAME, (String) map.get(JsonKey.USERNAME));
          String registerdOrgId = (String) map.get(JsonKey.ROOT_ORG_ID);
          if (isNotNull(registerdOrgId)) {
            orgSet.add(registerdOrgId);
          }
          userInfoCache.put(userId, new HashMap<String, Object>(map));
          // remove the org info from user content bcoz it is not desired in the user info
          // result
          map.remove(JsonKey.ROOT_ORG_ID);
          map.remove(JsonKey.USER_ID);
        }
      }

      List<String> orgfields = orgSet.stream().collect(Collectors.toList());
      List<Map<String, Object>> orgContent = userOrgService.getOrganisationsByIds(orgfields);
      Map<String, String> orgInfoCache = new HashMap<>();

      if (CollectionUtils.isNotEmpty(orgContent)) {
        for (Map<String, Object> map : orgContent) {

          String regOrgId = (String) map.get(JsonKey.ID);
          String regOrgName = (String) map.get(JsonKey.ORGANISATION_NAME);
          orgInfoCache.put(regOrgId, regOrgName);
        }
      }

      Map<String, Object> batchFilter = new HashMap<>();
      batchFilter.put(JsonKey.ID, batchId);
      Future<Map<String, Object>> batchresultF =
          esService.search(
              createESRequest(batchFilter, null, null), EsType.courseBatch.getTypeName());
      Map<String, Object> batchresult =
          (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(batchresultF);
      List<Map<String, Object>> batchContent =
          (List<Map<String, Object>>) batchresult.get(JsonKey.CONTENT);

      Map<String, Map<String, Object>> batchInfoCache = new HashMap<>();
      for (Map<String, Object> map : batchContent) {
        String id = (String) map.get(JsonKey.ID);
        batchInfoCache.put(id, map);
      }
      for (Map<String, Object> map : esContent) {
        String userId = (String) map.get(JsonKey.USER_ID);
        populateProgress(course, map);
        map.put("user", userId);
        map.put("enrolledOn", map.get(JsonKey.COURSE_ENROLL_DATE));
        map.put("lastAccessTime", map.get(JsonKey.DATE_TIME));
        if (isNotNull(userInfoCache.get(userId))) {
          map.put(JsonKey.USERNAME, userInfoCache.get(userId).get(JsonKey.USERNAME));
          map.put(JsonKey.FIRST_NAME, userInfoCache.get(userId).get(JsonKey.FIRST_NAME));
          map.put(JsonKey.LAST_NAME, userInfoCache.get(userId).get(JsonKey.LAST_NAME));
          map.put("org", orgInfoCache.get(userInfoCache.get(userId).get(JsonKey.ROOT_ORG_ID)));
          if (isNotNull(batchInfoCache.get(map.get(JsonKey.BATCH_ID)))) {
            map.put(
                "batchEndsOn", batchInfoCache.get(map.get(JsonKey.BATCH_ID)).get(JsonKey.END_DATE));
          }
        } else {
          map.put(JsonKey.USERNAME, null);
          map.put("org", null);
          map.put("batchEndsOn", null);
        }
        map.remove(JsonKey.DATE_TIME);
        map.remove(JsonKey.COURSE_ENROLL_DATE);
        map.remove(JsonKey.USER_ID);
        map.remove(JsonKey.BATCH_ID);
      }
      Response response = createCourseProgressResponse(useresContent,esContent,periodStr);
      sender().tell(response, self());
    } else {

      ProjectLogger.log(
          "CourseMetricsActor:courseProgressMetrics: Course not found for given batchId.",
          LoggerEnum.INFO.name());
      Response response = createCourseProgressResponse(null,esContent,periodStr);
      sender().tell(response, self());
    }
  }

  private void courseConsumptionMetrics(Request actorMessage) {
    ProjectLogger.log(
        "CourseMetricsActor: courseConsumptionMetrics called.", LoggerEnum.INFO.name());
    try {
      String periodStr = (String) actorMessage.getRequest().get(JsonKey.PERIOD);
      String courseId = (String) actorMessage.getRequest().get(JsonKey.COURSE_ID);
      String requestedBy = (String) actorMessage.getRequest().get(JsonKey.REQUESTED_BY);

      Map<String, Object> requestObject = new HashMap<>();
      requestObject.put(JsonKey.PERIOD, getEkstepPeriod(periodStr));
      Map<String, Object> filterMap = new HashMap<>();
      filterMap.put(CONTENT_ID, courseId);
      requestObject.put(JsonKey.FILTER, filterMap);

      Map<String, Object> result = userOrgService.getUserById(requestedBy);
      if (null == result || result.isEmpty()) {
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.unAuthorized.getErrorCode(),
                ResponseCode.unAuthorized.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
        return;
      }

      String rootOrgId = (String) result.get(JsonKey.ROOT_ORG_ID);
      if (StringUtils.isBlank(rootOrgId)) {
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.noDataForConsumption.getErrorCode(),
                ResponseCode.noDataForConsumption.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
      }
      Map<String, Object> rootOrgData = userOrgService.getOrganisationById(rootOrgId);
      if (null == rootOrgData || rootOrgData.isEmpty()) {
           throw new ProjectCommonException(
                ResponseCode.invalidData.getErrorCode(),
                ResponseCode.invalidData.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
      }

        String channel = (String) rootOrgData.get(JsonKey.HASHTAGID);
      ProjectLogger.log(
          "CourseMetricsActor:courseConsumptionMetrics: Root organisation hashtag id = " + channel,
          LoggerEnum.INFO.name());
      String responseFormat = getCourseConsumptionData(periodStr, courseId, requestObject, channel);
      Response response =
          metricsResponseGenerator(responseFormat, periodStr, getViewData(courseId));
      sender().tell(response, self());
    } catch (ProjectCommonException e) {
      ProjectLogger.log(
          "CourseMetricsActor:courseConsumptionMetrics: Exception in getting course consumption data: "
              + e.getMessage(),
          e);
      sender().tell(e, self());
    } catch (Exception e) {
      ProjectLogger.log(
          "CourseMetricsActor:courseConsumptionMetrics: Generic exception in getting course consumption data: "
              + e.getMessage(),
          e);
      throw new ProjectCommonException(
          ResponseCode.internalError.getErrorCode(),
          ResponseCode.internalError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
  }

  private String getCourseConsumptionData(
      String periodStr, String courseId, Map<String, Object> requestObject, String channel) {
    Request request = new Request();
    requestObject.put(JsonKey.CHANNEL, channel);
    request.setRequest(requestObject);
    String responseFormat = "";
    try {
      String requestStr = mapper.writeValueAsString(request);
      String analyticsBaseUrl = ProjectUtil.getConfigValue(JsonKey.ANALYTICS_API_BASE_URL);
      String ekStepResponse =
          makePostRequest(analyticsBaseUrl, JsonKey.EKSTEP_METRICS_API_URL, requestStr);
      responseFormat =
          courseConsumptionResponseGenerator(periodStr, ekStepResponse, courseId, channel);
    } catch (Exception e) {
      ProjectLogger.log(CourseJsonKey.ERROR, e);
      throw new ProjectCommonException(
          ResponseCode.internalError.getErrorCode(),
          ResponseCode.internalError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    return responseFormat;
  }

  private Map<String, Object> getViewData(String courseId) {
    Map<String, Object> courseData = new HashMap<>();
    Map<String, Object> viewData = new HashMap<>();
    courseData.put(JsonKey.COURSE_ID, courseId);
    viewData.put(JsonKey.COURSE, courseData);
    return viewData;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getCourseCompletedData(
      String periodStr, String courseId, String channel) {
    Map<String, Object> resultMap = new HashMap<>();
    try {
      Map<String, Object> dateRange = getStartAndEndDate(periodStr);
      Map<String, Object> filter = new HashMap<>();
      filter.put(JsonKey.COURSE_ID, courseId);
      Map<String, String> dateRangeFilter = new HashMap<>();
      dateRangeFilter.put(GTE, (String) dateRange.get(STARTDATE));
      dateRangeFilter.put(LTE, (String) dateRange.get(ENDDATE));
      filter.put(JsonKey.DATE_TIME, dateRangeFilter);
      filter.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.COMPLETED.getValue());

      List<String> coursefields = new ArrayList<>();
      coursefields.add(JsonKey.USER_ID);
      coursefields.add(JsonKey.STATUS);

      Future<Map<String, Object>> resultF =
              esService.search(
                      createESRequest(filter, null, coursefields), EsType.usercourses.getTypeName());
      Map<String, Object> result =
              (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
      if (null == result || result.isEmpty()) {
        throw new ProjectCommonException(
                ResponseCode.noDataForConsumption.getErrorCode(),
                ResponseCode.noDataForConsumption.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      List<Map<String, Object>> esContent = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);

      List<String> userIds = new ArrayList<>();
      Double timeConsumed = 0D;
      for (Map<String, Object> entry : esContent) {
        String userId = (String) entry.get(JsonKey.USER_ID);
        timeConsumed = timeConsumed + getMetricsForUser(courseId, userId, periodStr, channel);
        userIds.add(userId);
      }
      Integer usersCount = userIds.size();
      resultMap.put(CourseJsonKey.USER_COUNT, usersCount);
      if (0 == usersCount) {
        resultMap.put(CourseJsonKey.AVG_TIME_COURSE_COMPLETED, 0);
      } else {
        resultMap.put(CourseJsonKey.AVG_TIME_COURSE_COMPLETED, timeConsumed / usersCount);
      }
    }
    catch (Exception e) {
      ProjectLogger.log(CourseJsonKey.ERROR, e);
    }
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private Double getMetricsForUser(
      String courseId, String userId, String periodStr, String channel) {
    Double userTimeConsumed = 0D;
    Map<String, Object> requestObject = new HashMap<>();
    Request request = new Request();
    requestObject.put(JsonKey.PERIOD, getEkstepPeriod(periodStr));
    Map<String, Object> filterMap = new HashMap<>();
    filterMap.put(CONTENT_ID, courseId);
    filterMap.put(USER_ID, userId);
    requestObject.put(JsonKey.FILTER, filterMap);
    ProjectLogger.log("Channel for Course" + channel);
    if (null == channel || channel.isEmpty()) {
      throw new ProjectCommonException(
          ResponseCode.noDataForConsumption.getErrorCode(),
          ResponseCode.noDataForConsumption.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    requestObject.put(JsonKey.CHANNEL, channel);
    request.setRequest(requestObject);
    try {
      String requestStr = mapper.writeValueAsString(request);
      String analyticsBaseUrl = ProjectUtil.getConfigValue(JsonKey.ANALYTICS_API_BASE_URL);
      String ekStepResponse =
          makePostRequest(analyticsBaseUrl, JsonKey.EKSTEP_METRICS_API_URL, requestStr);
      Map<String, Object> resultData = mapper.readValue(ekStepResponse, Map.class);
      resultData = (Map<String, Object>) resultData.get(JsonKey.RESULT);
      userTimeConsumed = (Double) resultData.get(CourseJsonKey.M_TOTAL_TS);
    } catch (Exception e) {
      ProjectLogger.log(CourseJsonKey.ERROR, e);
    }
    return userTimeConsumed;
  }

  @SuppressWarnings("unchecked")
  private String courseConsumptionResponseGenerator(
      String period, String ekstepResponse, String courseId, String channel) {
    String result = "";
    try {
      Map<String, Object> resultData = mapper.readValue(ekstepResponse, Map.class);
      resultData = (Map<String, Object>) resultData.get(JsonKey.RESULT);
      List<Map<String, Object>> resultList =
          (List<Map<String, Object>>) resultData.get(JsonKey.METRICS);
      List<Map<String, Object>> userBucket = createBucketStructure(period);
      List<Map<String, Object>> consumptionBucket = createBucketStructure(period);
      Map<String, Object> userData = null;
      int index = 0;
      Collections.reverse(resultList);
      Map<String, Object> resData = null;
      for (Map<String, Object> res : resultList) {
        resData = consumptionBucket.get(index);
        userData = userBucket.get(index);
        String bucketDate = "";
        String metricsDate = "";
        if ("5w".equalsIgnoreCase(period)) {
          bucketDate = (String) resData.get("key");
          bucketDate = bucketDate.substring(bucketDate.length() - 2, bucketDate.length());
          metricsDate = String.valueOf(res.get("d_period"));
          metricsDate = metricsDate.substring(metricsDate.length() - 2, metricsDate.length());
        } else {
          bucketDate = (String) resData.get("key_name");
          metricsDate = String.valueOf(res.get("d_period"));
          Date date = new SimpleDateFormat("yyyyMMdd").parse(metricsDate);
          metricsDate = new SimpleDateFormat("yyyy-MM-dd").format(date);
        }
        if (metricsDate.equalsIgnoreCase(bucketDate)) {
          Double totalTimeSpent = (Double) res.get(CourseJsonKey.M_TOTAL_TS);
          Integer totalUsers = (Integer) res.get("m_total_users_count");
          resData.put(VALUE, totalTimeSpent);
          userData.put(VALUE, totalUsers);
        }
        if (index < consumptionBucket.size() && index < userBucket.size()) {
          index++;
        }
      }

      Map<String, Object> series = new HashMap<>();

      Map<String, Object> seriesData = new LinkedHashMap<>();
      seriesData.put(JsonKey.NAME, "Timespent for content consumption");
      seriesData.put(JsonKey.SPLIT, "content.sum(time_spent)");
      seriesData.put(JsonKey.TIME_UNIT, CourseJsonKey.SECONDS);
      seriesData.put(GROUP_ID, "course.timespent.sum");
      seriesData.put(CourseJsonKey.BUCKETS, consumptionBucket);
      series.put("course.consumption.time_spent", seriesData);
      seriesData = new LinkedHashMap<>();
      if ("5w".equalsIgnoreCase(period)) {
        seriesData.put(JsonKey.NAME, "Number of users by week");
      } else {
        seriesData.put(JsonKey.NAME, "Number of users by day");
      }
      seriesData.put(JsonKey.SPLIT, "content.users.count");
      seriesData.put(GROUP_ID, "course.users.count");
      seriesData.put(CourseJsonKey.BUCKETS, userBucket);
      series.put("course.consumption.content.users.count", seriesData);
      Map<String, Object> courseCompletedData = getCourseCompletedData(period, courseId, channel);
      ProjectLogger.log("Course completed Data" + courseCompletedData);
      resultData = (Map<String, Object>) resultData.get(JsonKey.SUMMARY);
      Map<String, Object> snapshot = new LinkedHashMap<>();
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(JsonKey.NAME, "Total time of Content consumption");
      dataMap.put(VALUE, resultData.get(CourseJsonKey.M_TOTAL_TS));
      dataMap.put(JsonKey.TIME_UNIT, CourseJsonKey.SECONDS);
      snapshot.put("course.consumption.time_spent.count", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "User access course over time");
      dataMap.put(VALUE, resultData.get("m_total_users_count"));
      snapshot.put("course.consumption.time_per_user", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "Total users completed the course");
      int userCount =
          courseCompletedData.get(CourseJsonKey.USER_COUNT) == null
              ? 0
              : (Integer) courseCompletedData.get(CourseJsonKey.USER_COUNT);
      dataMap.put(VALUE, userCount);
      snapshot.put("course.consumption.users_completed", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "Average time per user for course completion");
      int avgTime = getAverageCourseCompletionTime(courseCompletedData.get(CourseJsonKey.AVG_TIME_COURSE_COMPLETED));
      dataMap.put(VALUE, avgTime);
      dataMap.put(JsonKey.TIME_UNIT, CourseJsonKey.SECONDS);
      snapshot.put("course.consumption.time_spent_completion_count", dataMap);
      Map<String, Object> responseMap = new HashMap<>();
      responseMap.put(JsonKey.SNAPSHOT, snapshot);
      responseMap.put(JsonKey.SERIES, series);
      result = mapper.writeValueAsString(responseMap);
    } catch (Exception e) {
      ProjectLogger.log(CourseJsonKey.ERROR, e);
    }
    return result;
  }

  private int getAverageCourseCompletionTime(Object averageTime ){
    if(averageTime == null)
      return 0;
    else
    {
      return averageTime instanceof Double?((Double) averageTime).intValue():(Integer)averageTime;
    }
  }

  private String getSortyBy(String sortBy) {
    if (JsonKey.USERNAME.equalsIgnoreCase(sortBy)) {
      return JsonKey.NAME;
    } else if (JsonKey.PROGRESS.equalsIgnoreCase(sortBy)) {
      return JsonKey.COMPLETED_PERCENT;
    } else if (JsonKey.ENROLLED_ON.equalsIgnoreCase(sortBy)) {
      return JsonKey.ENROLLED_ON;
    } else if (JsonKey.ORG_NAME.equalsIgnoreCase(sortBy)) {
      return JsonKey.ROOT_ORG_NAME;
    } else {
      throw new ProjectCommonException(
          ResponseCode.invalidParameterValue.getErrorCode(),
          MessageFormat.format(
              ResponseCode.invalidParameterValue.getErrorMessage(), sortBy, JsonKey.SORT_BY),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }

  private Map<String, Object> getcontentForCourse(Request request, String courseId) {
    List<String> fields = new ArrayList<>();
    fields.add(JsonKey.IDENTIFIER);
    fields.add(JsonKey.DESCRIPTION);
    fields.add(JsonKey.NAME);
    fields.add(JsonKey.LEAF_NODE_COUNT);
    fields.add(JsonKey.APP_ICON);
    fields.add("leafNodes");
    String requestBody = prepareCourseSearchRequest(courseId, fields);
    ProjectLogger.log(
        "LearnerStateActor:getcontentsForCourses: Request Body = " + requestBody,
        LoggerEnum.INFO.name());
    Map<String, Object> contentsList =
        ContentSearchUtil.searchContentSync(
            null, requestBody, (Map<String, String>) request.getRequest().get(JsonKey.HEADER));
    if (contentsList == null) {
     throw new ProjectCommonException(
          ResponseCode.internalError.getErrorCode(),
          ResponseCode.internalError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
      List<Map<String, Object>> contents =
              ((List<Map<String, Object>>) (contentsList.get(JsonKey.CONTENTS)));
    return contents.get(0);
  }

  private String prepareCourseSearchRequest(String courseId, List<String> fields) {

    Map<String, Object> filters = new HashMap<String, Object>();
    filters.put(JsonKey.CONTENT_TYPE, new String[] {JsonKey.COURSE});
    filters.put(JsonKey.IDENTIFIER, courseId);
    ProjectLogger.log(
        "CourseMetricsActor:prepareCourseSearchRequest: courseIds = " + courseId,
        LoggerEnum.INFO.name());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.FILTERS, filters);
    if (fields != null) requestMap.put(JsonKey.FIELDS, fields);
    Map<String, Map<String, Object>> request = new HashMap<>();
    request.put(JsonKey.REQUEST, requestMap);

    String requestJson = null;
    try {
      requestJson = new ObjectMapper().writeValueAsString(request);
    } catch (JsonProcessingException e) {
      ProjectLogger.log(
          "CourseMetricsActor:prepareCourseSearchRequest: Exception occurred with error message = "
              + e.getMessage(),
          e);
    }

    return requestJson;
  }

  public void populateProgress(
      Map<String, Object> courseContent, Map<String, Object> userCoursesEsContent) {
    List<String> leafNodes = (List<String>) courseContent.get("leafNodes");
    userCoursesEsContent.put(COMPLETE_PERCENT, 0);
    userCoursesEsContent.put(JsonKey.LEAF_NODE_COUNT, courseContent.get(JsonKey.LEAF_NODE_COUNT));
    if (userCoursesEsContent.get(CourseJsonKey.CONTENT_STATUS) != null
        && CollectionUtils.isNotEmpty(leafNodes)) {
      Map<String, Object> contentStatus =
          new ObjectMapper().convertValue(userCoursesEsContent.get(CourseJsonKey.CONTENT_STATUS), Map.class);
      int contentIdscompleted =
          (int)
              contentStatus
                  .entrySet()
                  .stream()
                  .filter(
                      content ->
                          ProjectUtil.ProgressStatus.COMPLETED.getValue()
                              == (Integer) content.getValue())
                  .filter(content -> (leafNodes).contains((String) content.getKey()))
                  .count();

      Integer completionPercentage =
          (int) Math.round((contentIdscompleted * 100.0) / (leafNodes).size());
      userCoursesEsContent.put(JsonKey.PROGRESS, contentIdscompleted);
      userCoursesEsContent.put(COMPLETE_PERCENT, completionPercentage);
    }
  }

  private Response createCourseProgressResponse(List<Map<String, Object>> useresContent,List<Map<String, Object>> esContent,String periodStr){
    Map<String, Object> responseMap = new LinkedHashMap<>();
    Map<String, Object> userdataMap = new LinkedHashMap<>();
    Map<String, Object> courseprogressdataMap = new LinkedHashMap<>();
    Map<String, Object> valueMap = new LinkedHashMap<>();
    userdataMap.put(JsonKey.NAME, "List of users enrolled for the course");
    userdataMap.put("split", "content.sum(time_spent)");

    if(CollectionUtils.isNotEmpty(useresContent)) {
      userdataMap.put("buckets", useresContent);
    }
    else{
      userdataMap.put("buckets", new ArrayList<>());
    }
    courseprogressdataMap.put(JsonKey.NAME, "List of users enrolled for the course");
    courseprogressdataMap.put("split", "content.sum(time_spent)");
    courseprogressdataMap.put("buckets", esContent);
    valueMap.put("course.progress.users_enrolled.count", userdataMap);
    valueMap.put("course.progress.course_progress_per_user.count", courseprogressdataMap);
    responseMap.put("period", periodStr);
    responseMap.put("series", valueMap);
    Response response = new Response();
    response.putAll(responseMap);
    return response;
  }
}
