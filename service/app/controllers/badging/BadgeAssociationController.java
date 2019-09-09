package controllers.badging;

import controllers.BaseController;
import controllers.badging.validator.BadgeAssociationValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.BadgingActorOperations;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletionStage;

import play.mvc.Http;
import play.mvc.Result;

public class BadgeAssociationController extends BaseController {

  public CompletionStage<Result> createAssociation(Http.Request httpRequest) {
    return handleRequest(
        BadgingActorOperations.CREATE_BADGE_ASSOCIATION.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new BadgeAssociationValidator().validateCreateBadgeAssociationRequest((Request) request);
          return null;
        },
        null,
        null,
        true,
            httpRequest);
  }

  public CompletionStage<Result> removeAssociation(Http.Request httpRequest) {
    return handleRequest(
        BadgingActorOperations.REMOVE_BADGE_ASSOCIATION.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          new BadgeAssociationValidator().validateRemoveBadgeAssociationRequest((Request) request);
          return null;
        },
        null,
        null,
        true,
            httpRequest);
  }

  public CompletionStage<Result> searchAssociation(Http.Request httpRequest) {
    return handleSearchRequest(
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
