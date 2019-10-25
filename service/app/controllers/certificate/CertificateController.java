package controllers.certificate;

import controllers.BaseController;
import java.util.concurrent.CompletionStage;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actor.operations.CourseActorOperations;
import play.mvc.Http;
import play.mvc.Result;

public class CertificateController extends BaseController {

  public static final String REISSUE = "reIssue";

  public CompletionStage<Result> issueCertificate(Http.Request httpRequest) {
    return handleRequest(
        CourseActorOperations.ISSUE_CERTIFICATE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          new CertificateRequestValidator().validateIssueCertificateRequest(req);
          req.getContext().put(REISSUE, httpRequest.queryString().get(REISSUE));
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> addCertificate(Http.Request httpRequest) {
    return handleRequest(
        CourseActorOperations.ADD_BATCH_CERTIFICATE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          new CertificateRequestValidator().validateAddCertificateRequest(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

  public CompletionStage<Result> deleteCertificate(Http.Request httpRequest) {
    return handleRequest(
        CourseActorOperations.DELETE_BATCH_CERTIFICATE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          new CertificateRequestValidator().validateDeleteCertificateRequest(req);
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }
}
