package controllers.bulkapimanagement;

import akka.actor.ActorRef;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;

public class BulkUploadController extends BaseBulkUploadController {

  BaseRequestValidator baseRequestValidator = new BaseRequestValidator();

  @Inject
  @Named("bulk-upload-management-actor")
  private ActorRef bulkUploadActorRef;

  public CompletionStage<Result> batchEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(), JsonKey.BATCH_LEARNER_ENROL, false, httpRequest);
      return actorResponseHandler(bulkUploadActorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> batchUnEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(), JsonKey.BATCH_LEARNER_UNENROL, false, httpRequest);
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
