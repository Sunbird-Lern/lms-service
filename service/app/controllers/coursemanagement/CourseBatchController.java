/** */
package controllers.coursemanagement;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import controllers.coursemanagement.validator.CourseBatchRequestValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import play.mvc.Http;
import play.mvc.Result;

public class CourseBatchController extends BaseController {

  public CompletionStage<Result> createBatch(Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.CREATE_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseBatchRequestValidator().validateCreateCourseBatchRequest((Request) request);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> getBatch(String batchId, Http.Request httpRequest) {
    return handleRequest(ActorOperations.GET_BATCH.getValue(), batchId, JsonKey.BATCH_ID, false, httpRequest);
  }

  public CompletionStage<Result> updateBatch(Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.UPDATE_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseBatchRequestValidator().validateUpdateCourseBatchRequest((Request) request);
          return null;
        },
        httpRequest);
  }

  public CompletionStage<Result> addUserToCourseBatch(String batchId, Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.ADD_USER_TO_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseBatchRequestValidator().validateAddUserToCourseBatchRequest((Request) request);
          return null;
        },
        batchId,
        JsonKey.BATCH_ID,
        httpRequest);
  }

  public CompletionStage<Result> removeUserFromCourseBatch(String batchId, Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.REMOVE_USER_FROM_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseBatchRequestValidator().validateAddUserToCourseBatchRequest((Request) request);
          return null;
        },
        batchId,
        JsonKey.BATCH_ID,
        httpRequest);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public CompletionStage<Result> search(Http.Request httpRequest) {
    try {
      JsonNode requestData = httpRequest.body().asJson();
      ProjectLogger.log(
          "CourseBatchController: search called with data = " + requestData,
          LoggerEnum.DEBUG.name());
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      reqObj.setOperation(ActorOperations.COMPOSITE_SEARCH.getValue());
      reqObj.setRequestId(ExecutionContext.getRequestId());
      reqObj.setEnv(getEnvironment());
      reqObj.put(JsonKey.REQUESTED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      String requestedField = httpRequest.getQueryString(JsonKey.FIELDS);
      reqObj.getContext().put(JsonKey.PARTICIPANTS, requestedField);
      List<String> esObjectType = new ArrayList<>();
      esObjectType.add(EsType.courseBatch.getTypeName());
      if (reqObj.getRequest().containsKey(JsonKey.FILTERS)
          && reqObj.getRequest().get(JsonKey.FILTERS) != null
          && reqObj.getRequest().get(JsonKey.FILTERS) instanceof Map) {
        ((Map) (reqObj.getRequest().get(JsonKey.FILTERS))).put(JsonKey.OBJECT_TYPE, esObjectType);
      } else {
        Map<String, Object> filtermap = new HashMap<>();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(JsonKey.OBJECT_TYPE, esObjectType);
        filtermap.put(JsonKey.FILTERS, dataMap);
      }
      return actorResponseHandler(getActorRef(), reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> getParticipants(Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.GET_PARTICIPANTS.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseBatchRequestValidator().validateGetParticipantsRequest((Request) request);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }
}
