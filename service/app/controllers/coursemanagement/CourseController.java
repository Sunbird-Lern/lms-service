package controllers.coursemanagement;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.coursemanagement.validator.CourseCreateRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;

public class CourseController extends BaseController {

    @Inject
    @Named("course-management-actor")
    private ActorRef courseActorRef;

    public CompletionStage<Result> createCourse(Http.Request httpRequest) {
       logger.debug(null,
                "Create course method is called", null, new HashMap<>(){{put("requestData", httpRequest.body().asJson());}});
        return handleRequest(
                courseActorRef,
                ActorOperations.CREATE_COURSE.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    CourseCreateRequestValidator.validateRequest((Request) request);
                    return null;
                },
                httpRequest);
    }
}
