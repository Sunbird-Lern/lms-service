package org.sunbird.learner.actors.certificate;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
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
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.models.util.datasecurity.OneWayHashing;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.kafka.client.InstructionEventGenerator;
import org.sunbird.learner.actor.operations.CourseActorOperations;
import org.sunbird.learner.actors.coursebatch.service.CourseAssessmentService;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.constants.InstructionEvent;
import org.sunbird.learner.util.Util;
import scala.concurrent.Future;

@ActorConfig(
  tasks = {"issueCertificate"},
  asyncTasks = {}
)
public class CertificateActor extends BaseActor {

  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
  private CourseAssessmentService courseAssessmentService = new CourseAssessmentService();

  private static enum ResponseMessage {
    SUBMITTED("Certificates issue action for Course Batch Id {0} submitted Successfully!"),
    NO_USER("No user exists for provided requests");
    private String value;

    private ResponseMessage(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  };

  private static final int ES_MAX_LIMIT = 10000;

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    ExecutionContext.setRequestId(request.getRequestId());

    if (CourseActorOperations.ISSUE_CERTIFICATE
        .getValue()
        .equalsIgnoreCase(request.getOperation())) {
      issueCertificate(request);
    } else {
      onReceiveUnsupportedOperation(request.getOperation());
    }
  }

  private void issueCertificate(Request request) {
    ProjectLogger.log(
        "CertificateActor:issueCertificate request=" + request.getRequest(),
        LoggerEnum.INFO.name());
    final String batchId = (String) request.getRequest().get(JsonKey.BATCH_ID);
    final String courseId = (String) request.getRequest().get(JsonKey.COURSE_ID);
    final String certificateName = (String) request.getRequest().get(CourseJsonKey.CERTIFICATE);
    final boolean reIssue = isReissue(request.getContext().get(CourseJsonKey.REISSUE));
    validateCourseBatch(courseId, batchId);
    Map<String, Object> filters =
        request.getRequest().containsKey(JsonKey.FILTERS)
            ? (Map<String, Object>) request.getRequest().get(JsonKey.FILTERS)
            : new HashMap<>();
    Optional<Map<String, Object>> assessmentFilter =
        Optional.ofNullable((Map<String, Object>) filters.get(JsonKey.ASSESSMENT));
    filters.remove(JsonKey.ASSESSMENT);
    List<Map<String, Object>> esContents = getEnrollments(filters, batchId);
    Response response = new Response();
    Map<String, Object> resultData = new HashMap<>();
    resultData.put(
        JsonKey.STATUS, MessageFormat.format(ResponseMessage.SUBMITTED.getValue(), batchId));
    resultData.put(JsonKey.BATCH_ID, batchId);
    resultData.put(CourseJsonKey.CERTIFICATE, certificateName);
    resultData.put(JsonKey.COURSE_ID, courseId);
    if (CollectionUtils.isEmpty(esContents)) {
      resultData.put(JsonKey.STATUS, ResponseMessage.NO_USER.getValue());
    }
    response.put(JsonKey.RESULT, resultData);
    sender().tell(response, self());
    if (CollectionUtils.isNotEmpty(esContents)) {
      ProjectLogger.log(
          "CertificateActor:issueCertificate user size=" + esContents.size(),
          LoggerEnum.INFO.name());
      assessmentFilter.ifPresent(
          filter ->
              courseAssessmentService.fetchFilteredAssessmentUser(
                  courseId, batchId, filter, esContents));
      Optional<Set<String>> users =
          assessmentFilter.map(
              filter ->
                  courseAssessmentService.fetchFilteredAssessmentUser(
                      courseId, batchId, filter, esContents));
      users.ifPresent(
          userList -> {
            userList
                .stream()
                .forEach(
                    userId -> {
                      try {
                        pushInstructionEvent(userId, batchId, courseId, certificateName, reIssue);
                      } catch (Exception e) {
                        ProjectLogger.log(
                            "CertificateActor:issueCertificate pushInstructionEvent error for userId="
                                + userId,
                            e);
                      }
                    });
          });
      //      esContents
      //          .stream()
      //          .forEach(
      //              userCourse -> {
      //                String userId = (String) userCourse.get(JsonKey.USER_ID);
      //                try {
      //                  pushInstructionEvent(userId, batchId, courseId, certificateName, reIssue);
      //                } catch (Exception e) {
      //                  ProjectLogger.log(
      //                      "CertificateActor:issueCertificate pushInstructionEvent error for
      // userId="
      //                          + userId,
      //                      e);
      //                }
      //              });
    }
  }

