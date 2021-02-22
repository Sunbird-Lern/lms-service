/** */
package controllers.pagemanagement;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;
import org.sunbird.common.Common;

/**
 * This controller will handle all the request related to page api's.
 *
 * @author Amit Kumar
 */
public class PageController extends BaseController {

  @Inject
  @Named("page-management-actor")
  private ActorRef pageManagementActorRef;

  /**
   * This method will allow admin to create a page for view.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> createPage(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateCreatePage(reqObj);
      reqObj.setOperation(ActorOperations.CREATE_PAGE.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE, reqObj.getRequest());
      reqObj.setRequest(map);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will allow admin to update already created page data.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> updatePage(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateUpdatepage(reqObj);
      reqObj.setOperation(ActorOperations.UPDATE_PAGE.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getRequest().put(JsonKey.UPDATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE, reqObj.getRequest());
      reqObj.setRequest(map);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide particular page setting data.
   *
   * @param pageId String
   * @return Promise<Result>
   */
  public CompletionStage<Result> getPageSetting(
      String pageId, String organisationId, Http.Request httpRequest) {

    try {
      ProjectLogger.log(
          "getting data for particular page settings = " + pageId, LoggerEnum.INFO.name());
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.GET_PAGE_SETTING.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      reqObj.getRequest().put(JsonKey.ID, pageId);
      reqObj.getRequest().put(JsonKey.ORGANISATION_ID, organisationId);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide completed data for all pages which is saved in cassandra DAC.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> getPageSettings(Http.Request httpRequest) {

    try {
      ProjectLogger.log("getting page settings api called = ", LoggerEnum.INFO.name());
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.GET_PAGE_SETTINGS.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide completed data for a particular page.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> getPageData(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateGetPageData(reqObj);
      reqObj.setOperation(ActorOperations.GET_PAGE_DATA.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      reqObj.getContext().put(JsonKey.URL_QUERY_STRING, getQueryString(httpRequest.queryString()));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE, reqObj.getRequest());
      map.put(JsonKey.HEADER, getAllRequestHeaders(httpRequest));
      reqObj.setRequest(map);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      ProjectLogger.log(
          "PageController:getPageData: Exception occurred with error message = " + e.getMessage(),
          LoggerEnum.ERROR.name());
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide completed data for a particular page.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> getDIALPageData(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateGetPageData(reqObj);
      reqObj.setOperation(ActorOperations.GET_DIAL_PAGE_DATA.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      reqObj.getContext().put(JsonKey.URL_QUERY_STRING, getQueryString(httpRequest.queryString()));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.PAGE, reqObj.getRequest());
      map.put(JsonKey.HEADER, getAllRequestHeaders(httpRequest));
      reqObj.setRequest(map);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      ProjectLogger.log(
              "PageController:getPageData: Exception occurred with error message = " + e.getMessage(),
              LoggerEnum.ERROR.name());
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * Method to get all request headers
   *
   * @param request play.mvc.Http.Request
   * @return Map<String, String>
   */
  public Map<String, String> getAllRequestHeaders(play.mvc.Http.Request request) {
    Map<String, String[]> headers = Common.getRequestHeadersInArray(request.getHeaders().toMap());
    Map<String, String> filtered =
        headers
            .entrySet()
            .stream()
            .filter(e -> StringUtils.startsWithAny(e.getKey(), "X-", "x-"))
            .collect(Collectors.toMap(e -> e.getKey(), e -> StringUtils.join(e.getValue(), ",")));
    return filtered;
  }

  /**
   * This method will allow admin to create sections for page view
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> createPageSection(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      ProjectLogger.log(
          "getting create page section data request=" + requestData, LoggerEnum.INFO.name());
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateCreateSection(reqObj);
      reqObj.setOperation(ActorOperations.CREATE_SECTION.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.getRequest().put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      HashMap<String, Object> map = new HashMap<>();
      map.put(JsonKey.SECTION, reqObj.getRequest());
      reqObj.setRequest(map);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will allow admin to update already created page sections
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> updatePageSection(Http.Request httpRequest) {

    try {
      JsonNode requestData = httpRequest.body().asJson();
      ProjectLogger.log(
          "getting update page section data request=" + requestData, LoggerEnum.INFO.name());
      Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
      RequestValidator.validateUpdateSection(reqObj);
      reqObj.setOperation(ActorOperations.UPDATE_SECTION.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      HashMap<String, Object> innerMap = new HashMap<>();
      reqObj.getRequest().put(JsonKey.UPDATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
      innerMap.put(JsonKey.SECTION, reqObj.getRequest());
      reqObj.setRequest(innerMap);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide particular page section data.
   *
   * @param sectionId String
   * @return Promise<Result>
   */
  public CompletionStage<Result> getSection(String sectionId, Http.Request httpRequest) {

    try {
      ProjectLogger.log(
          "getting data for particular page section =" + sectionId, LoggerEnum.INFO.name());
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.GET_SECTION.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      reqObj.getRequest().put(JsonKey.ID, sectionId);
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  /**
   * This method will provide completed data for all sections stored in cassandra DAC.
   *
   * @return Promise<Result>
   */
  public CompletionStage<Result> getSections(Http.Request httpRequest) {

    try {
      ProjectLogger.log("get page all section method called = ", LoggerEnum.INFO.name());
      Request reqObj = new Request();
      reqObj.setOperation(ActorOperations.GET_ALL_SECTION.getValue());
      reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
      reqObj.setEnv(getEnvironment());
      return actorResponseHandler(pageManagementActorRef, reqObj, timeout, null, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }
}
