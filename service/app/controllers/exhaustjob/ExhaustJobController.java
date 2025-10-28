/** */
package controllers.exhaustjob;

import org.apache.pekko.actor.ActorRef;
import controllers.BaseController;
import controllers.exhaustjob.validator.ExhaustJobRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class ExhaustJobController extends BaseController {

  @Inject
  @Named("exhaust-job-actor")
  private ActorRef exhaustJobActorRef;

  public CompletionStage<Result> submitJobRequest(Http.Request httpRequest) {
    return handleRequest(
            exhaustJobActorRef,
        ActorOperations.SUBMIT_JOB_REQUEST.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          new ExhaustJobRequestValidator().validateCreateExhaustJobRequest(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> listJobRequest(String tag, Http.Request httpRequest) {
    return handleRequest(
            exhaustJobActorRef,
        ActorOperations.LIST_JOB_REQUEST.getValue(),
            tag,
        JsonKey.TAG,
        false,
        httpRequest);
  }
}