  private boolean isReissue(Object queryString) {
    if (queryString != null) {
      if (queryString instanceof String[]) {
        String query = Arrays.stream((String[]) queryString).findFirst().orElse(null);
        return Boolean.parseBoolean(query);
      } else if (queryString instanceof String) {
        return Boolean.parseBoolean((String) queryString);
      }
    }
    return false;
  }

  private void validateCourseBatch(String courseId, String batchId) {
    Future<Map<String, Object>> resultF =
        esService.getDataByIdentifier(EsType.courseBatch.getTypeName(), batchId);
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (MapUtils.isEmpty(result)) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "No such batchId exists");
    }
    if (courseId != null && !courseId.equals(result.get(JsonKey.COURSE_ID))) {
      ProjectCommonException.throwClientErrorException(
          ResponseCode.CLIENT_ERROR, "batchId is not linked with courseId");
    }
  }

  private List<Map<String, Object>> getEnrollments(Map<String, Object> filters, String batchId) {
    filters.put(JsonKey.BATCH_ID, batchId);
    filters.put(JsonKey.ACTIVE, true);
    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filters);
    searchDTO.setLimit(ES_MAX_LIMIT);
    searchDTO.setFields(Arrays.asList(JsonKey.USER_ID));
    List<Map<String, Object>> esContents = null;
    Future<Map<String, Object>> resultF =
        esService.search(searchDTO, EsType.usercourses.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    if (MapUtils.isNotEmpty(result)) {
      esContents = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
    }
    return esContents;
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
      String userId, String batchId, String courseId, String certificateName, boolean reIssue)
      throws Exception {
    Map<String, Object> data = new HashMap<>();

    data.put(
        CourseJsonKey.ACTOR,
        new HashMap<String, Object>() {
          {
            put(JsonKey.ID, InstructionEvent.ISSUE_COURSE_CERTIFICATE.getActorId());
            put(JsonKey.TYPE, InstructionEvent.ISSUE_COURSE_CERTIFICATE.getActorType());
          }
        });

    String id =
        OneWayHashing.encryptVal(
            batchId
                + CourseJsonKey.UNDERSCORE
                + userId
                + CourseJsonKey.UNDERSCORE
                + certificateName);
    data.put(
        CourseJsonKey.OBJECT,
        new HashMap<String, Object>() {
          {
            put(JsonKey.ID, id);
            put(JsonKey.TYPE, InstructionEvent.ISSUE_COURSE_CERTIFICATE.getType());
          }
        });

    data.put(CourseJsonKey.ACTION, InstructionEvent.ISSUE_COURSE_CERTIFICATE.getAction());

    data.put(
        CourseJsonKey.E_DATA,
        new HashMap<String, Object>() {
          {
            put(JsonKey.USER_ID, userId);
            put(JsonKey.BATCH_ID, batchId);
            put(JsonKey.COURSE_ID, courseId);
            put(CourseJsonKey.CERTIFICATE, certificateName);
            put(CourseJsonKey.ACTION, InstructionEvent.ISSUE_COURSE_CERTIFICATE.getAction());
            put(CourseJsonKey.ITERATION, 1);
            if (reIssue) {
              put(CourseJsonKey.REISSUE, true);
            }
          }
        });
    String topic = ProjectUtil.getConfigValue("kafka_topics_certificate_instruction");
    InstructionEventGenerator.pushInstructionEvent(topic, data);
  }
}
