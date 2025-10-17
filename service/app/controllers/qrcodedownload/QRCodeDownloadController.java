package controllers.qrcodedownload;

import org.apache.pekko.actor.ActorRef;
import controllers.BaseController;
import controllers.qrcodedownload.validator.QRCodeDownloadRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.request.Request;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletionStage;

public class QRCodeDownloadController extends BaseController {

  @Inject
  @Named("qrcode-download-management-actor")
  private ActorRef qrcodeDownloadActorRef;

  public CompletionStage<Result> downloadQRCodes(Http.Request httpRequest) {
   logger.debug(null,
        "Download QR Code method is called = " + httpRequest.body().asJson());
    return handleRequest(
        qrcodeDownloadActorRef,
        ActorOperations.DOWNLOAD_QR_CODES.getValue(),
        httpRequest.body().asJson(),
        (request) -> {
          QRCodeDownloadRequestValidator.validateRequest((Request) request);
          return null;
        },
        httpRequest);
  }
}
