package controllers.certificate;

import controllers.BaseController;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actor.operations.CourseActorOperations;
import play.libs.F.Promise;
import play.mvc.Result;

public class CertificateController extends BaseController {

  public static final String REISSUE = "reIssue";

  public Promise<Result> issueCertificate() {
    return handleRequest(
        CourseActorOperations.ISSUE_CERTIFICATE.getValue(),
        request().body().asJson(),
        (request) -> {
          Request req = (Request) request;
          new CertificateRequestValidator().validateIssueCertificateRequest(req);
          req.getContext().put(REISSUE, request().queryString().get(REISSUE));
          return null;
        },
        getAllRequestHeaders(request()));
  }
}
