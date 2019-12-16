package org.sunbird.learner.actors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.datasecurity.OneWayHashing;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.kafka.client.KafkaClient;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.constants.InstructionEvent;
import org.sunbird.learner.util.Util;
import scala.concurrent.Future;

/**
 * This actor to handle learner's state update operation .
 *
 * @author Manzarul
 * @author Arvind
 */
public class LearnerStateUpdateActor extends BaseActor {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private Util.DbInfo consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB);
  private Util.DbInfo userCourseDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB);
  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private SimpleDateFormat simpleDateFormat = ProjectUtil.getDateFormatter();
  private ObjectMapper mapper = new ObjectMapper();

  private enum ContentUpdateResponseKeys {
    SUCCESS_CONTENTS,
    NOT_A_ON_GOING_BATCH,
    BATCH_NOT_EXISTS
  }

  /**
   * Receives the actor message and perform the add content operation.
   *
   * @param request Request
   */
  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    ExecutionContext.setRequestId(request.getRequestId());

    if (request.getOperation().equalsIgnoreCase(ActorOperations.ADD_CONTENT.getValue())) {
      ProjectLogger.log(
              "LearnerStateUpdateActor: onReceive called for operation: "
                      + request.getOperation(),
              LoggerEnum.INFO.name());
      String userId = (String) request.getRequest().get(JsonKey.USER_ID);
      List<Map<String, Object>> assessments =
          (List<Map<String, Object>>) request.getRequest().get(JsonKey.ASSESSMENT_EVENTS);
      if (CollectionUtils.isNotEmpty(assessments)) {
        Map<String, List<Map<String, Object>>> batchAssessmentList =
            assessments
                .stream()
                .filter(x -> StringUtils.isNotBlank((String) x.get("batchId")))
                .collect(
                    Collectors.groupingBy(
                        x -> {
                          return (String) x.get("batchId");
                        }));
        List<String> batchIds = batchAssessmentList.keySet().stream().collect(Collectors.toList());
        Map<String, List<Map<String, Object>>> batches =
            getBatches(batchIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        x -> {
                          return (String) x.get("batchId");
                        }));
        Map<String, Object> respMessages = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> input : batchAssessmentList.entrySet()) {
          String batchId = input.getKey();
          if (batches.containsKey(batchId)) {
            Map<String, Object> batchDetails = batches.get(batchId).get(0);
            int status = getInteger(batchDetails.get("status"), 0);
            if (status == 1) {
              input
                  .getValue()
                  .stream()
                  .forEach(
                      data -> {
                        try {
                          syncAssessmentData(data);
                          updateMessages(respMessages, batchId, JsonKey.SUCCESS);
                        } catch (Exception e) {
                          ProjectLogger.log("Error syncing assessment data: " + e.getMessage(), e);
                        }
                      });
            } else {
              updateMessages(
                  respMessages, ContentUpdateResponseKeys.NOT_A_ON_GOING_BATCH.name(), batchId);
            }
          } else {
            updateMessages(
                respMessages, ContentUpdateResponseKeys.BATCH_NOT_EXISTS.name(), batchId);
          }
        }
        Response response = new Response();
        response.getResult().putAll(respMessages);
        sender().tell(response, self());
      }
      List<Map<String, Object>> contentList =
          (List<Map<String, Object>>) request.getRequest().get(JsonKey.CONTENTS);
      if (CollectionUtils.isNotEmpty(contentList)) {
        Map<String, List<Map<String, Object>>> batchContentList =
            contentList
                .stream()
                .filter(x -> StringUtils.isNotBlank((String) x.get("batchId")))
                .collect(
                    Collectors.groupingBy(
                        x -> {
                          return (String) x.get("batchId");
                        }));
        List<String> batchIds = batchContentList.keySet().stream().collect(Collectors.toList());
        Map<String, List<Map<String, Object>>> batches =
            getBatches(batchIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        x -> {
                          return (String) x.get("batchId");
                        }));
        Map<String, Object> respMessages = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> input : batchContentList.entrySet()) {
          String batchId = input.getKey();
          if (batches.containsKey(batchId)) {
            Map<String, Object> batchDetails = batches.get(batchId).get(0);
            String courseId = (String) batchDetails.get("courseId");
            int status = getInteger(batchDetails.get("status"), 0);
            if (status == 1) {
              List<String> contentIds =
                  input
                      .getValue()
                      .stream()
                      .map(c -> (String) c.get("contentId"))
                      .collect(Collectors.toList());
              Map<String, Map<String, Object>> existingContents =
                  getContents(userId, contentIds, batchId)
                      .stream()
                      .collect(
                          Collectors.groupingBy(
                              x -> {
                                return (String) x.get("contentId");
                              }))
                      .entrySet()
                      .stream()
                      .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get(0)));
              List<Map<String, Object>> contents =
                  input
                      .getValue()
                      .stream()
                      .map(
                          inputContent -> {
                            Map<String, Object> existingContent =
                                existingContents.get(inputContent.get("contentId"));
                            return processContent(inputContent, existingContent, userId);
                          })
                      .collect(Collectors.toList());

              cassandraOperation.batchInsert(
                  consumptionDBInfo.getKeySpace(), consumptionDBInfo.getTableName(), contents);
              Map<String, Object> updatedBatch = getBatchCurrentStatus(batchId, userId, contents);
              cassandraOperation.upsertRecord(
                  userCourseDBInfo.getKeySpace(), userCourseDBInfo.getTableName(), updatedBatch);
              // Generate Instruction event. Send userId, batchId, courseId, contents.
              pushInstructionEvent(userId, batchId, courseId, contents);
              contentIds.forEach(
                  contentId -> {
                    updateMessages(respMessages, contentId, JsonKey.SUCCESS);
                  });
            } else {
              updateMessages(
                  respMessages, ContentUpdateResponseKeys.NOT_A_ON_GOING_BATCH.name(), batchId);
            }
          } else {
            updateMessages(
                respMessages, ContentUpdateResponseKeys.BATCH_NOT_EXISTS.name(), batchId);
          }
        }
        Response response = new Response();
        response.getResult().putAll(respMessages);
        sender().tell(response, self());
      }
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private List<Map<String, Object>> getBatches(List<String> batchIds) {
    Map<String, Object> filters =
        new HashMap<String, Object>() {
          {
            put("batchId", batchIds);
          }
        };
    SearchDTO dto = new SearchDTO();
    dto.getAdditionalProperties().put(JsonKey.FILTERS, filters);
    Future<Map<String, Object>> searchFuture =
        esService.search(dto, ProjectUtil.EsType.courseBatch.getTypeName());
    Map<String, Object> response =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(searchFuture);
    return (List<Map<String, Object>>) response.get(JsonKey.CONTENT);
  }

  private List<Map<String, Object>> getContents(
      String userId, List<String> contentIds, String batchId) {
    Map<String, Object> filters =
        new HashMap<String, Object>() {
          {
            put("userid", userId);
            put("contentid", contentIds);
            put("batchid", batchId);
          }
        };
    Response response =
        cassandraOperation.getRecords(
            consumptionDBInfo.getKeySpace(), consumptionDBInfo.getTableName(), filters, null);
    List<Map<String, Object>> resultList =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(resultList)) {
      resultList = new ArrayList<>();
    }
    return resultList;
  }

  private Map<String, Object> processContent(
      Map<String, Object> inputContent, Map<String, Object> existingContent, String userId) {
    int inputStatus = getInteger(inputContent.get("status"), 0);
    Date inputCompletedDate =
        parseDate(inputContent.get(JsonKey.LAST_COMPLETED_TIME), simpleDateFormat);
    Date inputAccessTime = parseDate(inputContent.get(JsonKey.LAST_ACCESS_TIME), simpleDateFormat);
    if (MapUtils.isNotEmpty(existingContent)) {
      int viewCount = getInteger(existingContent.get(JsonKey.VIEW_COUNT), 0);
      inputContent.put(JsonKey.VIEW_COUNT, viewCount + 1);

      Date accessTime = parseDate(existingContent.get(JsonKey.LAST_ACCESS_TIME), simpleDateFormat);
      inputContent.put(JsonKey.LAST_ACCESS_TIME, compareTime(accessTime, inputAccessTime));

      int inputProgress = getInteger(inputContent.get(JsonKey.PROGRESS), 0);
      int existingProgress = getInteger(existingContent.get(JsonKey.PROGRESS), 0);
      int progress = Collections.max(Arrays.asList(inputProgress, existingProgress));
      inputContent.put(JsonKey.PROGRESS, progress);
      Date completedDate =
          parseDate(existingContent.get(JsonKey.LAST_COMPLETED_TIME), simpleDateFormat);

      int completedCount = getInteger(existingContent.get(JsonKey.COMPLETED_COUNT), 0);
      int existingStatus = getInteger(existingContent.get(JsonKey.STATUS), 0);
      if (inputStatus >= existingStatus) {
        if (inputStatus >= 2) {
          completedCount = completedCount + 1;
          inputContent.put(JsonKey.PROGRESS, 100);
          inputContent.put(
              JsonKey.LAST_COMPLETED_TIME, compareTime(completedDate, inputCompletedDate));
          inputContent.put(JsonKey.STATUS, 2);
        }
        inputContent.put(JsonKey.COMPLETED_COUNT, completedCount);
      } else {
        inputContent.put(JsonKey.STATUS, existingStatus);
      }
    } else {
      if (inputStatus >= 2) {
        inputContent.put(JsonKey.COMPLETED_COUNT, 1);
        inputContent.put(JsonKey.PROGRESS, 100);
        inputContent.put(JsonKey.LAST_COMPLETED_TIME, compareTime(null, inputCompletedDate));
      } else {
        inputContent.put(JsonKey.PROGRESS, getInteger(inputContent.get(JsonKey.PROGRESS), 0));
      }
      inputContent.put(JsonKey.VIEW_COUNT, 1);
      inputContent.put(JsonKey.LAST_ACCESS_TIME, compareTime(null, inputAccessTime));
    }
    inputContent.put(JsonKey.LAST_UPDATED_TIME, ProjectUtil.getFormattedDate());
    inputContent.put("userId", userId);
    return inputContent;
  }

  private Map<String, Object> getBatchCurrentStatus(
      String batchId, String userId, List<Map<String, Object>> contents) {
    Map<String, Object> lastAccessedContent =
        contents
            .stream()
            .max(
                Comparator.comparing(
                    x -> {
                      return parseDate(x.get(JsonKey.LAST_ACCESS_TIME), simpleDateFormat);
                    }))
            .get();
    Map<String, Object> courseBatch =
        new HashMap<String, Object>() {
          {
            put("batchId", batchId);
            put("userId", userId);
            put("lastreadcontentid", lastAccessedContent.get("contentId"));
            put("lastreadcontentstatus", lastAccessedContent.get("status"));
          }
        };
    return courseBatch;
  }

  private void updateMessages(Map<String, Object> messages, String key, Object value) {
    if (value instanceof List) {
      List list = (List) value;
      messages.put(key, list);
    } else {
      messages.put(key, value);
    }
  }

  private int getInteger(Object obj, int defaultValue) {
    int value = defaultValue;
    Number number = (Number) obj;
    if (null != number) {
      value = number.intValue();
    }
    return value;
  }

  private Date parseDate(Object obj, SimpleDateFormat formatter) {
    if (null == obj || ((String) obj).equalsIgnoreCase(JsonKey.NULL)) {
      return null;
    }
    Date date;
    try {
      date = formatter.parse((String) obj);
    } catch (ParseException ex) {
      ProjectLogger.log(ex.getMessage(), ex);
      throw new ProjectCommonException(
          ResponseCode.invalidDateFormat.getErrorCode(),
          ResponseCode.invalidDateFormat.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return date;
  }

  private String compareTime(Date currentValue, Date requestedValue) {
    SimpleDateFormat simpleDateFormat = ProjectUtil.getDateFormatter();
    simpleDateFormat.setLenient(false);
    if (currentValue == null && requestedValue == null) {
      return ProjectUtil.getFormattedDate();
    } else if (currentValue == null) {
      return simpleDateFormat.format(requestedValue);
    } else if (null == requestedValue) {
      return simpleDateFormat.format(currentValue);
    }
    return (requestedValue.after(currentValue)
        ? simpleDateFormat.format(requestedValue)
        : simpleDateFormat.format(currentValue));
  }

  private String generatePrimaryKey(Map<String, Object> req, String userId) {
    String contentId = (String) req.get(JsonKey.CONTENT_ID);
    String courseId = (String) req.get(JsonKey.COURSE_ID);
    String batchId = (String) req.get(JsonKey.BATCH_ID);
    String key =
        userId
            + JsonKey.PRIMARY_KEY_DELIMETER
            + contentId
            + JsonKey.PRIMARY_KEY_DELIMETER
            + courseId
            + JsonKey.PRIMARY_KEY_DELIMETER
            + batchId;
    return OneWayHashing.encryptVal(key);
  }

  /**
   * Construct the instruction event data and push the event data as BEInstructionEvent.
   *
   * @param userId
   * @param batchId
   * @param courseId
   * @param contents
   * @throws Exception
   */
  private void pushInstructionEvent(
      String userId, String batchId, String courseId, List<Map<String, Object>> contents)
      throws Exception {
    Map<String, Object> data = new HashMap<>();

    data.put(
        CourseJsonKey.ACTOR,
        new HashMap<String, Object>() {
          {
            put(JsonKey.ID, InstructionEvent.BATCH_USER_STATE_UPDATE.getActorId());
            put(JsonKey.TYPE, InstructionEvent.BATCH_USER_STATE_UPDATE.getActorType());
          }
        });

    data.put(
        CourseJsonKey.OBJECT,
        new HashMap<String, Object>() {
          {
            put(JsonKey.ID, batchId + CourseJsonKey.UNDERSCORE + userId);
            put(JsonKey.TYPE, InstructionEvent.BATCH_USER_STATE_UPDATE.getType());
          }
        });

    data.put(CourseJsonKey.ACTION, InstructionEvent.BATCH_USER_STATE_UPDATE.getAction());

    List<Map<String, Object>> contentsMap =
        contents
            .stream()
            .map(
                c -> {
                  return new HashMap<String, Object>() {
                    {
                      put(JsonKey.CONTENT_ID, c.get(JsonKey.CONTENT_ID));
                      put(JsonKey.STATUS, c.get(JsonKey.STATUS));
                    }
                  };
                })
            .collect(Collectors.toList());

    data.put(
        CourseJsonKey.E_DATA,
        new HashMap<String, Object>() {
          {
            put(JsonKey.USER_ID, userId);
            put(JsonKey.BATCH_ID, batchId);
            put(JsonKey.COURSE_ID, courseId);
            put(JsonKey.CONTENTS, contentsMap);
            put(CourseJsonKey.ACTION, InstructionEvent.BATCH_USER_STATE_UPDATE.getAction());
            put(CourseJsonKey.ITERATION, 1);
          }
        });
    String topic = ProjectUtil.getConfigValue("kafka_topics_instruction");
    ProjectLogger.log(
            "LearnerStateUpdateActor: pushInstructionEvent :Event Data "
                    + data+" and Topic "+topic,
            LoggerEnum.INFO.name());
    InstructionEventGenerator.pushInstructionEvent(topic, data);
  }

  private void syncAssessmentData(Map<String, Object> assessmentData) throws Exception {
    String topic = ProjectUtil.getConfigValue("kafka_assessment_topic");
    if (StringUtils.isNotBlank(topic)) {
      KafkaClient.send(mapper.writeValueAsString(assessmentData), topic);
    } else {
      throw new ProjectCommonException(
          "BE_JOB_REQUEST_EXCEPTION",
          "Invalid topic id.",
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
  }
}
