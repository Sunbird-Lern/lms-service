package org.sunbird.learner.actors.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.Common;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.actors.coursebatch.service.UserCoursesService;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public class EventSetManagementActor extends BaseActor {
    private final UserCoursesService userCoursesService = new UserCoursesService();

    @Override
    public void onReceive(Request request) throws Throwable {
        String requestedOperation = request.getOperation();
        switch (requestedOperation) {
            case "updateEventSet":
                updateEventSet(request);
                break;
            case "discardEventSet":
                discardEventSet(request);
                break;
            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void updateEventSet(Request request) throws Exception {
        validateNoEventEnrollments(request);
        try {
            Map<String, Object> contentMap = new HashMap<>((Map<String, Object>) request.get(SunbirdKey.EVENT_SET));
            String pathId = JsonKey.IDENTIFIER;
            String pathVal = request.getRequest().getOrDefault(JsonKey.IDENTIFIER, "").toString();
            Response response = EventContentUtil.postContent(request, SunbirdKey.EVENT_SET, "/private/eventset/v4/update/{identifier}", contentMap, pathId, pathVal);
            if (null != response) {
                if (response.getResponseCode().getResponseCode() == ResponseCode.OK.getResponseCode()) {
                    sender().tell(response, self());
                } else {
                    String message = formErrorDetailsMessage(response, "EventSet updation failed ");
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.customServerError,
                            MessageFormat.format(
                                    ResponseCode.customServerError.getErrorMessage(), message));
                }
            } else {
                ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR);
            }
        } catch (Exception ex) {
            logger.error(request.getRequestContext(), "EventSetManagementActor:updateEventSet : update error ", ex);
            if (ex instanceof ProjectCommonException) {
                throw ex;
            } else {
                throw new ProjectCommonException(
                        ResponseCode.SERVER_ERROR.getErrorCode(),
                        ResponseCode.SERVER_ERROR.getErrorMessage(),
                        ResponseCode.SERVER_ERROR.getResponseCode());
            }
        }
    }

    private String formErrorDetailsMessage(Response response, String message) {
        Map<String, Object> resultMap =
                Optional.ofNullable(response.getResult()).orElse(new HashMap<>());
        if (MapUtils.isNotEmpty(resultMap)) {
            Object obj = Optional.ofNullable(resultMap.get(SunbirdKey.TB_MESSAGES)).orElse("");
            if (obj instanceof List) {
                message += ((List<String>) obj).stream().collect(Collectors.joining(";"));
            } else if (StringUtils.isNotEmpty(response.getParams().getErrmsg())) {
                message += response.getParams().getErrmsg();
            } else {
                message += String.valueOf(obj);
            }
        }
        return message;
    }

    private void discardEventSet(Request request) throws Exception {
        validateNoEventEnrollments(request);
        try {
            String pathId = JsonKey.IDENTIFIER;
            String pathVal = request.getRequest().getOrDefault(JsonKey.IDENTIFIER, "").toString();
            Response response = EventContentUtil.deleteContent(request, "/private/eventset/v4/discard/{identifier}", pathId, pathVal);
            if (null != response) {
                if (response.getResponseCode().getResponseCode() == ResponseCode.OK.getResponseCode()) {
                    sender().tell(response, self());
                } else {
                    String message = formErrorDetailsMessage(response, "EventSet discard failed ");
                    ProjectCommonException.throwClientErrorException(
                            ResponseCode.customServerError,
                            MessageFormat.format(
                                    ResponseCode.customServerError.getErrorMessage(), message));
                }
            } else {
                ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR);
            }
        } catch (Exception ex) {
            logger.error(request.getRequestContext(), "EventSetManagementActor:discardEventSet : discard error ", ex);
            if (ex instanceof ProjectCommonException) {
                throw ex;
            } else {
                throw new ProjectCommonException(
                        ResponseCode.SERVER_ERROR.getErrorCode(),
                        ResponseCode.SERVER_ERROR.getErrorMessage(),
                        ResponseCode.SERVER_ERROR.getResponseCode());
            }
        }
    }

    private void validateNoEventEnrollments(Request request) {
        String identifier = request.get(SunbirdKey.IDENTIFIER).toString();
        String fixedBatchId = request.get(JsonKey.FIXED_BATCH_ID).toString();
        List<String> participants = new ArrayList<>();
        try {
            List<String> eventsIds = EventContentUtil.getChildEventIds(request, identifier);
            participants = eventsIds.stream().map(childId -> {
                String childBatchId = Common.formBatchIdForFixedBatchId(childId, fixedBatchId);
                return userCoursesService.getParticipantsList(childBatchId, true, request.getRequestContext());
            })
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } catch (UnirestException | JsonProcessingException e) {
            ProjectCommonException.throwServerErrorException(
                    ResponseCode.SERVER_ERROR,
                    e.getMessage());
        }
        if (!participants.isEmpty()) {
            ProjectCommonException.throwClientErrorException(
                    ResponseCode.cannotUpdateEventSetHavingEnrollments,
                    ResponseCode.cannotUpdateEventSetHavingEnrollments.getErrorMessage());
        }
    }

}