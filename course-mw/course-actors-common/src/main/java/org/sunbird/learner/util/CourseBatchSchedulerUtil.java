package org.sunbird.learner.util;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import scala.concurrent.Future;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class will update course batch count to EKStep. First it will get batch details from ES ,
 * then collect old open/private batch count value form EKStep then update cassandra db and EKStep
 * course instance count under EKStep.
 *
 * @author Manzarul
 */
public final class CourseBatchSchedulerUtil {
  public static Map<String, String> headerMap = new HashMap<>();
  private static ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private static LoggerUtil logger = new LoggerUtil(CourseBatchSchedulerUtil.class);
  private static String EKSTEP_COURSE_SEARCH_QUERY =
      "{\"request\": {\"filters\":{\"identifier\": \"COURSE_ID_PLACEHOLDER\", \"status\": \"Live\", \"mimeType\": \"application/vnd.ekstep.content-collection\", \"trackable.enabled\": \"Yes\"},\"limit\": 1}}";

  static {
    String header = ProjectUtil.getConfigValue(JsonKey.EKSTEP_AUTHORIZATION);
    header = JsonKey.BEARER + header;
    headerMap.put(JsonKey.AUTHORIZATION, header);
    headerMap.put("Content-Type", "application/json");
  }

  private CourseBatchSchedulerUtil() {}

  /**
   * Method to update course batch status to db as well as EkStep .
   *
   * @param map
   * @param increment
   * @param requestContext
   */
  public static void updateCourseBatchDbStatus(Map<String, Object> map, Boolean increment, RequestContext requestContext) {
    logger.info(requestContext, "updateCourseBatchDbStatus: updating course batch details start");
    try {
      boolean response =
          doOperationInContentCourse(requestContext,
              (String) map.get(JsonKey.COURSE_ID),
              increment,
              (String) map.get(JsonKey.ENROLLMENT_TYPE));
      logger.debug(requestContext, "Response for update content == " + response);
      if (response) {
        boolean flag = updateDataIntoES(requestContext, map);
        if (flag) {
          updateDataIntoCassandra(requestContext, map);
        }
      } else {
        logger.info(requestContext, "CourseBatchSchedulerUtil:updateCourseBatchDbStatus: Ekstep content update failed for courseId "
                + (String) map.get(JsonKey.COURSE_ID));
      }
    } catch (Exception e) {
      logger.error(requestContext, "CourseBatchSchedulerUtil:updateCourseBatchDbStatus: Exception occurred while savin data to course batch db "
              + e.getMessage(), e);
    }
  }

  /**
   * @param requestContext
   * @param map */
  public static boolean updateDataIntoES(RequestContext requestContext, Map<String, Object> map) {
    boolean flag = true;
    try {
      Future<Boolean> flagF =
          esService.update(
                  requestContext, ProjectUtil.EsType.course.getTypeName(), (String) map.get(JsonKey.ID), map);
      flag = (boolean) ElasticSearchHelper.getResponseFromFuture(flagF);
    } catch (Exception e) {
      logger.error(requestContext, "CourseBatchSchedulerUtil:updateDataIntoES: Exception occurred while saving course batch data to ES", e);
      flag = false;
    }
    return flag;
  }

  /**
   * @param map
   * @param requestContext */
  public static void updateDataIntoCassandra(RequestContext requestContext, Map<String, Object> map) {
    CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    Util.DbInfo courseBatchDBInfo = Util.dbInfoMap.get(JsonKey.COURSE_BATCH_DB);
    cassandraOperation.updateRecord(
            requestContext, courseBatchDBInfo.getKeySpace(), courseBatchDBInfo.getTableName(), map);
    logger.info(requestContext, "CourseBatchSchedulerUtil:updateDataIntoCassandra: Update Successful for batchId "
            + map.get(JsonKey.ID));
  }

  private static void addHeaderProps(Map<String, String> header, String key, String value) {
    header.put(key, value);
  }
  /**
   * Method to update the content state at ekstep : batch count
   *
   *
   * @param requestContext
   * @param courseId
   * @param increment
   * @param enrollmentType
   * @return
   */
  public static boolean doOperationInContentCourse(
          RequestContext requestContext, String courseId, boolean increment, String enrollmentType) {
    String contentName = getCountName(enrollmentType);
    boolean response = false;
    Map<String, Object> ekStepContent = getCourseObject(requestContext, courseId, getBasicHeader());
    if (MapUtils.isNotEmpty(ekStepContent)) {
      int val = getUpdatedBatchCount(ekStepContent, contentName, increment);
      if (ekStepContent.get(JsonKey.CHANNEL) != null) {
        logger.info(requestContext, "Channel value coming from content is " + (String) ekStepContent.get(JsonKey.CHANNEL)
                + " Id " + courseId);
        addHeaderProps(
            getBasicHeader(),
            HeaderParam.CHANNEL_ID.getName(),
            (String) ekStepContent.get(JsonKey.CHANNEL));
      } else {
        logger.info(requestContext, "No channel value available in content with Id " + courseId);
      }
      response = true;
    } else {
      logger.info(requestContext, "EKstep content not found for course id==" + courseId);
    }
    return response;
  }

  private static Map<String, String> getBasicHeader() {
    return headerMap;
  }

  public static String getCountName(String enrollmentType) {
    String name = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_INSTALLATION);
    String contentName = "";
    if (ProjectUtil.EnrolmentType.open.getVal().equals(enrollmentType)) {
      contentName = "c_" + name + "_open_batch_count";
    } else {
      contentName = "c_" + name + "_private_batch_count";
    }
    return contentName.toLowerCase();
  }

  public static int getUpdatedBatchCount(
      Map<String, Object> ekStepContent, String contentName, boolean increment) {
    int val = (int) ekStepContent.getOrDefault(contentName, 0);
    val = increment ? val + 1 : (val > 0) ? val - 1 : 0;
    return val;
  }

//  public static boolean updateCourseContent(RequestContext requestContext, String courseId, String contentName, int val) {
//    String response = "";
//    try {
//      String contentUpdateBaseUrl = ProjectUtil.getConfigValue(JsonKey.LEARNING_SERVICE_BASE_URL);
//      response =
//          HttpUtil.sendPatchRequest(
//              contentUpdateBaseUrl
//                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_UPDATE_URL)
//                  + courseId,
//              "{\"request\": {\"content\": {\"" + contentName + "\": " + val + "}}}",
//              getBasicHeader());
//    } catch (Exception e) {
//      logger.error(requestContext, "Error while updating content value " + e.getMessage(), e);
//    }
//    return JsonKey.SUCCESS.equalsIgnoreCase(response);
//  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getCourseObject(RequestContext requestContext, String courseId, Map<String, String> headers) {
    logger.debug(requestContext, "getCourseObject: Requested course id is ==" + courseId);
    if (!StringUtils.isBlank(courseId)) {
      try {
        String query = EKSTEP_COURSE_SEARCH_QUERY.replaceAll("COURSE_ID_PLACEHOLDER", courseId);
        Map<String, Object> result = ContentUtil.searchContent(query, headers);
        if (null != result && !result.isEmpty() && result.get(JsonKey.CONTENTS) != null) {
          return ((List<Map<String, Object>>) result.get(JsonKey.CONTENTS)).get(0);
          // return (Map<String, Object>) contentObject;
        } else {
          logger.info(requestContext, "CourseEnrollmentActor:getCourseObjectFromEkStep: Content not found for requested courseId "
                  + courseId);
        }
      } catch (Exception e) {
        logger.error(requestContext, e.getMessage(), e);
      }
    }
    return null;
  }
}
