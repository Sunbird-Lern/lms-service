package controllers.courserecommendation;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.courseenrollment.validator.CourseEnrollmentRequestValidator;
import controllers.coursemanagement.validator.CourseBatchRequestValidator;
import controllers.coursemanagement.validator.CourseCreateRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;


import java.util.*;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;

public class CourseRecommendationController extends BaseController {


    @Inject
    @Named("course-batch-management-actor")
    private ActorRef courseBatchActorRef;

    @Inject
    @Named("course-enrolment-actor")
    private ActorRef courseEnrolmentActor;

    @Inject
    @Named("course-recommendation-actor")
    private ActorRef courseRecommendationActor;

    private CourseEnrollmentRequestValidator validator = new CourseEnrollmentRequestValidator();

    public CompletionStage<Result> getRecommendedCourses(String uid, Http.Request httpRequest) {
        System.out.println("Inside getRecommendedCourses2");

        return handleRequest(courseRecommendationActor, ActorOperations.RECOMMEND_COURSE.getValue(),
                httpRequest.body().asJson(),
                (req) -> {
                    Request request = (Request) req;
                    String userId = (String) request.getContext().getOrDefault(JsonKey.REQUESTED_FOR, request.getContext().get(JsonKey.REQUESTED_BY));
                    validator.validateRequestedBy(userId);
                    return null;
                },
                null,
                null,
                getAllRequestHeaders((httpRequest)),
                false,
                httpRequest);
    }

       /* return handleRequest(
                courseActorRef,
                ActorOperations.CREATE_COURSE.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    CourseCreateRequestValidator.validateRequest((Request) request);
                    return null;
                },
                httpRequest);*/


}
