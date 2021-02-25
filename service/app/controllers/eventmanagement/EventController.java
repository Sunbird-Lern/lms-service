package controllers.eventmanagement;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.eventmanagement.validator.EventRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class EventController extends BaseController {

    @Inject
    @Named("event-management-actor")
    private ActorRef actorRef;

    public CompletionStage<Result> discard(String id, Http.Request httpRequest) {
        ProjectLogger.log(
                "Discard event method is called = " + httpRequest.body().asJson(),
                LoggerEnum.DEBUG.name());
        return handleRequest(
                actorRef,
                ActorOperations.DELETE_EVENT.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    ((Request) request).getRequest().put("identifier", id);
                    EventRequestValidator.validateFixedBatchId((Request) request);
                    return null;
                },
                httpRequest);
    }

}