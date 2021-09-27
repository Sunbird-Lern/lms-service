package modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseController;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.auth.verifier.AccessTokenValidator;
import org.sunbird.cache.platform.Platform;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.JsonUtil;
import play.http.ActionCreator;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import util.Attrs;
import util.RequestInterceptor;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class OnRequestHandler implements ActionCreator {
    
    private ObjectMapper mapper = new ObjectMapper();
  public static boolean isServiceHealthy = true;
  private final List<String> USER_UNAUTH_STATES =
      Arrays.asList(JsonKey.UNAUTHORIZED, JsonKey.ANONYMOUS);
  public LoggerUtil logger = new LoggerUtil(this.getClass());
  private static final List<String> clientAppHeaderKeys = Platform.getStringList("request_headers_logging", Arrays.asList("x-app-id", "x-device-id", "x-channel-id"));


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
    return new Action.Simple() {
      @Override
      public CompletionStage<Result> call(Http.Request request) {
        CompletionStage<Result> result = checkForServiceHealth(request);
        if (result != null) return result;
        // Setting Actual userId (requestedBy) and managed userId (requestedFor) placeholders in flash memory to null before processing.
        // Unauthorized, Anonymous, UserID
        String message = RequestInterceptor.verifyRequestData(request);
        Optional<String> forAuth = request.header(HeaderParam.X_Authenticated_For.getName());
        String childId = null;
        String loggingHeaders = getLoggingHeaders(request);
        request = request.addAttr(Attrs.X_LOGGING_HEADERS, loggingHeaders);
        if (StringUtils.isNotBlank(message) && forAuth.isPresent() && StringUtils.isNotBlank(forAuth.orElse(""))) {
            String requestedForId = getRequestedForId(request);
          childId = AccessTokenValidator.verifyManagedUserToken(forAuth.get(), message, requestedForId, loggingHeaders);
          if (StringUtils.isNotBlank(childId) && !USER_UNAUTH_STATES.contains(childId)) {
              request = request.addAttr(Attrs.REQUESTED_FOR, childId);
          }
          
        }
        // call method to set all the required params for the telemetry event(log)...
        request = intializeRequestInfo(request, message, messageId);
        request = request.addAttr(Attrs.X_AUTH_TOKEN, request.header(HeaderParam.X_Authenticated_User_Token.getName()).orElse(""));
        if ((!USER_UNAUTH_STATES.contains(message)) && (childId==null || !USER_UNAUTH_STATES.contains(childId))) {
            request = request.addAttr(Attrs.USER_ID, message);
            request = request.addAttr(Attrs.IS_AUTH_REQ, "false");
          for (String uri : RequestInterceptor.restrictedUriList) {
            if (request.path().contains(uri)) {
                request = request.addAttr(Attrs.IS_AUTH_REQ, "true");
              break;
            }
          }
          result = delegate.call(request);
        } else if (JsonKey.UNAUTHORIZED.equals(message) || (childId != null && JsonKey.UNAUTHORIZED.equals(childId))) {
          String errorCode = JsonKey.UNAUTHORIZED.equals(message) ? message : childId;
          result = onDataValidationError(request, errorCode, ResponseCode.UNAUTHORIZED.getResponseCode());
        } else {
          result = delegate.call(request);
        }
        return result.thenApply(res -> res.withHeader("Access-Control-Allow-Origin", "*"));
      }
    };
  }

    private String getRequestedForId(Http.Request request) {
      String requestedForUserID = null;
      JsonNode jsonBody = request.body().asJson();
      if(null != jsonBody && jsonBody.has(JsonKey.REQUEST) && jsonBody.get(JsonKey.REQUEST).has(JsonKey.USER_ID)) {
          requestedForUserID = jsonBody.get(JsonKey.REQUEST).get(JsonKey.USER_ID).asText();
      } else { // for read-api
          String uuidSegment = null;
          Path path = Paths.get(request.uri());
          if (request.queryString().isEmpty()) {
              uuidSegment = path.getFileName().toString();
          } else {
              String[] queryPath = path.getFileName().toString().split("\\?");
              uuidSegment = queryPath[0];
          }
          try {
              requestedForUserID = UUID.fromString(uuidSegment).toString();
          } catch (IllegalArgumentException iae) {
             logger.info(null, "Perhaps this is another API, like search that doesn't carry user id.");
          }
      }
      return requestedForUserID;
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
    logger.info(null, "Data error found--" + errorMessage);
    ResponseCode code = ResponseCode.getResponse(errorMessage);
    ResponseCode headerCode = ResponseCode.CLIENT_ERROR;
    Response resp = BaseController.createFailureResponse(request, code, headerCode);
    return CompletableFuture.completedFuture(Results.status(responseCode, Json.toJson(resp)));
  }

  private Http.Request intializeRequestInfo(Http.Request request, String userId, String requestId) { 
      try {
          String actionMethod = request.method();
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
          Map<String, Object> reqContext = new WeakHashMap<>();
          request = request.addAttr(Attrs.SIGNUP_TYPE, signType);
          reqContext.put(JsonKey.SIGNUP_TYPE, signType);
          request = request.addAttr(Attrs.REQUEST_SOURCE, source);
          reqContext.put(JsonKey.REQUEST_SOURCE, source);

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
          request = request.addAttr(Attrs.CHANNEL, channel);
          reqContext.put(JsonKey.ENV, getEnv(request));
          reqContext.put(JsonKey.REQUEST_ID, requestId);
          Optional<String> optionalAppId = request.header(HeaderParam.X_APP_ID.getName());
          // check if in request header X-app-id is coming then that need to
          // be pass in search telemetry.
          if (optionalAppId.isPresent()) {
              request = request.addAttr(Attrs.APP_ID, optionalAppId.get());
              reqContext.put(JsonKey.APP_ID, optionalAppId.get());
          }
          // checking device id in headers
          Optional<String> optionalDeviceId = request.header(HeaderParam.X_Device_ID.getName());
          if (optionalDeviceId.isPresent()) {
              request = request.addAttr(Attrs.DEVICE_ID, optionalDeviceId.get());
              reqContext.put(JsonKey.DEVICE_ID, optionalDeviceId.get());
          }
          if (!USER_UNAUTH_STATES.contains(userId)) {
              reqContext.put(JsonKey.ACTOR_ID, userId);
              reqContext.put(JsonKey.ACTOR_TYPE, StringUtils.capitalize(JsonKey.USER));
              request = request.addAttr(Attrs.ACTOR_ID, userId);
              request = request.addAttr(Attrs.ACTOR_TYPE, JsonKey.USER);
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
              request = request.addAttr(Attrs.ACTOR_ID, consumerId);
              request = request.addAttr(Attrs.ACTOR_TYPE, JsonKey.CONSUMER);
          }
          Map<String, Object> map = new WeakHashMap<>();
          map.put(JsonKey.CONTEXT, reqContext);
          Map<String, Object> additionalInfo = new WeakHashMap<>();
          additionalInfo.put(JsonKey.URL, url);
          additionalInfo.put(JsonKey.METHOD, methodName);
          additionalInfo.put(JsonKey.START_TIME, startTime);

          // additional info contains info other than context info ...
          map.put(JsonKey.ADDITIONAL_INFO, additionalInfo);
          if (StringUtils.isBlank(requestId)) {
              requestId = JsonKey.DEFAULT_CONSUMER_ID;
          }
          request = request.addAttr(Attrs.REQUEST_ID, requestId);
          request = request.addAttr(Attrs.CONTEXT, mapper.writeValueAsString(map));
      } catch (Exception e) {
          ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, e.getMessage());
      }
      return request;
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

  // TODO: same method created in BaseController also. We should move it to a common place.
    protected String getLoggingHeaders(Http.Request httpRequest) {
        try {
            Map<String, List<String>> headers = httpRequest.getHeaders().toMap();
            Map<String, List<String>>  filteredHeaders = headers.entrySet().stream().filter(e -> clientAppHeaderKeys.contains(e.getKey().toLowerCase())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return JsonUtil.serialize(filteredHeaders);
        } catch (Exception e) {
            return "Exception in serializing headers= " + e.getMessage();
        }
    }
}
