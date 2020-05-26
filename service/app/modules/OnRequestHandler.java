package modules;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.telemetry.util.TelemetryUtil;
import play.http.ActionCreator;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import util.RequestInterceptor;

public class OnRequestHandler implements ActionCreator {

  public static boolean isServiceHealthy = true;
  private final List<String> USER_UNAUTH_STATES =
      Arrays.asList(JsonKey.UNAUTHORIZED, JsonKey.ANONYMOUS);
  public static Map<String, Map<String, Object>> requestInfo = new ConcurrentHashMap<>();

  @Override
  public Action createAction(Http.Request request, Method actionMethod) {
    Optional<String> optionalMessageId = request.header(JsonKey.MESSAGE_ID);
    String messageId;
    if (optionalMessageId.isPresent()) {
      messageId = optionalMessageId.get();
    } else {
      UUID uuid = UUID.randomUUID();
      messageId = uuid.toString();
    }
    ExecutionContext.setRequestId(messageId);
    return new Action.Simple() {
      @Override
      public CompletionStage<Result> call(Http.Request request) {
        request.getHeaders();
        CompletionStage<Result> result = checkForServiceHealth(request);
        if (result != null) return result;

        // Unauthorized, Anonymous, UserID
        String message = RequestInterceptor.verifyRequestData(request);
        // call method to set all the required params for the telemetry event(log)...
        intializeRequestInfo(request, message);
        if (!USER_UNAUTH_STATES.contains(message)) {
          request.flash().put(JsonKey.USER_ID, message);
          request.flash().put(JsonKey.IS_AUTH_REQ, "false");
          for (String uri : RequestInterceptor.restrictedUriList) {
            if (request.path().contains(uri)) {
              request.flash().put(JsonKey.IS_AUTH_REQ, "true");
              break;
            }
          }
          result = delegate.call(request);
        } else if (JsonKey.UNAUTHORIZED.equals(message)) {
          result =
              onDataValidationError(request, message, ResponseCode.UNAUTHORIZED.getResponseCode());
        } else {
          result = delegate.call(request);
        }
        Map<String,Object> reqInfo = requestInfo.remove(ExecutionContext.getRequestId());
        reqInfo = null;
        return result.thenApply(res -> res.withHeader("Access-Control-Allow-Origin", "*"));
      }
    };
  }

  /**
   * This method will do request data validation for GET method only. As a GET request user must
   * send some key in header.
   *
   * @param request Request
   * @param errorMessage String
   * @return Promise<Result>
   */
  public CompletionStage<Result> onDataValidationError(
      Http.Request request, String errorMessage, int responseCode) {
    ProjectLogger.log("Data error found--" + errorMessage);
    ResponseCode code = ResponseCode.getResponse(errorMessage);
    ResponseCode headerCode = ResponseCode.CLIENT_ERROR;
    Response resp = BaseController.createFailureResponse(request, code, headerCode);
    return CompletableFuture.completedFuture(Results.status(responseCode, Json.toJson(resp)));
  }

