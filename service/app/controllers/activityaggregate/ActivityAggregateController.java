package controllers.activityaggregate;

import controllers.BaseController;
import controllers.activityaggregate.validator.ActivityAggregateRequestValidator;
import org.apache.pekko.actor.ActorRef;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ActivityAggregateController extends BaseController {

    private final ActorRef activityAggregatorActor;
    private final ActivityAggregateRequestValidator validator = new ActivityAggregateRequestValidator();

    @Inject
    public ActivityAggregateController(
            @Named("activity-aggregator-actor") ActorRef activityAggregatorActor) {
        this.activityAggregatorActor = activityAggregatorActor;
    }

    public CompletionStage<Result> updateActivityAggregates(Http.Request httpRequest) {
        try {
            Request request = createAndInitRequest(
                    ActorOperations.UPDATE_ACTIVITY_AGGREGATES.getValue(),
                    httpRequest.body().asJson(),
                    httpRequest);
            
            validator.validateUpdateActivityAggregates(request);
            
            return actorResponseHandler(
                    activityAggregatorActor,
                    request,
                    timeout,
                    null,
                    httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }
}
