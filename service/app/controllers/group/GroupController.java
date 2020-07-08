package controllers.group;

import akka.actor.ActorRef;
import controllers.BaseController;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class GroupController extends BaseController {

    @Inject
    @Named("group-management-actor")
    private ActorRef groupManagementActorRef;

    public CompletionStage<Result> getAggregateGroupActivity(Http.Request httpRequest) {
        ProjectLogger.log(
                "Aggregate Group Activity method is called = " + httpRequest.body().asJson(),
                LoggerEnum.DEBUG.name());
        return handleRequest(
                groupManagementActorRef,
                "aggregateGroupActivity",
                httpRequest.body().asJson(),
                (request) -> {
                    return null;
                },
                httpRequest);
    }
}
