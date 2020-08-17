package org.sunbird.learner.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;
import org.sunbird.learner.util.CourseBatchSchedulerUtil;
import org.sunbird.learner.util.Util;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class will handle all the background job. Example when ever course is published then this
 * job will collect course related data from EKStep and update with Sunbird.
 *
 * @author Manzarul
 * @author Amit Kumar
 */
public class BackgroundJobManager extends BaseActor {

  private static Map<String, String> headerMap = new HashMap<>();
  private static Util.DbInfo dbInfo = null;
  private ObjectMapper mapper = new ObjectMapper();
  private LoggerUtil logger = new LoggerUtil(BackgroundJobManager.class);
  static {
    headerMap.put("content-type", "application/json");
    headerMap.put("accept", "application/json");
  }

  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);

  @Override
  public void onReceive(Request request) throws Throwable {
    if (dbInfo == null) {
      dbInfo = Util.dbInfoMap.get(JsonKey.COURSE_MANAGEMENT_DB);
    }
    String operation = request.getOperation();
    if (operation.equalsIgnoreCase(ActorOperations.INSERT_USR_COURSES_INFO_ELASTIC.getValue())) {
      insertUserCourseInfoToEs(request);
    } else if (operation.equalsIgnoreCase(ActorOperations.INSERT_COURSE_BATCH_ES.getValue())) {
      insertCourseBatchInfoToEs(request);
    } else if (operation.equalsIgnoreCase(ActorOperations.UPDATE_COURSE_BATCH_ES.getValue())) {
      updateCourseBatchInfoToEs(request);
    } else if (operation.equalsIgnoreCase(
        ActorOperations.UPDATE_USR_COURSES_INFO_ELASTIC.getValue())) {
      updateUserCourseInfoToEs(request);
    } else {
      ProjectCommonException exception =
          new ProjectCommonException(
              ResponseCode.invalidOperationName.getErrorCode(),
              ResponseCode.invalidOperationName.getErrorMessage(),
              ResponseCode.CLIENT_ERROR.getResponseCode());
      logger.error(request.getRequestContext(), "UnSupported operation in Background Job Manager", exception);
    }
  }

  public static List<Map<String, Object>> removeDataFromMap(List<Map<String, Object>> listOfMap) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, Object> map : listOfMap) {
      Map<String, Object> innermap = new HashMap<>();
      innermap.put(JsonKey.ID, map.get(JsonKey.ID));
      innermap.put(JsonKey.BADGE_TYPE_ID, map.get(JsonKey.BADGE_TYPE_ID));
      innermap.put(JsonKey.RECEIVER_ID, map.get(JsonKey.RECEIVER_ID));
      innermap.put(JsonKey.CREATED_DATE, map.get(JsonKey.CREATED_DATE));
      innermap.put(JsonKey.CREATED_BY, map.get(JsonKey.CREATED_BY));
      list.add(innermap);
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  private void updateUserCourseInfoToEs(Request actorMessage) {

    Map<String, Object> batch =
        (Map<String, Object>) actorMessage.getRequest().get(JsonKey.USER_COURSES);
    updateDataToElastic(actorMessage.getRequestContext(), 
        ProjectUtil.EsIndex.sunbird.getIndexName(),
        ProjectUtil.EsType.usercourses.getTypeName(),
        (String) batch.get(JsonKey.ID),
        batch);
  }

  @SuppressWarnings("unchecked")
  private void insertUserCourseInfoToEs(Request actorMessage) {

    Map<String, Object> batch =
        (Map<String, Object>) actorMessage.getRequest().get(JsonKey.USER_COURSES);
    String userId = (String) batch.get(JsonKey.USER_ID);
    String batchId = (String) batch.get(JsonKey.BATCH_ID);
    String identifier = UserCoursesService.generateUserCourseESId(batchId, userId);
    insertDataToElastic(actorMessage.getRequestContext(), 
        ProjectUtil.EsIndex.sunbird.getIndexName(),
        ProjectUtil.EsType.usercourses.getTypeName(),
        identifier,
        batch);
  }

  @SuppressWarnings("unchecked")
  private void updateCourseBatchInfoToEs(Request actorMessage) {
    Map<String, Object> batch = (Map<String, Object>) actorMessage.getRequest().get(JsonKey.BATCH);
    updateDataToElastic(
            actorMessage.getRequestContext(), ProjectUtil.EsIndex.sunbird.getIndexName(),
        ProjectUtil.EsType.courseBatch.getTypeName(),
        (String) batch.get(JsonKey.ID),
        batch);
  }

  @SuppressWarnings("unchecked")
  private void insertCourseBatchInfoToEs(Request actorMessage) {
    Map<String, Object> batch = (Map<String, Object>) actorMessage.getRequest().get(JsonKey.BATCH);
    // making call to register tag
    registertag(actorMessage.getRequestContext(), 
        (String) batch.getOrDefault(JsonKey.HASH_TAG_ID, batch.get(JsonKey.ID)),
        "{}",
        CourseBatchSchedulerUtil.headerMap);
    // register tag for course
    registertag(
            actorMessage.getRequestContext(), (String) batch.getOrDefault(JsonKey.COURSE_ID, batch.get(JsonKey.COURSE_ID)),
        "{}",
        CourseBatchSchedulerUtil.headerMap);
  }

  private boolean updateDataToElastic(
          RequestContext requestContext, String indexName, String typeName, String identifier, Map<String, Object> data) {
    Future<Boolean> responseF = esService.update(requestContext, typeName, identifier, data);
    boolean response = (boolean) ElasticSearchHelper.getResponseFromFuture(responseF);
    if (response) {
      return true;
    }
    logger.info(requestContext, "unbale to save the data inside ES with identifier " + identifier);
    return false;
  }

  /**
   * Method to cache the course data .
   *
   *
   * @param requestContext
   * @param index String
   * @param type String
   * @param identifier String
   * @param data Map<String,Object>
   * @return boolean
   */
  private boolean insertDataToElastic(
          RequestContext requestContext, String index, String type, String identifier, Map<String, Object> data) {
    logger.info(requestContext, "BackgroundJobManager:insertDataToElastic: type = " + type + " identifier = " + identifier);
    Future<String> responseF = esService.save(requestContext, type, identifier, data);
    String response = (String) ElasticSearchHelper.getResponseFromFuture(responseF);
    logger.debug(requestContext, "ES save response for identifier :" + identifier + " : ", response);
    if (!StringUtils.isBlank(response)) {
      return true;
    }
    logger.info(requestContext,"unbale to save the data inside ES with identifier " + identifier);
    return false;
  }

  /**
   * This method will make EkStep api call register the tag.
   *
   *
   * @param requestContext
   * @param tagId String unique tag id.
   * @param body String requested body
   * @param header Map<String,String>
   * @return String
   */
  private String registertag(RequestContext requestContext, String tagId, String body, Map<String, String> header) {
    String tagStatus = "";
    try {
      tagStatus = ProjectUtil.registertag(tagId, body, header);
    } catch (Exception e) {
      logger.error(requestContext, "BackgroundJobManager:registertag register tag call failure with error message = "
                      + e.getMessage(), e);
    }
    return tagStatus;
  }
}
