package controllers.qrcodedownload;

import akka.actor.ActorRef;
import controllers.BaseController;
import controllers.qrcodedownload.validator.QRCodeDownloadRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletionStage;

import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Named;

public class QRCodeDownloadController extends BaseController {
    private ActorRef qrcodeDownloadActorRef;


    @Inject
    public QRCodeDownloadController(@Named("qrcode-download-management-actor") ActorRef qrcodeDownloadActorRef) {
        this.qrcodeDownloadActorRef = qrcodeDownloadActorRef;
    }
    public CompletionStage<Result> downloadQRCodes(Http.Request httpRequest) {
        ProjectLogger.log("Download QR Code method is called = " + httpRequest.body().asJson(), LoggerEnum.DEBUG.name());
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
