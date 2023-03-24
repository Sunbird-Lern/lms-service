package controllers.certificate;

import akka.actor.ActorRef;
import controllers.BaseController;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Named;

import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actor.operations.CourseActorOperations;
import play.mvc.Http;
import play.mvc.Result;

public class CertificateController extends BaseController {

  public static final String REISSUE = "reIssue";

  @Inject
  @Named("course-batch-certificate-actor")
  private ActorRef courseBatchCertificateActorRef;

  @Inject
  @Named("certificate-actor")
  private ActorRef certificateActorRef;

  public CompletionStage<Result> issueCertificate(Http.Request httpRequest) {
    return handleRequest(
        certificateActorRef,
        CourseActorOperations.ISSUE_CERTIFICATE.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          Request req = (Request) request;
          String courseId = req.getRequest().containsKey(JsonKey.COURSE_ID) ? JsonKey.COURSE_ID : JsonKey.COLLECTION_ID;
          req.getRequest().put(JsonKey.COURSE_ID, req.getRequest().get(courseId));
          new CertificateRequestValidator().validateIssueCertificateRequest(req);
          req.getContext().put(REISSUE, httpRequest.queryString().get(REISSUE));
          return null;
        },
        getAllRequestHeaders(httpRequest),
        httpRequest);
  }

    public CompletionStage<Result> issueCertificateForPIAA(Http.Request httpRequest) {
        return handleRequest(
                certificateActorRef,
                CourseActorOperations.ISSUE_PIAA_CERTIFICATE.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    Request req = (Request) request;
                    String courseId = req.getRequest().containsKey(JsonKey.COURSE_ID) ? JsonKey.COURSE_ID : JsonKey.COLLECTION_ID;
                    req.getRequest().put(JsonKey.COURSE_ID, req.getRequest().get(courseId));
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
