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
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.constants.InstructionEvent;
import org.sunbird.learner.util.JsonUtil;
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
      addContent(request);
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void addContent(Request request) throws Exception {
    String requestedBy = (String) request.getRequest().get(JsonKey.REQUESTED_BY);
    String requestedFor = (String) request.getRequest().getOrDefault(SunbirdKey.REQUESTED_FOR, "");
    // Here we are identifying the requestedBy and requestedFor as valid users for processing contents and assessments.
    List<String> validUserIds = getUserIds(requestedBy, requestedFor);
    List<Map<String, Object>> assessments = (List<Map<String, Object>>) request.getRequest().get(JsonKey.ASSESSMENT_EVENTS);
    if (CollectionUtils.isNotEmpty(assessments)) {
      Map<String, List<Map<String, Object>>> batchAssessmentList = assessments.stream()
              .filter(x -> StringUtils.isNotBlank((String) x.get("batchId"))).collect(Collectors.groupingBy(x -> (String) x.get("batchId")));
      List<String> batchIds = batchAssessmentList.keySet().stream().collect(Collectors.toList());
      Map<String, List<Map<String, Object>>> batches = getBatches(batchIds).stream().collect(Collectors.groupingBy(x -> (String) x.get("batchId")));
      Map<String, Object> respMessages = new HashMap<>();
      List<Map<String, Object>> invalidAssessments = new ArrayList<>();
      for (Map.Entry<String, List<Map<String, Object>>> input : batchAssessmentList.entrySet()) {
        String batchId = input.getKey();
        if (batches.containsKey(batchId)) {
          Map<String, Object> batchDetails = batches.get(batchId).get(0);
          int status = getInteger(batchDetails.get("status"), 0);
          if (status == 1) {
            // Actual processing of the Assessment data.
            // Filter the records which are not of the authorized user of this request. Then, process it.
            for (String userId: validUserIds) {
              input.getValue().stream().filter(assessment -> {
                String assessmentUserId = (String) assessment.getOrDefault(JsonKey.USER_ID, "");
                return StringUtils.isBlank(assessmentUserId) || StringUtils.equalsIgnoreCase(assessmentUserId, userId);
              }).forEach(data -> {
                try {
                  syncAssessmentData(data);
                  updateMessages(respMessages, batchId, JsonKey.SUCCESS);
                } catch (Exception e) {
                  ProjectLogger.log("Error syncing assessment data: " + e.getMessage(), e);
                }
              });
              List<Map<String, Object>> invalidList = input.getValue().stream().filter(assessment -> {
                String assessmentUserId = (String) assessment.getOrDefault(JsonKey.USER_ID, "");
                return StringUtils.isNotBlank(assessmentUserId) || !validUserIds.contains(assessmentUserId);
              }).collect(Collectors.toList());
              invalidAssessments.addAll(invalidList);
            }
          } else {
            updateMessages(respMessages, ContentUpdateResponseKeys.NOT_A_ON_GOING_BATCH.name(), batchId);
          }
        } else {
          updateMessages(respMessages, ContentUpdateResponseKeys.BATCH_NOT_EXISTS.name(), batchId);
        }
      }

      if (CollectionUtils.isNotEmpty(invalidAssessments)) {
        Map<String, Object> map = new HashMap<String, Object>() {{
          put("validUserIds", validUserIds);
          put("invalidAssessments", invalidAssessments);
          put("ets", System.currentTimeMillis());
        }};
        pushInvalidDataToKafka(map, "Assessments");
      }

      Response response = new Response();
      response.getResult().putAll(respMessages);
      sender().tell(response, self());
    }

    List<Map<String, Object>> contentList = (List<Map<String, Object>>) request.getRequest().get(JsonKey.CONTENTS);
    if (CollectionUtils.isNotEmpty(contentList)) {
      Map<String, List<Map<String, Object>>> batchContentList = contentList.stream()
              .filter(x -> StringUtils.isNotBlank((String) x.get("batchId"))).collect(Collectors.groupingBy(x -> (String) x.get("batchId")));
      List<String> batchIds = batchContentList.keySet().stream().collect(Collectors.toList());
      Map<String, List<Map<String, Object>>> batches = getBatches(batchIds).stream().collect(Collectors.groupingBy(x -> (String) x.get("batchId")));
      Map<String, Object> respMessages = new HashMap<>();
      List<Map<String, Object>> invalidContents = new ArrayList<>();
      for (Map.Entry<String, List<Map<String, Object>>> input : batchContentList.entrySet()) {
        String batchId = input.getKey();
        if (batches.containsKey(batchId)) {
          Map<String, Object> batchDetails = batches.get(batchId).get(0);
          String courseId = (String) batchDetails.get("courseId");
          int status = getInteger(batchDetails.get("status"), 0);
          if (status == 1) {
            // Actual processing of the Assessment data.
            // Filter the records which are not of the authorized user of this request. Then, process it.
            for (String userId : validUserIds) {
              List<Map<String, Object>> filteredContents = input.getValue().stream().filter(content -> {
                String contentUserId = (String) content.getOrDefault(JsonKey.USER_ID, "");
                return StringUtils.isBlank(contentUserId) || StringUtils.equalsIgnoreCase(contentUserId, userId);
              }).collect(Collectors.toList());
              if (CollectionUtils.isNotEmpty(filteredContents)) {
                List<String> contentIds = filteredContents.stream().map(c -> (String) c.get("contentId")).collect(Collectors.toList());
                Map<String, Map<String, Object>> existingContents =
                        getContents(userId, contentIds, batchId).stream()
                                .collect(Collectors.groupingBy(x -> (String) x.get("contentId")))
                                .entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get(0)));

                List<Map<String, Object>> contents = filteredContents.stream()
                        .map(inputContent -> {
                          Map<String, Object> existingContent =
                                  existingContents.get(inputContent.get("contentId"));
                          return processContent(inputContent, existingContent, userId);
                        })
                        .collect(Collectors.toList());
                cassandraOperation.batchInsert(consumptionDBInfo.getKeySpace(), consumptionDBInfo.getTableName(), contents);
                Map<String, Object> updatedBatch = getBatchCurrentStatus(batchId, userId, contents);
                cassandraOperation.upsertRecord(userCourseDBInfo.getKeySpace(), userCourseDBInfo.getTableName(), updatedBatch);
                // Generate Instruction event. Send userId, batchId, courseId, contents.
                pushInstructionEvent(userId, batchId, courseId, contents);
                contentIds.forEach(contentId -> updateMessages(respMessages, contentId, JsonKey.SUCCESS));
              }
            }
            List<Map<String, Object>> invalidList = input.getValue().stream().filter(content -> {
              String contentUserId = (String) content.getOrDefault(JsonKey.USER_ID, "");
              return StringUtils.isNotBlank(contentUserId) && !validUserIds.contains(contentUserId);
            }).collect(Collectors.toList());
            invalidContents.addAll(invalidList);
          } else {
            updateMessages(respMessages, ContentUpdateResponseKeys.NOT_A_ON_GOING_BATCH.name(), batchId);
          }
        } else {
          updateMessages(respMessages, ContentUpdateResponseKeys.BATCH_NOT_EXISTS.name(), batchId);
        }
      }

      if (CollectionUtils.isNotEmpty(invalidContents)) {
        Map<String, Object> map = new HashMap<String, Object>() {{
          put("validUserIds", validUserIds);
          put("invalidContents", invalidContents);
          put("ets", System.currentTimeMillis());
        }};
        pushInvalidDataToKafka(map, "Contents");
      }
      Response response = new Response();
      response.getResult().putAll(respMessages);
      sender().tell(response, self());
    }
  }

  private List<String> getUserIds(String requestedBy, String requestedFor) {
    return Arrays.asList(requestedBy, requestedFor).stream().filter(uId -> StringUtils.isNotBlank(uId)).collect(Collectors.toList());
  }

  private void pushInvalidDataToKafka(Map<String, Object> data, String dataType) {
    ProjectLogger.log("LearnerStateUpdater - Invalid " + dataType, data, LoggerEnum.INFO.name());
    String topic = ProjectUtil.getConfigValue("kafka_topics_contentstate_invalid");
    try {
      String event = mapper.writeValueAsString(data);
      KafkaClient.send(event,topic);
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private String getAllUserIds(List<Map<String, Object>> contents, List<Map<String, Object>> assessments) throws Exception {
    Map<String, Object> map = new HashMap<>();
    if (CollectionUtils.isNotEmpty(contents)) {
      List<String> contentUserIds = contents.stream().map(content -> (String) content.getOrDefault(JsonKey.USER_ID, ""))
              .filter(uId -> StringUtils.isNotBlank(uId)).collect(Collectors.toList());
      map.put("assessmentUserIds", contentUserIds);
    }
    if (CollectionUtils.isNotEmpty(assessments)) {
      List<String> assessmentUserIds = assessments.stream().map(assessment -> (String) assessment.getOrDefault(JsonKey.USER_ID, ""))
              .filter(uId -> StringUtils.isNotBlank(uId)).collect(Collectors.toList());
      map.put("assessmentUserIds", assessmentUserIds);
    }
    return JsonUtil.serialize(map);
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
    InstructionEventGenerator.pushInstructionEvent(userId, topic, data);
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

  private void verifyRequestedByAndThrowErrorIfNotMatch(String userId, String requestedBy, String requestedFor, Request request) throws Exception {
    if (!(userId.equals(requestedBy)) && !(userId.equals(requestedFor))) {
      String userIdInRequestGlobal = (String) request.getRequest().get(JsonKey.USER_ID);
      List<Map<String, Object>> contentList = (List<Map<String, Object>>) request.getRequest().get(JsonKey.CONTENTS);
      List<Map<String, Object>> assessments = (List<Map<String, Object>>) request.getRequest().get(JsonKey.ASSESSMENT_EVENTS);
      String allUserIds = getAllUserIds(contentList, assessments);
      ProjectLogger.log("LearnerStateUpdateActor:verifyRequestedByAndThrowErrorIfNotMatch : validation failed: " +
              "userId: " + userId + " :: requestedBy: " + requestedBy + " :: requestedFor: "+ requestedFor
              + " :: userIdInRequestGlobal: " + userIdInRequestGlobal
              + " :: allUserIds: " + allUserIds
              + " :: END", LoggerEnum.INFO.name());
      ProjectCommonException.throwUnauthorizedErrorException();
    }
  }
}
