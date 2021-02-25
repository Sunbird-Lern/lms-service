/** */
package controllers.coursemanagement;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import controllers.coursemanagement.validator.CourseBatchRequestValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;

public class CourseBatchController extends BaseController {

  @Inject
  @Named("course-batch-management-actor")
  private ActorRef courseBatchActorRef;

  @Inject
  @Named("search-handler-actor")
  private ActorRef compositeSearchActorRef;

  public CompletionStage<Result> createBatch(Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
        ActorOperations.CREATE_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          String courseId = req.getRequest().containsKey(JsonKey.COURSE_ID) ? JsonKey.COURSE_ID : JsonKey.COLLECTION_ID;
          req.getRequest().put(JsonKey.COURSE_ID, req.getRequest().get(courseId));
          new CourseBatchRequestValidator().validateCreateCourseBatchRequest(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> privateCreateBatch(Http.Request httpRequest) {
      return createBatch(httpRequest);
  }

  public CompletionStage<Result> getBatch(String batchId, Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
        ActorOperations.GET_BATCH.getValue(),
        batchId,
        JsonKey.BATCH_ID,
        false,
        httpRequest);
  }

  public CompletionStage<Result> updateBatch(Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
        ActorOperations.UPDATE_BATCH.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          String courseId = req.getRequest().containsKey(JsonKey.COURSE_ID) ? JsonKey.COURSE_ID : JsonKey.COLLECTION_ID;
          req.getRequest().put(JsonKey.COURSE_ID, req.getRequest().get(courseId));
          new CourseBatchRequestValidator().validateUpdateCourseBatchRequest(req);
          return null;
        },
        httpRequest);
  }

  public CompletionStage<Result> addUserToCourseBatch(String batchId, Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
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

  public CompletionStage<Result> removeUserFromCourseBatch(
      String batchId, Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
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
        reqObj.put("creatorDetails", httpRequest.queryString().containsKey("creatorDetails"));
      reqObj.setOperation(ActorOperations.COMPOSITE_SEARCH.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      reqObj.put(JsonKey.REQUESTED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
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
      return actorResponseHandler(compositeSearchActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> getParticipants(Http.Request httpRequest) {
    return handleRequest(
        courseBatchActorRef,
        ActorOperations.GET_PARTICIPANTS.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
            Request req = (Request) request;
            String batchIdKey = req.getRequest().containsKey(JsonKey.BATCH_ID)
                    ? JsonKey.BATCH_ID
                    : JsonKey.FIXED_BATCH_ID;
            req.getRequest().put(JsonKey.BATCH_ID, req.getRequest().get(batchIdKey));
          new CourseBatchRequestValidator().validateGetParticipantsRequest(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }
}
