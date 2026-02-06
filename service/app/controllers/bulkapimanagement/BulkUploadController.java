package controllers.bulkapimanagement;

import org.apache.pekko.actor.ActorRef;
import org.sunbird.operations.lms.ActorOperations;
import org.sunbird.keys.JsonKey;
import org.sunbird.validators.BaseRequestValidator;
import org.sunbird.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BulkUploadController extends BaseBulkUploadController {

  BaseRequestValidator baseRequestValidator = new BaseRequestValidator();

  @Inject
  @Named("bulk-upload-management-actor")
  private ActorRef bulkUploadActorRef;

  public CompletionStage<Result> batchEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(),
              JsonKey.BATCH_LEARNER_ENROL,
              false,
              httpRequest);
      return actorResponseHandler(bulkUploadActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> batchUnEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(),
              JsonKey.BATCH_LEARNER_UNENROL,
              false,
              httpRequest);
      return actorResponseHandler(bulkUploadActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> getUploadStatus(String processId, Http.Request httpRequest) {
    return handleRequest(
        bulkUploadActorRef,
        ActorOperations.GET_BULK_OP_STATUS.getValue(),
        null,
        null,
        processId,
        JsonKey.PROCESS_ID,
        null,
        false,
        httpRequest);
  }

  public CompletionStage<Result> getStatusDownloadLink(String processId, Http.Request httpRequest) {
    return handleRequest(
        bulkUploadActorRef,
        ActorOperations.GET_BULK_UPLOAD_STATUS_DOWNLOAD_LINK.getValue(),
        null,
        null,
        processId,
        JsonKey.PROCESS_ID,
        null,
        false,
        httpRequest);
  }
}
