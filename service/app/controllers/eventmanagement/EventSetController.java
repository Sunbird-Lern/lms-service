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

public class EventSetController extends BaseController {

    @Inject
    @Named("eventset-management-actor")
    private ActorRef actorRef;

    public CompletionStage<Result> update(Http.Request httpRequest) {
        ProjectLogger.log(
                LoggerEnum.DEBUG.name());
        return handleRequest(
                actorRef,
                ActorOperations.UPDATE_EVENT_SET.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    EventRequestValidator.validateRequest((Request) request);
                    return null;
                },
                httpRequest);
    }

    public CompletionStage<Result> discard(String id, Http.Request httpRequest) {
        ProjectLogger.log(
                "Discard eventset method is called = " + httpRequest.body().asJson(),
                LoggerEnum.DEBUG.name());
        return handleRequest(
                actorRef,
                ActorOperations.DELETE_EVENT_SET.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    ((Request) request).getRequest().put("identifier", id);
                    EventRequestValidator.validateFixedBatchId((Request) request);
                    return null;
                },
                httpRequest);
    }

}