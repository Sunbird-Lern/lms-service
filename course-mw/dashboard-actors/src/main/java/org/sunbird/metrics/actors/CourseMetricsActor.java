package org.sunbird.metrics.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;
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
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import scala.concurrent.Future;

public class CourseMetricsActor extends BaseMetricsActor {
  protected static final String CONTENT_ID = "content_id";
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
  private static ObjectMapper mapper = new ObjectMapper();

  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);

  @Override
  public void onReceive(Request request) throws Throwable {
    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "courseConsumptionMetrics":
        courseConsumptionMetrics(request);
        break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
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
        ProjectCommonException exception =
            new ProjectCommonException(
                ResponseCode.invalidData.getErrorCode(),
                ResponseCode.invalidData.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
        sender().tell(exception, self());
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
      return;
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
      ProjectLogger.log("Error occurred", e);
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
    Map<String, Object> dateRange = getStartAndEndDate(periodStr);
    Map<String, Object> filter = new HashMap<>();
    Map<String, Object> resultMap = new HashMap<>();
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
    Integer users_count = userIds.size();
    resultMap.put("user_count", users_count);
    if (0 == users_count) {
      resultMap.put("avg_time_course_completed", 0);
    } else {
      resultMap.put("avg_time_course_completed", timeConsumed / users_count);
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
      userTimeConsumed = (Double) resultData.get("m_total_ts");
    } catch (Exception e) {
      ProjectLogger.log("Error occurred", e);
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
          Double totalTimeSpent = (Double) res.get("m_total_ts");
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
      seriesData.put(JsonKey.TIME_UNIT, "seconds");
      seriesData.put(GROUP_ID, "course.timespent.sum");
      seriesData.put("buckets", consumptionBucket);
      series.put("course.consumption.time_spent", seriesData);
      seriesData = new LinkedHashMap<>();
      if ("5w".equalsIgnoreCase(period)) {
        seriesData.put(JsonKey.NAME, "Number of users by week");
      } else {
        seriesData.put(JsonKey.NAME, "Number of users by day");
      }
      seriesData.put(JsonKey.SPLIT, "content.users.count");
      seriesData.put(GROUP_ID, "course.users.count");
      seriesData.put("buckets", userBucket);
      series.put("course.consumption.content.users.count", seriesData);
      Map<String, Object> courseCompletedData = new HashMap<>();
      try {
        courseCompletedData = getCourseCompletedData(period, courseId, channel);
      } catch (Exception e) {
        ProjectLogger.log("Error occurred", e);
      }
      ProjectLogger.log("Course completed Data" + courseCompletedData);
      resultData = (Map<String, Object>) resultData.get(JsonKey.SUMMARY);
      Map<String, Object> snapshot = new LinkedHashMap<>();
      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(JsonKey.NAME, "Total time of Content consumption");
      dataMap.put(VALUE, resultData.get("m_total_ts"));
      dataMap.put(JsonKey.TIME_UNIT, "seconds");
      snapshot.put("course.consumption.time_spent.count", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "User access course over time");
      dataMap.put(VALUE, resultData.get("m_total_users_count"));
      snapshot.put("course.consumption.time_per_user", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "Total users completed the course");
      int userCount =
          courseCompletedData.get("user_count") == null
              ? 0
              : (Integer) courseCompletedData.get("user_count");
      dataMap.put(VALUE, userCount);
      snapshot.put("course.consumption.users_completed", dataMap);
      dataMap = new LinkedHashMap<>();
      dataMap.put(JsonKey.NAME, "Average time per user for course completion");
      int avgTime =
          courseCompletedData.get("avg_time_course_completed") == null
              ? 0
              : (courseCompletedData.get("avg_time_course_completed") instanceof Double)
                  ? ((Double) courseCompletedData.get("avg_time_course_completed")).intValue()
                  : (Integer) courseCompletedData.get("avg_time_course_completed");
      dataMap.put(VALUE, avgTime);
      dataMap.put(JsonKey.TIME_UNIT, "seconds");
      snapshot.put("course.consumption.time_spent_completion_count", dataMap);

      Map<String, Object> responseMap = new HashMap<>();
      responseMap.put(JsonKey.SNAPSHOT, snapshot);
      responseMap.put(JsonKey.SERIES, series);
      result = mapper.writeValueAsString(responseMap);
    } catch (Exception e) {
      ProjectLogger.log("Error occurred", e);
    }
    return result;
  }

}
