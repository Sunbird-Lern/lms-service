package org.sunbird.learner.actors.certificate.service;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.sunbird.learner.actors.certificate.dao.CertificateDao;
import org.sunbird.learner.actors.certificate.dao.impl.CertificateDaoImpl;
import org.sunbird.learner.actors.coursebatch.CourseEnrollmentActor;
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.constants.InstructionEvent;
import org.sunbird.learner.util.Util;
import scala.concurrent.Future;

@ActorConfig(
  tasks = {"issueCertificate","addCertificate","getCertificate"},
  asyncTasks = {}
)
public class CertificateActor extends BaseActor {

  private ElasticSearchService esService = EsClientFactory.getInstance(JsonKey.REST);
    private CertificateDao certificateDao = new CertificateDaoImpl();
    private ObjectMapper mapper = new ObjectMapper();

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

    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "issueCertificate":
        issueCertificate(request);
        break;
      case "addCertificate":
        addCertificate(request);
        break;
        case "getCertificate":
            getCertificateList(request);
            break;
      default:
        onReceiveUnsupportedOperation(request.getOperation());
        break;
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
      esContents
          .stream()
          .forEach(
              userCourse -> {
                String userId = (String) userCourse.get(JsonKey.USER_ID);
                try {
                  pushInstructionEvent(userId, batchId, courseId, certificateName, reIssue);
                } catch (Exception e) {
                  ProjectLogger.log(
                      "CertificateActor:issueCertificate pushInstructionEvent error for userId="
                          + userId,
                      e);
                }
              });
    }
  }

  private void getCertificateList(Request request){
      ProjectLogger.log(
              "CertificateActor:getCertificateList request=" + request.getRequest(),
              LoggerEnum.INFO.name());
      final String courseId = (String) request.getRequest().get(JsonKey.COURSE_ID);
      Map<String, String> headers =
              (Map<String, String>) request.getContext().get(JsonKey.HEADER);
      String batchId =
              request.getRequest().containsKey(JsonKey.BATCH_ID)
                      ? (String)request.getRequest().get(JsonKey.BATCH_ID)
                      : null;
      List<Map<String, Object>>  result = certificateDao.readById(courseId, batchId);
      Response response = new Response();
      response.put(JsonKey.RESPONSE, result);
      sender().tell(response, self());
  }

    private void addCertificate(Request request){
        ProjectLogger.log(
                "CertificateActor:addCertificate request=" + request.getRequest(),
                LoggerEnum.INFO.name());
        final String courseId = (String) request.getRequest().get(JsonKey.COURSE_ID);
        final Map<String,Object> template = ( Map<String,Object>) request.getRequest().get(CourseJsonKey.TEMPLATE);
        Map<String, String> headers =
                (Map<String, String>) request.getContext().get(JsonKey.HEADER);
      //  validateCourseDetails(courseId,headers);
        String batchId =
                request.getRequest().containsKey(JsonKey.BATCH_ID)
                        ? (String)request.getRequest().get(JsonKey.BATCH_ID)
                        : StringUtils.EMPTY;
        if(StringUtils.isNotBlank(batchId)){
          //  validateCourseBatch(courseId,batchId);
        }
        String requestedBy = (String) request.getContext().get(JsonKey.REQUESTED_BY);
        Map<String, Object> filters =
                request.getRequest().containsKey(JsonKey.FILTERS)
                        ? (Map<String, Object>) request.getRequest().get(JsonKey.FILTERS)
                        : new HashMap<>();
        Map<String,Object> requestMap = request.getRequest();
        requestMap.put(JsonKey.ADDED_BY,requestedBy);
        try {
            requestMap.put(JsonKey.FILTERS, mapper.writeValueAsString(filters));
            requestMap.put(CourseJsonKey.TEMPLATE, mapper.writeValueAsString(template));
        }
        catch (Exception e){
            ProjectLogger.log(
                    "CertificateActor:addCertificate Exception occurred with error message ==" + e.getMessage(),
                    LoggerEnum.INFO.name());
        }
        requestMap.put(JsonKey.BATCH_ID,batchId);
        ProjectLogger.log(
                "CertificateActor:addCertificate certificateDbRecord=" + requestMap,
                LoggerEnum.INFO.name());
        certificateDao.add(requestMap);
        Response result = new Response();
        result.put("response", "SUCCESS");
        sender().tell(result, self());

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

    private Map<String, Object> validateCourseDetails(String courseId, Map<String, String> headers) {
        Map<String, Object> ekStepContent =
                CourseEnrollmentActor.getCourseObjectFromEkStep(courseId, headers);
        if (MapUtils.isEmpty(ekStepContent )|| ekStepContent.size() == 0) {
            ProjectLogger.log(
                    "CertificateActor:validateCourseDetails: Not found course for ID = " + courseId,
                    LoggerEnum.INFO.name());
            throw new ProjectCommonException(
                    ResponseCode.invalidCourseId.getErrorCode(),
                    ResponseCode.invalidCourseId.getErrorMessage(),
                    ResponseCode.CLIENT_ERROR.getResponseCode());
        }
        return ekStepContent;
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
