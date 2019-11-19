package org.sunbird.learner.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.ElasticSearchTcpImpl;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.CourseEnrollmentActor;
import scala.concurrent.Future;

/**
 * This class will update course batch count to EKStep. First it will get batch details from ES ,
 * then collect old open/private batch count value form EKStep then update cassandra db and EKStep
 * course instance count under EKStep.
 *
 * @author Manzarul
 */
public final class CourseBatchSchedulerUtil {
  public static Map<String, String> headerMap = new HashMap<>();
  private static ElasticSearchService esService = new ElasticSearchTcpImpl();

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
   * @param increment
   * @param map
   */
  public static void updateCourseBatchDbStatus(Map<String, Object> map, Boolean increment) {
    ProjectLogger.log(
        "CourseBatchSchedulerUtil:updateCourseBatchDbStatus: updating course batch details start",
        LoggerEnum.INFO.name());
    try {
      boolean response =
          doOperationInEkStepCourse(
              (String) map.get(JsonKey.COURSE_ID),
              increment,
              (String) map.get(JsonKey.ENROLLMENT_TYPE));
      ProjectLogger.log("Geeting response code back for update content == " + response);
      if (response) {
        boolean flag = updateDataIntoES(map);
        if (flag) {
          updateDataIntoCassandra(map);
        }
      } else {
        ProjectLogger.log(
            "CourseBatchSchedulerUtil:updateCourseBatchDbStatus: Ekstep content update failed for courseId "
                + (String) map.get(JsonKey.COURSE_ID),
            LoggerEnum.INFO.name());
      }
    } catch (Exception e) {
      ProjectLogger.log(
          "CourseBatchSchedulerUtil:updateCourseBatchDbStatus: Exception occurred while savin data to course batch db "
              + e.getMessage(),
          LoggerEnum.INFO.name());
    }
  }

  /** @param map */
  public static boolean updateDataIntoES(Map<String, Object> map) {
    boolean flag = true;
    try {
      Future<Boolean> flagF =
          esService.update(
              ProjectUtil.EsType.course.getTypeName(), (String) map.get(JsonKey.ID), map);
      flag = (boolean) ElasticSearchHelper.getResponseFromFuture(flagF);
    } catch (Exception e) {
      ProjectLogger.log(
          "CourseBatchSchedulerUtil:updateDataIntoES: Exception occurred while saving course batch data to ES",
          e);
      flag = false;
    }
    return flag;
  }

  /** @param map */
  public static void updateDataIntoCassandra(Map<String, Object> map) {
    CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    Util.DbInfo courseBatchDBInfo = Util.dbInfoMap.get(JsonKey.COURSE_BATCH_DB);
    cassandraOperation.updateRecord(
        courseBatchDBInfo.getKeySpace(), courseBatchDBInfo.getTableName(), map);
    ProjectLogger.log(
        "CourseBatchSchedulerUtil:updateDataIntoCassandra: Update Successful for batchId "
            + map.get(JsonKey.ID),
        LoggerEnum.INFO);
  }

  private static void addHeaderProps(Map<String, String> header, String key, String value) {
    header.put(key, value);
  }
  /**
   * Method to update the content state at ekstep : batch count
   *
   * @param courseId
   * @param increment
   * @param enrollmentType
   * @return
   */
  public static boolean doOperationInEkStepCourse(
      String courseId, boolean increment, String enrollmentType) {
    String contentName = getCountName(enrollmentType);
    boolean response = false;
    Map<String, Object> ekStepContent =
        CourseEnrollmentActor.getCourseObjectFromEkStep(courseId, getBasicHeader());
    if (MapUtils.isNotEmpty(ekStepContent)) {
      int val = getUpdatedBatchCount(ekStepContent, contentName, increment);
      if (ekStepContent.get(JsonKey.CHANNEL) != null) {
        ProjectLogger.log(
            "Channel value coming from content is "
                + (String) ekStepContent.get(JsonKey.CHANNEL)
                + " Id "
                + courseId,
            LoggerEnum.INFO.name());
        addHeaderProps(
            getBasicHeader(),
            HeaderParam.CHANNEL_ID.getName(),
            (String) ekStepContent.get(JsonKey.CHANNEL));
      } else {
        ProjectLogger.log(
            "No channel value available in content with Id " + courseId, LoggerEnum.INFO.name());
      }
      response = updateEkstepContent(courseId, contentName, val);
    } else {
      ProjectLogger.log(
          "EKstep content not found for course id==" + courseId, LoggerEnum.INFO.name());
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

  public static boolean updateEkstepContent(String courseId, String contentName, int val) {
    String response = "";
    try {
      ProjectLogger.log("updating content details to Ekstep start", LoggerEnum.INFO.name());
      String contentUpdateBaseUrl = ProjectUtil.getConfigValue(JsonKey.EKSTEP_BASE_URL);
      response =
          HttpUtil.sendPatchRequest(
              contentUpdateBaseUrl
                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_UPDATE_URL)
                  + courseId,
              "{\"request\": {\"content\": {\"" + contentName + "\": " + val + "}}}",
              getBasicHeader());
      ProjectLogger.log(
          "batch count update response==" + response + " " + courseId, LoggerEnum.INFO.name());
    } catch (IOException e) {
      ProjectLogger.log("Error while updating content value " + e.getMessage(), e);
    }
    return JsonKey.SUCCESS.equalsIgnoreCase(response);
  }
}
