package controllers.bulkapimanagement;

import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.BaseRequestValidator;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

import play.mvc.Http;
import play.mvc.Result;

public class BulkUploadController extends BaseBulkUploadController {

  BaseRequestValidator baseRequestValidator = new BaseRequestValidator();

  public CompletionStage<Result> batchEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(), JsonKey.BATCH_LEARNER_ENROL, false, httpRequest);
      return actorResponseHandler(getActorRef(), request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> batchUnEnrollmentBulkUpload(Http.Request httpRequest) {
    try {
      Request request =
          createAndInitBulkRequest(
              ActorOperations.BULK_UPLOAD.getValue(), JsonKey.BATCH_LEARNER_UNENROL, false, httpRequest);
      return actorResponseHandler(getActorRef(), request, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> getUploadStatus(String processId, Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.GET_BULK_OP_STATUS.getValue(),
        null,
        null,
        processId,
        JsonKey.PROCESS_ID,
        false,
            httpRequest);
  }

  public CompletionStage<Result> getStatusDownloadLink(String processId, Http.Request httpRequest) {
    return handleRequest(
        ActorOperations.GET_BULK_UPLOAD_STATUS_DOWNLOAD_LINK.getValue(),
        null,
        null,
        processId,
        JsonKey.PROCESS_ID,
        false,
            httpRequest);
  }
}
