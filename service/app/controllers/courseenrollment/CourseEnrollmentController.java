package controllers.courseenrollment;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.courseenrollment.validator.CourseEnrollmentRequestValidator;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

public class CourseEnrollmentController extends BaseController {

  @Inject
  @Named("course-enrollment-actor")
  private ActorRef courseEnrollmentActorRef;

  @Inject
  @Named("learner-state-actor")
  private ActorRef learnerStateActorRef;

  public CompletionStage<Result> getEnrolledCourses(String uid, Http.Request httpRequest) {
    return handleRequest(
        learnerStateActorRef,
        ActorOperations.GET_COURSE.getValue(),
        httpRequest.body().asJson(),
        (req) -> {
          Request request = (Request) req;
          request
              .getContext()
              .put(JsonKey.URL_QUERY_STRING, getQueryString(httpRequest.queryString()));
          request
              .getContext()
              .put(JsonKey.BATCH_DETAILS, httpRequest.queryString().get(JsonKey.BATCH_DETAILS));
          return null;
        },
        ProjectUtil.getLmsUserId(uid),
        JsonKey.USER_ID,
        getAllRequestHeaders((httpRequest)),
        false,
        httpRequest);
  }

  public CompletionStage<Result> getEnrolledCourse(Http.Request httpRequest) {
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
  }

  public CompletionStage<Result> enrollCourse(Http.Request httpRequest) {
    return handleRequest(
        courseEnrollmentActorRef,
        ActorOperations.ENROLL_COURSE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseEnrollmentRequestValidator().validateEnrollCourse((Request) request);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> unenrollCourse(Http.Request httpRequest) {
    return handleRequest(
        courseEnrollmentActorRef,
        ActorOperations.UNENROLL_COURSE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new CourseEnrollmentRequestValidator().validateUnenrollCourse((Request) request);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }
}
