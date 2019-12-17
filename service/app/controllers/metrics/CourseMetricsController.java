package controllers.metrics;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.metrics.validator.CourseMetricsProgressValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

public class CourseMetricsController extends BaseController {
  private static final String DEFAULT_LIMIT = "200";
  private static final String DEFAULT_OFFSET = "0";

  @Inject
  @Named("course-metrics-actor")
  private ActorRef courseMetricsActorRef;

  public CompletionStage<Result> courseProgress(String batchId, Http.Request httpRequest) {
    try {
      String periodStr = httpRequest.getQueryString("period");
      Map<String, Object> map = new HashMap<>();
      Request request = new Request();
      request.setEnv(getEnvironment());
      map.put(JsonKey.BATCH_ID, batchId);
      map.put(JsonKey.PERIOD, periodStr);
      map.put(JsonKey.REQUESTED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      request.setRequest(map);
      request.setOperation(ActorOperations.COURSE_PROGRESS_METRICS.getValue());
      request.setRequest(map);
      request.setRequestId(ExecutionContext.getRequestId());
      return actorResponseHandler(courseMetricsActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> courseProgressV2(String batchId, Http.Request httpRequest) {
    String limit = httpRequest.getQueryString(JsonKey.LIMIT);
    limit = StringUtils.isEmpty(limit) ? DEFAULT_LIMIT : limit;

    String offset = httpRequest.getQueryString(JsonKey.OFFSET);
    offset =
        StringUtils.isEmpty(offset) ? DEFAULT_OFFSET : httpRequest.getQueryString(JsonKey.OFFSET);

    final String sortOrder = httpRequest.getQueryString(JsonKey.SORT_ORDER);
    final String sortBy = httpRequest.getQueryString(JsonKey.SORTBY);
    final String userName = httpRequest.getQueryString(JsonKey.USERNAME);
    new CourseMetricsProgressValidator()
        .validateCourseProgressMetricsV2Request(limit, offset, sortOrder);
    final int dataLimit = Integer.parseInt(limit);
    final int dataOffset = Integer.parseInt(offset);

    return handleRequest(
        courseMetricsActorRef,
        ActorOperations.COURSE_PROGRESS_METRICS_V2.getValue(),
        (request) -> {
          Request req = (Request) request;
          req.getContext().put(JsonKey.LIMIT, dataLimit);
          req.getContext().put(JsonKey.BATCH_ID, batchId);
          req.getContext().put(JsonKey.OFFSET, dataOffset);
          req.getContext().put(JsonKey.SORTBY, sortBy);
          req.getContext().put(JsonKey.USERNAME, userName);
          req.getContext().put(JsonKey.SORT_ORDER, sortOrder);
          return null;
        },
        httpRequest);
  }

  public CompletionStage<Result> courseCreation(String courseId, Http.Request httpRequest) {
    try {
      String periodStr = httpRequest.getQueryString("period");
      Map<String, Object> map = new HashMap<>();
      Request request = new Request();
      request.setEnv(getEnvironment());
      request.setOperation(ActorOperations.COURSE_CREATION_METRICS.getValue());
      map.put(JsonKey.COURSE_ID, courseId);
      map.put(JsonKey.PERIOD, periodStr);
      map.put(JsonKey.REQUESTED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      request.setRequest(map);
      request.setRequestId(ExecutionContext.getRequestId());
      return actorResponseHandler(courseMetricsActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> courseProgressReport(String batchId, Http.Request httpRequest) {
    try {
      String periodStr = httpRequest.getQueryString(JsonKey.PERIOD);
      String reportType = httpRequest.getQueryString(JsonKey.FORMAT);
      if (StringUtils.isEmpty(periodStr)) {
         periodStr = JsonKey.FROM_BEGINING;
      }
      Map<String, Object> map = new HashMap<>();
      Request request = new Request();
      request.setEnv(getEnvironment());
      map.put(JsonKey.BATCH_ID, batchId);
      map.put(JsonKey.PERIOD, periodStr);
      map.put(JsonKey.FORMAT, reportType);
      map.put(JsonKey.REQUESTED_BY, httpRequest.flash().get(JsonKey.USER_ID));
      request.setRequest(map);
      request.setOperation(ActorOperations.COURSE_PROGRESS_METRICS_REPORT.getValue());
      request.setRequest(map);
      request.setRequestId(ExecutionContext.getRequestId());
      return actorResponseHandler(courseMetricsActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }
}
