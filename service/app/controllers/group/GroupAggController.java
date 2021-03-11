package controllers.group;

import akka.actor.ActorRef;
import controllers.BaseController;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
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

        logger.debug(null, "Aggregate Group Activity method is called = " + httpRequest.body().asJson());
        return handleRequest(
                groupAggregatesActorRef,
                ActorOperations.GROUP_ACTIVITY_AGGREGATES.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    RequestValidator.validateGroupActivityAggregatesRequest((Request) request);
                    return null;
                },
                getAllRequestHeaders(httpRequest),
                httpRequest);
    }
}
