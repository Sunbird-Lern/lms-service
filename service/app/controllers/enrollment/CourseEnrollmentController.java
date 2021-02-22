package controllers.enrollment;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.enrollment.validator.CourseEnrollmentRequestValidator;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;
import org.sunbird.common.Common;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class CourseEnrollmentController extends BaseController {

  @Inject
  @Named("course-enrolment-actor")
  private ActorRef courseEnrolmentActor;

  public CompletionStage<Result> getEnrolledCourses(String uid, Http.Request httpRequest) {
    return handleRequest(courseEnrolmentActor, "listEnrol", 
        httpRequest.body().asJson(),
        (req) -> {
          Request request = (Request) req;
          Map<String, String[]> queryParams = new HashMap<>(httpRequest.queryString());
          if(queryParams.containsKey("fields")) {
              Set<String> fields = new HashSet<>(Arrays.asList(queryParams.get("fields")[0].split(",")));
              fields.addAll(Arrays.asList(JsonKey.NAME, JsonKey.DESCRIPTION, JsonKey.LEAF_NODE_COUNT, JsonKey.APP_ICON));
              queryParams.put("fields", fields.toArray(new String[0]));
          }
          request
              .getContext()
              .put(JsonKey.URL_QUERY_STRING, getQueryString(queryParams));
          request
              .getContext()
              .put(JsonKey.BATCH_DETAILS, httpRequest.queryString().get(JsonKey.BATCH_DETAILS));
            if (queryParams.containsKey("cache")) {
                request.getContext().put("cache", Boolean.parseBoolean(queryParams.get("cache")[0]));
            } else
                request.getContext().put("cache", true);
          return null;
        },
        ProjectUtil.getLmsUserId(uid),
        JsonKey.USER_ID,
        getAllRequestHeaders((httpRequest)),
        false,
        httpRequest);
  }

  /*public CompletionStage<Result> getEnrolledCourse(Http.Request httpRequest) {
    return handleRequest(
        learnerStateActorRef,
        ActorOperations.GET_USER_COURSE.getValue(),
        httpRequest.body().asJson(),
        (req) -> {
          Request request = (Request) req;
          new CourseEnrollmentRequestValidator().validateEnrolledCourse(request);
          return null;
        },
        getAllRequestHeaders((httpRequest)),
        httpRequest);
  }*/

  public CompletionStage<Result> enrollCourse(Http.Request httpRequest) {
    return handleRequest(courseEnrolmentActor, "enrol",
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          Common.handleFixedBatchIdRequest(req);
          new CourseEnrollmentRequestValidator().validateEnrollCourse(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> unenrollCourse(Http.Request httpRequest) {
    return handleRequest(
            courseEnrolmentActor, "unenrol",
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          Common.handleFixedBatchIdRequest(req);
          new CourseEnrollmentRequestValidator().validateUnenrollCourse(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> getParticipantsForFixedBatch(Http.Request httpRequest) {
      return handleRequest(courseEnrolmentActor, "getParticipantsForFixedBatch",
              httpRequest.body().asJson(),
              (request) -> {
                  Common.handleFixedBatchIdRequest((Request) request);
                  new CourseEnrollmentRequestValidator().validateCourseParticipant((Request) request);
                  return null;
              },
              getAllRequestHeaders(httpRequest),
              httpRequest);
  }

    public CompletionStage<Result> getUserEnrolledCourses(Http.Request httpRequest) {
        return handleRequest(
                courseEnrolmentActor, "listEnrol",
                httpRequest.body().asJson(),
                (req) -> {
                    Request request = (Request) req;
                    Map<String, String[]> queryParams = new HashMap<>(httpRequest.queryString());
                    if(queryParams.containsKey("fields")) {
                        Set<String> fields = new HashSet<>(Arrays.asList(queryParams.get("fields")[0].split(",")));
                        fields.addAll(Arrays.asList(JsonKey.NAME, JsonKey.DESCRIPTION, JsonKey.LEAF_NODE_COUNT, JsonKey.APP_ICON));
                        queryParams.put("fields", fields.toArray(new String[0]));
                    }
                    request
                            .getContext()
                            .put(JsonKey.URL_QUERY_STRING, getQueryString(queryParams));
                    request
                            .getContext()
                            .put(JsonKey.BATCH_DETAILS, httpRequest.queryString().get(JsonKey.BATCH_DETAILS));
                    new CourseEnrollmentRequestValidator().validateUserEnrolledCourse(request);
                    request.getContext().put(JsonKey.USER_ID, request.get(JsonKey.USER_ID));
                    return null;
                },
                getAllRequestHeaders((httpRequest)),
                httpRequest);
    }
}
