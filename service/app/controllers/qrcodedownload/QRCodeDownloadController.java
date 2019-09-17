package controllers.qrcodedownload;

import controllers.BaseController;
import controllers.qrcodedownload.validator.QRCodeDownloadRequestValidator;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import java.util.concurrent.CompletionStage;

import play.mvc.Http;
import play.mvc.Result;

public class QRCodeDownloadController extends BaseController {
    public CompletionStage<Result> downloadQRCodes(Http.Request httpRequest) {
        ProjectLogger.log("Download QR Code method is called = " + httpRequest.body().asJson(), LoggerEnum.DEBUG.name());
        return handleRequest(
                ActorOperations.DOWNLOAD_QR_CODES.getValue(),
                httpRequest.body().asJson(),
                (request) -> {
                    QRCodeDownloadRequestValidator.validateRequest((Request) request);
                    return null;
                },
                httpRequest);
    }
}
