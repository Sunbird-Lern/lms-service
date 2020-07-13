package controllers.group;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.group.validator.GroupActivityValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class GroupAggController extends BaseController {

    @Inject
    @Named("group-aggregates-actor")
    private ActorRef groupAggregatesActorRef;

    public CompletionStage<Result> getGroupActivityAggregates(Http.Request httpRequest) {

        ProjectLogger.log(
                "Aggregate Group Activity method is called = " + httpRequest.body().asJson(),
                LoggerEnum.DEBUG.name());
        return handleRequest(
                groupAggregatesActorRef,
                ActorOperations.GROUP_ACTIVITY_AGGREGATES.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    GroupActivityValidator.validateRequest((Request) request);
                    return null;
                },
                getAllRequestHeaders(httpRequest),
                httpRequest);
    }
}