  private void intializeRequestInfo(Http.Request request, String userId) {

    String actionMethod = request.method();
    String messageId = ExecutionContext.getRequestId(); // request.getHeader(JsonKey.MESSAGE_ID);
    String url = request.uri();
    String methodName = actionMethod;
    long startTime = System.currentTimeMillis();
    String signType = "";
    String source = "";
    if (request.body() != null && request.body().asJson() != null) {
      JsonNode requestNode =
          request.body().asJson().get("params"); // extracting signup type from request
      if (requestNode != null && requestNode.get(JsonKey.SIGNUP_TYPE) != null) {
        signType = requestNode.get(JsonKey.SIGNUP_TYPE).asText();
      }
      if (requestNode != null && requestNode.get(JsonKey.REQUEST_SOURCE) != null) {
        source = requestNode.get(JsonKey.REQUEST_SOURCE).asText();
      }
    }
    request.flash().put(JsonKey.SIGNUP_TYPE, signType);
    request.flash().put(JsonKey.REQUEST_SOURCE, source);
    ExecutionContext context = ExecutionContext.getCurrent();
    Map<String, Object> reqContext = new WeakHashMap<>();
    // set env and channel to the
    Optional<String> optionalChannel = request.header(HeaderParam.CHANNEL_ID.getName());
    String channel;
    if (optionalChannel.isPresent()) {
      channel = optionalChannel.get();
    } else {
      String sunbirdDefaultChannel = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DEFAULT_CHANNEL);
      channel =
          (StringUtils.isNotEmpty(sunbirdDefaultChannel))
              ? sunbirdDefaultChannel
              : JsonKey.DEFAULT_ROOT_ORG_ID;
    }
    reqContext.put(JsonKey.CHANNEL, channel);
    request.flash().put(JsonKey.CHANNEL, channel);
    reqContext.put(JsonKey.ENV, getEnv(request));
    reqContext.put(JsonKey.REQUEST_ID, ExecutionContext.getRequestId());
    Optional<String> optionalAppId = request.header(HeaderParam.X_APP_ID.getName());
    // check if in request header X-app-id is coming then that need to
    // be pass in search telemetry.
    if (optionalAppId.isPresent()) {
      request.flash().put(JsonKey.APP_ID, optionalAppId.get());
      reqContext.put(JsonKey.APP_ID, optionalAppId.get());
    }
    // checking device id in headers
    Optional<String> optionalDeviceId = request.header(HeaderParam.X_Device_ID.getName());
    if (optionalDeviceId.isPresent()) {
      request.flash().put(JsonKey.DEVICE_ID, optionalDeviceId.get());
      reqContext.put(JsonKey.DEVICE_ID, optionalDeviceId.get());
    }
    if (!USER_UNAUTH_STATES.contains(userId)) {
      reqContext.put(JsonKey.ACTOR_ID, userId);
      reqContext.put(JsonKey.ACTOR_TYPE, StringUtils.capitalize(JsonKey.USER));
      request.flash().put(JsonKey.ACTOR_ID, userId);
      request.flash().put(JsonKey.ACTOR_TYPE, JsonKey.USER);
    } else {
      Optional<String> optionalConsumerId = request.header(HeaderParam.X_Consumer_ID.getName());
      String consumerId;
      if (optionalConsumerId.isPresent()) {
        consumerId = optionalConsumerId.get();
      } else {
        consumerId = JsonKey.DEFAULT_CONSUMER_ID;
      }
      reqContext.put(JsonKey.ACTOR_ID, consumerId);
      reqContext.put(JsonKey.ACTOR_TYPE, StringUtils.capitalize(JsonKey.CONSUMER));
      request.flash().put(JsonKey.ACTOR_ID, consumerId);
      request.flash().put(JsonKey.ACTOR_TYPE, JsonKey.CONSUMER);
    }
    context.setRequestContext(reqContext);
    Map<String, Object> map = new WeakHashMap<>();
    map.put(JsonKey.CONTEXT, TelemetryUtil.getTelemetryContext());
    Map<String, Object> additionalInfo = new WeakHashMap<>();
    additionalInfo.put(JsonKey.URL, url);
    additionalInfo.put(JsonKey.METHOD, methodName);
    additionalInfo.put(JsonKey.START_TIME, startTime);

    // additional info contains info other than context info ...
    map.put(JsonKey.ADDITIONAL_INFO, additionalInfo);
    if (StringUtils.isBlank(messageId)) {
      messageId = JsonKey.DEFAULT_CONSUMER_ID;
    }
    request.flash().put(JsonKey.REQUEST_ID, messageId);
    if (requestInfo == null) {
      requestInfo = new ConcurrentHashMap<>();
    }
    requestInfo.put(messageId, map);
    ProjectLogger.log(
        "OnRequestHandler:intializeRequestInfo added details for messageId=" + messageId,
        LoggerEnum.INFO);
  }

  private String getEnv(Http.Request request) {

    String uri = request.uri();
    String env;
    if (uri.startsWith("/v1/page")) {
      env = JsonKey.PAGE;
    } else if (uri.startsWith("/v1/course/batch")) {
      env = JsonKey.BATCH;
    } else if (uri.startsWith("/v1/dashboard")) {
      env = JsonKey.DASHBOARD;
    } else if (uri.startsWith("/v1/content")) {
      env = JsonKey.BATCH;
    } else {
      env = "miscellaneous";
    }
    return env;
  }

  public CompletionStage<Result> checkForServiceHealth(Http.Request request) {
    if (Boolean.parseBoolean((ProjectUtil.getConfigValue(JsonKey.SUNBIRD_HEALTH_CHECK_ENABLE)))
        && !request.path().endsWith(JsonKey.HEALTH)) {
      if (!isServiceHealthy) {
        ResponseCode headerCode = ResponseCode.SERVICE_UNAVAILABLE;
        Response resp = BaseController.createFailureResponse(request, headerCode, headerCode);
        return CompletableFuture.completedFuture(
            Results.status(ResponseCode.SERVICE_UNAVAILABLE.getResponseCode(), Json.toJson(resp)));
      }
    }
    return null;
  }
}
