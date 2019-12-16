package controllers.badging;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.badging.validator.BadgeAssociationValidator;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.BadgingActorOperations;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

public class BadgeAssociationController extends BaseController {

  @Inject
  @Named("badge-association-actor")
  private ActorRef badgeAssociationActorRef;

  public CompletionStage<Result> createAssociation(Http.Request httpRequest) {
    return handleRequest(
        badgeAssociationActorRef,
        BadgingActorOperations.CREATE_BADGE_ASSOCIATION.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new BadgeAssociationValidator().validateCreateBadgeAssociationRequest((Request) request);
          return null;
        },
        null,
        null,
        null,
        true,
        httpRequest);
  }

  public CompletionStage<Result> removeAssociation(Http.Request httpRequest) {
    return handleRequest(
        badgeAssociationActorRef,
        BadgingActorOperations.REMOVE_BADGE_ASSOCIATION.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new BadgeAssociationValidator().validateRemoveBadgeAssociationRequest((Request) request);
          return null;
        },
        null,
        null,
        null,
        true,
        httpRequest);
  }

  public CompletionStage<Result> searchAssociation(Http.Request httpRequest) {
    return handleSearchRequest(
        badgeAssociationActorRef,
        ActorOperations.COMPOSITE_SEARCH.getValue(),
        httpRequest.body().asJson(),
        request -> {
          new BaseRequestValidator().validateSearchRequest((Request) request);
          return null;
        },
        null,
        null,
        getAllRequestHeaders(httpRequest),
        EsType.badgeassociations.getTypeName(),
        httpRequest);
  }
}
