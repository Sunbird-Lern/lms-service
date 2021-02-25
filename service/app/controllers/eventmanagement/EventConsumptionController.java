/**
 *
 */
package controllers.eventmanagement;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;
import org.sunbird.common.Common;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class EventConsumptionController extends BaseController {

    @Inject
    @Named("event-consumption-actor")
    private ActorRef actorRef;

    public CompletionStage<Result> getUserEventState(Http.Request httpRequest) {
        try {
            JsonNode requestJson = httpRequest.body().asJson();
            Request request =
                    createAndInitRequest("getConsumption", requestJson, httpRequest);
            request = transformUserId(request);
            Common.handleFixedBatchIdRequest(request);
            validateAndSetRequest(request);
            return actorResponseHandler(
                    actorRef, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    public CompletionStage<Result> updateUserEventState(Http.Request httpRequest) {
        try {
            JsonNode requestData = httpRequest.body().asJson();
            Request request = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
            request = transformUserId(request);
            Common.handleFixedBatchIdRequest(request);
            request.getRequest().remove(JsonKey.FIXED_BATCH_ID);
            request.getRequest().remove(JsonKey.ID);
            validateAndSetRequest(request);
            request.setOperation("updateConsumption");
            request.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
            request.setEnv(getEnvironment());
            return actorResponseHandler(actorRef, request, timeout, null, httpRequest);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
        }
    }

    private void validateAndSetRequest(Request request) {
        if (null == request.get(JsonKey.COURSE_ID) || request.get(JsonKey.COURSE_ID).toString().isEmpty()) {
            throw new ProjectCommonException(
                    ResponseCode.courseIdRequired.getErrorCode(),
                    ResponseCode.courseIdRequired.getErrorMessage(),
                    ResponseCode.CLIENT_ERROR.getResponseCode());
        }
        request.getRequest().put(JsonKey.CONTENT_ID, request.get(JsonKey.COURSE_ID));
        if (null == request.get(JsonKey.BATCH_ID) || request.get(JsonKey.BATCH_ID).toString().isEmpty()) {
            throw new ProjectCommonException(
                    ResponseCode.courseBatchIdRequired.getErrorCode(),
                    ResponseCode.courseBatchIdRequired.getErrorMessage(),
                    ResponseCode.CLIENT_ERROR.getResponseCode());
        }
        if (null == request.get(JsonKey.USER_ID) || request.get(JsonKey.USER_ID).toString().isEmpty()) {
            throw new ProjectCommonException(
                    ResponseCode.userIdRequired.getErrorCode(),
                    ResponseCode.userIdRequired.getErrorMessage(),
                    ResponseCode.CLIENT_ERROR.getResponseCode());
        }
    }

}