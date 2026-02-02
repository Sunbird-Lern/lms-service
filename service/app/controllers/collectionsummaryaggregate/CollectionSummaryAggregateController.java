package controllers.collectionsummaryaggregate;

import org.apache.pekko.actor.ActorRef;
import controllers.BaseController;
import controllers.collectionsummaryaggregate.validator.Validator;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class CollectionSummaryAggregateController extends BaseController {

    @Inject
    @Named("collection-summary-aggregate-actor")
    private ActorRef CollectionSummaryAggregateActor;

    public CompletionStage<Result> getCollectionSummaryAggregate(Http.Request httpRequest) {
        return handleRequest(
                CollectionSummaryAggregateActor,
                ActorOperations.GET_USER_COURSE.getValue(),
                httpRequest.body().asJson(),
                (req) -> {
                    Request request = (Request) req;
                    System.out.println("Validation Request Obj" + request);
                    new Validator().validate(request);
                    return null;
                },
                getAllRequestHeaders((httpRequest)),
                httpRequest);
    }

}
