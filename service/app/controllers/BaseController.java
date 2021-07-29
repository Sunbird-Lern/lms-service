package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import modules.ApplicationStart;
import modules.OnRequestHandler;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryWriter;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Result;
import play.mvc.Results;
import util.Attrs;
import util.AuthenticationHelper;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This controller we can use for writing some common method.
 *
 * @author Manzarul
 */
public class BaseController extends Controller {
  
  private static ObjectMapper objectMapper = new ObjectMapper();
  private static final String version = "v1";
  public static final int AKKA_WAIT_TIME = 30;
  protected Timeout timeout = new Timeout(AKKA_WAIT_TIME, TimeUnit.SECONDS);
  private static final String debugEnabled = "false";
  public static final LoggerUtil logger = new LoggerUtil(BaseController.class);

  private org.sunbird.common.request.Request initRequest(
      org.sunbird.common.request.Request request, String operation, Http.Request httpRequest) {
    request.setOperation(operation);
    request.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
    request.setEnv(getEnvironment());
    request.setRequestContext(getRequestContext(httpRequest, request));
    request.getContext().put(JsonKey.REQUESTED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
    if (StringUtils.isNotBlank(httpRequest.attrs().getOptional(Attrs.REQUESTED_FOR).orElse(null)))
      request.getContext().put(SunbirdKey.REQUESTED_FOR, httpRequest.attrs().get(Attrs.REQUESTED_FOR));
    request.getContext().put(JsonKey.X_AUTH_TOKEN, httpRequest.attrs().getOptional(Attrs.X_AUTH_TOKEN).orElse(""));
    request = transformUserId(request);
    return request;
  }

  private RequestContext getRequestContext(Http.Request httpRequest, org.sunbird.common.request.Request request) {
    RequestContext requestContext = new RequestContext(
            JsonKey.SERVICE_NAME,
            JsonKey.PRODUCER_NAME,
            request.getContext().getOrDefault(JsonKey.ENV, "").toString(),
            httpRequest.header(JsonKey.X_DEVICE_ID).orElse(null),
            httpRequest.header(JsonKey.X_SESSION_ID).orElse(null),
            JsonKey.PID,JsonKey.P_VERSION, null);
    requestContext.setActorId(httpRequest.attrs().getOptional(Attrs.ACTOR_ID).orElse(null));
    requestContext.setActorType(httpRequest.attrs().getOptional(Attrs.ACTOR_TYPE).orElse(null));
    requestContext.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
    return requestContext;
  }

  /**
   * Helper method for creating and initialising a request for given operation and request body.
   *
   * @param operation A defined actor operation
   * @param requestBodyJson Optional information received in request body (JSON)
   * @return Created and initialised Request (@see {@link org.sunbird.common.request.Request})
   *     instance.
   */
  protected org.sunbird.common.request.Request createAndInitRequest(
      String operation, JsonNode requestBodyJson, Http.Request httpRequest) {
    org.sunbird.common.request.Request request =
        (org.sunbird.common.request.Request)
            mapper.RequestMapper.mapRequest(
                requestBodyJson, org.sunbird.common.request.Request.class);
    return initRequest(request, operation, httpRequest);
  }

  /**
   * Helper method for creating and initialising a request for given operation.
   *
   * @param operation A defined actor operation
   * @return Created and initialised Request (@see {@link org.sunbird.common.request.Request})
   *     instance.
   */
  protected org.sunbird.common.request.Request createAndInitRequest(
      String operation, Http.Request httpRequest) {
    org.sunbird.common.request.Request request = new org.sunbird.common.request.Request();
    return initRequest(request, operation, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef,
        operation,
        requestBodyJson,
        requestValidatorFn,
        null,
        null,
        null,
        true,
        httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      java.util.function.Function requestValidatorFn,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef, operation, null, requestValidatorFn, null, null, null, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      String pathId,
      String pathVariable,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef, operation, null, null, pathId, pathVariable, null, false, httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      String pathId,
      String pathVariable,
      boolean isJsonBodyRequired,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef,
        operation,
        null,
        null,
        pathId,
        pathVariable,
        null,
        isJsonBodyRequired,
        httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      Map<String, String> headers,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef,
        operation,
        requestBodyJson,
        requestValidatorFn,
        null,
        null,
        headers,
        true,
        httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Http.Request httpRequest) {
    return handleRequest(
        actorRef,
        operation,
        requestBodyJson,
        requestValidatorFn,
        pathId,
        pathVariable,
        null,
        true,
        httpRequest);
  }

  protected CompletionStage<Result> handleRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Map<String, String> headers,
      boolean isJsonBodyRequired,
      Http.Request httpRequest) {
    try {
      org.sunbird.common.request.Request request = null;
      if (!isJsonBodyRequired) {
        request = createAndInitRequest(operation, httpRequest);
      } else {
        request = createAndInitRequest(operation, requestBodyJson, httpRequest);
      }
      if (pathId != null) {
        request.getRequest().put(pathVariable, pathId);
        request.getContext().put(pathVariable, pathId);
      }
      if (requestValidatorFn != null) requestValidatorFn.apply(request);
      if (headers != null) request.getContext().put(JsonKey.HEADER, headers);

      return actorResponseHandler(actorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      logger.error(null,
          "BaseController:handleRequest: Exception occurred with error message = " + e.getMessage(),
          e);
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  protected CompletionStage<Result> handleSearchRequest(
      ActorRef actorRef,
      String operation,
      JsonNode requestBodyJson,
      java.util.function.Function requestValidatorFn,
      String pathId,
      String pathVariable,
      Map<String, String> headers,
      String esObjectType,
      Http.Request httpRequest) {
    try {
      org.sunbird.common.request.Request request = null;
      if (null != requestBodyJson) {
        request = createAndInitRequest(operation, requestBodyJson, httpRequest);
      } else {
        ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData, null);
      }
      if (request != null) {
        if (pathId != null) {
          request.getRequest().put(pathVariable, pathId);
          request.getContext().put(pathVariable, pathId);
        }
        if (requestValidatorFn != null) requestValidatorFn.apply(request);
        if (headers != null) request.getContext().put(JsonKey.HEADER, headers);
        if (StringUtils.isNotBlank(esObjectType)) {
          List<String> esObjectTypeList = new ArrayList<>();
          esObjectTypeList.add(esObjectType);
          ((Map) (request.getRequest().get(JsonKey.FILTERS)))
              .put(JsonKey.OBJECT_TYPE, esObjectTypeList);
        }
        request.getRequest().put(JsonKey.REQUESTED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      }
      return actorResponseHandler(actorRef, request, timeout, null, httpRequest);
    } catch (Exception e) {
      logger.error(null,
          "BaseController:handleRequest: Exception occurred with error message = " + e.getMessage(),
          e);
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will create failure response
   *
   * @param request Request
   * @param code ResponseCode
   * @param headerCode ResponseCode
   * @return Response
   */
  public static Response createFailureResponse(
      Request request, ResponseCode code, ResponseCode headerCode) {

    Response response = new Response();
    response.setVer(getApiVersion(request.path()));
    response.setId(getApiResponseId(request));
    response.setTs(ProjectUtil.getFormattedDate());
    response.setResponseCode(headerCode);
    response.setParams(createResponseParamObj(code, null, request.attrs().getOptional(Attrs.REQUEST_ID).orElse(null)));
    return response;
  }

  public static ResponseParams createResponseParamObj(ResponseCode code, String customMessage, String requestId) {
    ResponseParams params = new ResponseParams();
    if (code.getResponseCode() != 200) {
      params.setErr(code.getErrorCode());
      params.setErrmsg(
          StringUtils.isNotBlank(customMessage) ? customMessage : code.getErrorMessage());
    }
    params.setMsgid(requestId);
    params.setStatus(ResponseCode.getHeaderResponseCode(code.getResponseCode()).name());
    return params;
  }

  /**
   * This method will create data for success response.
   *
   * @param request play.mvc.Http.Request
   * @param response Response
   * @return Result
   */
  public static Result createSuccessResponse(Request request, Response response) {
    if (request != null) {
      response.setVer(getApiVersion(request.path()));
    } else {
      response.setVer("");
    }

    response.setId(getApiResponseId(request));
    response.setTs(ProjectUtil.getFormattedDate());
    ResponseCode code = ResponseCode.getResponse(ResponseCode.success.getErrorCode());
    code.setResponseCode(ResponseCode.OK.getResponseCode());
    response.setParams(createResponseParamObj(code, null, request.attrs().getOptional(Attrs.REQUEST_ID).orElse(null)));

    String value = null;
    try {
      if (response.getResult() != null) {
        String json = new ObjectMapper().writeValueAsString(response.getResult());
        value = getResponseSize(json);
      }
    } catch (Exception e) {
      value = "0.0";
    }

    return Results.ok(Json.toJson(response))
        .withHeader(HeaderParam.X_Response_Length.getName(), value);
  }

  /**
   * This method will provide api version.
   *
   * @param request String
   * @return String
   */
  public static String getApiVersion(String request) {

    return request.split("[/]")[1];
  }

  /**
   * This method will handle response in case of exception
   *
   * @param request play.mvc.Http.Request
   * @param exception ProjectCommonException
   * @return Response
   */
  public static Response createResponseOnException(
      Http.Request request, ProjectCommonException exception) {
    logger.error(null,
        exception != null ? exception.getMessage() : "Message is not coming",
        exception,
        genarateTelemetryInfoForError(request));
    Response response = new Response();
    response.setVer("");
    if (request != null) {
      response.setVer(getApiVersion(request.path()));
    }
    response.setId(getApiResponseId(request));
    response.setTs(ProjectUtil.getFormattedDate());
    if (exception != null) {
      response.setResponseCode(ResponseCode.getHeaderResponseCode(exception.getResponseCode()));
      ResponseCode code = ResponseCode.getResponse(exception.getCode());
      if (code == null) {
        code = ResponseCode.SERVER_ERROR;
      }
      response.setParams(createResponseParamObj(code, exception.getMessage(), request.attrs().getOptional(Attrs.REQUEST_ID).orElse(null)));
      if (response.getParams() != null) {
        response.getParams().setStatus(response.getParams().getStatus());
        if (exception.getCode() != null) {
          response.getParams().setStatus(exception.getCode());
        }
        if (!StringUtils.isBlank(response.getParams().getErrmsg())
            && response.getParams().getErrmsg().contains("{0}")) {
          response.getParams().setErrmsg(exception.getMessage());
        }
      }
    }
    return response;
  }

  /**
   * @param path String
   * @param method String
   * @param exception ProjectCommonException
   * @return Response
   */
  public static Response createResponseOnException(
      String path, String method, ProjectCommonException exception) {
    Response response = new Response();
    response.setVer(getApiVersion(path));
    response.setId(getApiResponseId(path, method));
    response.setTs(ProjectUtil.getFormattedDate());
    response.setResponseCode(ResponseCode.getHeaderResponseCode(exception.getResponseCode()));
    ResponseCode code = ResponseCode.getResponse(exception.getCode());
    response.setParams(createResponseParamObj(code, exception.getMessage(), null));
    return response;
  }

  /**
   * This method will create common response for all controller method
   *
   * @param response Object
   * @param key String
   * @param request play.mvc.Http.Request
   * @return Result
   */
  public Result createCommonResponse(Object response, String key, Http.Request request) {
    Response courseResponse = (Response) response;
    if (!StringUtils.isBlank(key)) {
      Object value = courseResponse.getResult().get(JsonKey.RESPONSE);
      courseResponse.getResult().remove(JsonKey.RESPONSE);
      courseResponse.getResult().put(key, value);
    }
    return BaseController.createSuccessResponse(request, courseResponse);
  }

  /**
   * @param file
   * @return
   */
  public Result createFileDownloadResponse(File file) {
    return Results.ok(file)
        .withHeader("Content-Type", "application/x-download")
        .withHeader("Content-disposition", "attachment; filename=" + file.getName());
  }

  private void removeFields(Map<String, Object> params, String... properties) {
    for (String property : properties) {
      params.remove(property);
    }
  }

  private String generateStackTrace(StackTraceElement[] elements) {
    StringBuilder builder = new StringBuilder("");
    for (StackTraceElement element : elements) {

      builder.append(element.toString());
      builder.append("\n");
    }
    return ProjectUtil.getFirstNCharacterString(builder.toString(), 100);
  }

  private Map<String, Object> generateTelemetryRequestForController(
      String eventType, Map<String, Object> params, Map<String, Object> context) {

    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.TELEMETRY_EVENT_TYPE, eventType);
    map.put(JsonKey.CONTEXT, context);
    map.put(JsonKey.PARAMS, params);
    return map;
  }

  /**
   * Common exception response handler method.
   *
   * @param e Exception
   * @param request play.mvc.Http.Request
   * @return Result
   */
  public Result createCommonExceptionResponse(Exception e, Http.Request request) {
    Request req = request;
    logger.error(null, e.getMessage(), e, genarateTelemetryInfoForError(request));
    ProjectCommonException exception = null;
    if (e instanceof ProjectCommonException) {
      exception = (ProjectCommonException) e;
    } else {
      exception =
          new ProjectCommonException(
              ResponseCode.internalError.getErrorCode(),
              ResponseCode.internalError.getErrorMessage(),
              ResponseCode.SERVER_ERROR.getResponseCode());
    }
    generateExceptionTelemetry(request, exception);
    // cleaning request info ...
    return Results.status(
        exception.getResponseCode(),
        Json.toJson(createResponseOnException(req, exception)));
  }

  private long calculateApiTimeTaken(Long startTime) {

    Long timeConsumed = null;
    if (null != startTime) {
      timeConsumed = System.currentTimeMillis() - startTime;
    }
    return timeConsumed;
  }

  /**
   * This method will make a call to Akka actor and return promise.
   *
   * @param actorRef ActorSelection
   * @param request Request
   * @param timeout Timeout
   * @param responseKey String
   * @param httpReq play.mvc.Http.Request
   * @return CompletionStage<Result>
   */
  public CompletionStage<Result> actorResponseHandler(
      Object actorRef,
      org.sunbird.common.request.Request request,
      Timeout timeout,
      String responseKey,
      Http.Request httpReq) {

    String operation = request.getOperation();

    // set header to request object , setting actor type and channel headers value
    // ...
    setContextData(httpReq, request);
    setChannelAndActorInfo(httpReq, request);

    Function<Object, Result> function =
        new Function<Object, Result>() {
          @Override
          public Result apply(Object result) {
            if (ActorOperations.HEALTH_CHECK.getValue().equals(request.getOperation())) {
              setGlobalHealthFlag(result);
            }

            if (result instanceof Response) {
              Response response = (Response) result;
              return createCommonResponse(response, responseKey, httpReq);
            } else if (result instanceof ProjectCommonException) {
              return createCommonExceptionResponse((ProjectCommonException) result, httpReq);
            } else if (result instanceof File) {
              return createFileDownloadResponse((File) result);
            } else {
              return createCommonExceptionResponse(new Exception(), httpReq);
            }
          }
        };

    if (actorRef instanceof ActorRef) {
      return PatternsCS.ask((ActorRef) actorRef, request, timeout).thenApply(function);
    } else {
      return PatternsCS.ask((ActorSelection) actorRef, request, timeout).thenApply(function);
    }
  }

  /**
   * This method will provide environment id.
   *
   * @return int
   */
  public int getEnvironment() {

    if (ApplicationStart.env != null) {
      return ApplicationStart.env.getValue();
    }
    return ProjectUtil.Environment.dev.getValue();
  }

  /**
   * Method to get UserId by AuthToken
   *
   * @param token
   * @return String
   */
  public String getUserIdByAuthToken(String token) {

    return AuthenticationHelper.verifyUserAccessToken(token);
  }

  /**
   * Method to get API response Id
   *
   * @param request play.mvc.Http.Request
   * @return String
   */
  private static String getApiResponseId(Request request) {

    String val = "";
    if (request != null) {
      String path = request.path();
      if (request.method().equalsIgnoreCase(ProjectUtil.Method.GET.name())) {
        val = getResponseId(path);
        if (StringUtils.isBlank(val)) {
          String[] splitedpath = path.split("[/]");
          path = removeLastValue(splitedpath);
          val = getResponseId(path);
        }
      } else {
        val = getResponseId(path);
      }
      if (StringUtils.isBlank(val)) {
        val = getResponseId(path);
        if (StringUtils.isBlank(val)) {
          String[] splitedpath = path.split("[/]");
          path = removeLastValue(splitedpath);
          val = getResponseId(path);
        }
      }
    }
    return val;
  }

  /**
   * Method to get API response Id
   *
   * @param path String
   * @param method String
   * @return String
   */
  private static String getApiResponseId(String path, String method) {
    String val = "";
    if (ProjectUtil.Method.GET.name().equalsIgnoreCase(method)) {
      val = getResponseId(path);
      if (StringUtils.isBlank(val)) {
        String[] splitedpath = path.split("[/]");
        String tempPath = removeLastValue(splitedpath);
        val = getResponseId(tempPath);
      }
    } else {
      val = getResponseId(path);
    }
    return val;
  }

  /**
   * Method to remove last value
   *
   * @param splited String []
   * @return String
   */
  private static String removeLastValue(String splited[]) {

    StringBuilder builder = new StringBuilder();
    if (splited != null && splited.length > 0) {
      for (int i = 1; i < splited.length - 1; i++) {
        builder.append("/" + splited[i]);
      }
    }
    return builder.toString();
  }

  private static Map<String, Object> genarateTelemetryInfoForError(Http.Request request) {
    try{
      Map<String, Object> map = new HashMap<>();
      String reqContext = request.attrs().getOptional(Attrs.CONTEXT).orElse(null);
      Map<String, Object> requestInfo =
              objectMapper.readValue(reqContext, new TypeReference<Map<String, Object>>() {});
      Map<String, Object> contextInfo = (Map<String, Object>) requestInfo.getOrDefault(JsonKey.CONTEXT, new HashMap<String, Object>());
      Map<String, Object> params = new HashMap<>();
      params.put(JsonKey.ERR_TYPE, JsonKey.API_ACCESS);

      map.put(JsonKey.CONTEXT, contextInfo);
      map.put(JsonKey.PARAMS, params);
      return map;
    } catch (Exception e) {
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR);
    }
    return Collections.emptyMap();
  }

  public void setChannelAndActorInfo(
      Http.Request httpReq, org.sunbird.common.request.Request reqObj) {

    reqObj.getContext().put(JsonKey.CHANNEL, httpReq.attrs().getOptional(Attrs.CHANNEL).orElse(null));
    reqObj.getContext().put(JsonKey.ACTOR_ID, httpReq.attrs().getOptional(Attrs.ACTOR_ID).orElse(null));
    reqObj.getContext().put(JsonKey.ACTOR_TYPE, httpReq.attrs().getOptional(Attrs.ACTOR_TYPE).orElse(null) );
    reqObj.getContext().put(JsonKey.APP_ID, httpReq.attrs().getOptional(Attrs.APP_ID).orElse(null));
    reqObj.getContext().put(JsonKey.DEVICE_ID, httpReq.attrs().getOptional(Attrs.DEVICE_ID).orElse(null));
    reqObj
        .getContext()
        .put(
            JsonKey.SIGNUP_TYPE,
            httpReq.attrs().getOptional(Attrs.SIGNUP_TYPE).orElse(null)); // adding signup type in request context
    reqObj
        .getContext()
        .put(
            JsonKey.REQUEST_SOURCE,
            httpReq.attrs().getOptional(Attrs.REQUEST_SOURCE).orElse(null)); // ADDING Source under params in context
  }

  public Map<String, String> getAllRequestHeaders(Request request) {
    Map<String, String> map = new HashMap<>();
    Map<String, List<String>> headers = request.getHeaders().toMap();
    Iterator<Map.Entry<String, List<String>>> itr = headers.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, List<String>> entry = itr.next();
      map.put(entry.getKey(), entry.getValue().get(0));
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private void setGlobalHealthFlag(Object result) {
    if (result instanceof Response) {
      Response response = (Response) result;
      if (Boolean.parseBoolean(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_HEALTH_CHECK_ENABLE))
          && ((HashMap<String, Object>) response.getResult().get(JsonKey.RESPONSE))
              .containsKey(JsonKey.Healthy)) {
        OnRequestHandler.isServiceHealthy =
            (boolean)
                ((HashMap<String, Object>) response.getResult().get(JsonKey.RESPONSE))
                    .get(JsonKey.Healthy);
      }
    } else {
      OnRequestHandler.isServiceHealthy = false;
    }
    logger.debug(null,
        "BaseController:setGlobalHealthFlag: isServiceHealthy = "
            + OnRequestHandler.isServiceHealthy);
  }

  protected String getQueryString(Map<String, String[]> queryStringMap) {
    return queryStringMap
        .entrySet()
        .stream()
        .map(p -> p.getKey() + "=" + String.join(",", p.getValue()))
        .reduce((p1, p2) -> p1 + "&" + p2)
        .map(s -> "?" + s)
        .orElse("");
  }

  public static String getResponseSize(String response) throws UnsupportedEncodingException {
    if (StringUtils.isNotBlank(response)) {
      return response.getBytes("UTF-8").length + "";
    }
    return "0.0";
  }

  public org.sunbird.common.request.Request transformUserId(
      org.sunbird.common.request.Request request) {
    if (request != null && request.getRequest() != null) {
      String id = (String) request.getRequest().get(JsonKey.ID);
      request.getRequest().put(JsonKey.ID, ProjectUtil.getLmsUserId(id));
      id = (String) request.getRequest().get(JsonKey.USER_ID);
      request.getRequest().put(JsonKey.USER_ID, ProjectUtil.getLmsUserId(id));
      return request;
    }
    return request;
  }

  /**
   * Method to get the response id on basis of request path.
   *
   * @param requestPath
   * @return
   */
  public static String getResponseId(String requestPath) {

    String path = requestPath;
    final String ver = "/" + version;
    final String ver2 = "/" + JsonKey.VERSION_2;
    path = path.trim();
    StringBuilder builder = new StringBuilder("");
    if (path.startsWith(ver) || path.startsWith(ver2)) {
      String requestUrl = (path.split("\\?"))[0];
      if (requestUrl.contains(ver)) {
        requestUrl = requestUrl.replaceFirst(ver, "api");
      } else if (requestUrl.contains(ver2)) {
        requestUrl = requestUrl.replaceFirst(ver2, "api");
      }

      String[] list = requestUrl.split("/");
      for (String str : list) {
        if (str.matches("[A-Za-z]+")) {
          builder.append(str).append(".");
        }
      }
      builder.deleteCharAt(builder.length() - 1);
    } else {
      if ("/health".equalsIgnoreCase(path)) {
        builder.append("api.all.health");
      }
    }
    return builder.toString();
  }

  public void setContextData(Http.Request httpReq, org.sunbird.common.request.Request reqObj) {
    try {
      String reqContext = httpReq.attrs().get(Attrs.CONTEXT);
      Map<String, Object> requestInfo =
              objectMapper.readValue(reqContext, new TypeReference<Map<String, Object>>() {});
      reqObj.setRequestId(httpReq.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getContext().putAll((Map<String, Object>) requestInfo.get(JsonKey.CONTEXT));
      reqObj.getContext().putAll((Map<String, Object>) requestInfo.get(JsonKey.ADDITIONAL_INFO));
    } catch (Exception ex) {
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR);
    }
  }

  private void generateExceptionTelemetry(Request request, ProjectCommonException exception) {
    try {
      String reqContext = request.attrs().get(Attrs.CONTEXT);
      Map<String, Object> requestInfo = objectMapper.readValue(reqContext, new TypeReference<Map<String, Object>>() {});
      org.sunbird.common.request.Request reqForTelemetry = new org.sunbird.common.request.Request();
      Map<String, Object> params = (Map<String, Object>) requestInfo.getOrDefault(JsonKey.ADDITIONAL_INFO, new HashMap<>());
      params.put(JsonKey.LOG_TYPE, JsonKey.API_ACCESS);
      params.put(JsonKey.MESSAGE, "");
      params.put(JsonKey.METHOD, request.method());
      params.put("err", exception.getResponseCode() + "");
      params.put("errtype", exception.getCode());
      long startTime = (Long) params.get(JsonKey.START_TIME);
      params.put(JsonKey.DURATION, calculateApiTimeTaken(startTime));
      removeFields(params, JsonKey.START_TIME);
      params.put(JsonKey.STATUS, String.valueOf(exception.getResponseCode()));
      params.put(JsonKey.LOG_LEVEL, "error");
      params.put(JsonKey.STACKTRACE, generateStackTrace(exception.getStackTrace()));
      reqForTelemetry.setRequest(
              generateTelemetryRequestForController(
                      TelemetryEvents.ERROR.getName(),
                      params,
                      (Map<String, Object>) requestInfo.get(JsonKey.CONTEXT)));
      TelemetryWriter.write(reqForTelemetry);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
