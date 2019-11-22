package controllers.certificate;

import akka.actor.ActorRef;
import controllers.BaseController;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actor.operations.CourseActorOperations;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class CertificateController extends BaseController {

  public static final String REISSUE = "reIssue";
    private ActorRef courseBatchCertificateActorRef;
    private ActorRef certificateActorRef;

    @Inject
    public CertificateController(@Named("course-batch-certificate-actor") ActorRef courseBatchCertificateActorRef,
                                 @Named("certificate-actor") ActorRef certificateActorRef) {
        this.courseBatchCertificateActorRef=courseBatchCertificateActorRef;
        this.certificateActorRef = certificateActorRef;
    }

  public CompletionStage<Result> issueCertificate(Http.Request httpRequest) {
    return handleRequest(
            certificateActorRef,
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
            courseBatchCertificateActorRef,
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
            courseBatchCertificateActorRef,
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
