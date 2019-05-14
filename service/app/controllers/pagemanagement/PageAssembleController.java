package controllers.pagemanagement;

import akka.actor.ActorRef;
import akka.pattern.FutureRef;
import akka.pattern.Patterns;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.BaseController;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestValidator;
import org.sunbird.learner.util.ContentSearchUtil;
import play.api.http.Writeable;
import play.api.libs.ws.WSClient;
import play.api.libs.ws.WSResponse;
import play.api.mvc.Codec;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;
import reactor.rx.Promises;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.immutable.Seq;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PageAssembleController extends BaseController {

    @Inject
    WSClient wsClient;

    public Promise<Result> getPageData() {

        System.out.println("ContentSearch URL: " + ContentSearchUtil.contentSearchURL);
        try {
            JsonNode requestData = request().body().asJson();
            Request reqObj = (Request) mapper.RequestMapper.mapRequest(requestData, Request.class);
            RequestValidator.validateGetPageData(reqObj);
            reqObj.setOperation(ActorOperations.GET_PAGE_DATA.getValue());
            reqObj.setRequestId(ExecutionContext.getRequestId());
            reqObj.setEnv(getEnvironment());
            reqObj.getContext().put(JsonKey.URL_QUERY_STRING, getQueryString(request().queryString()));
            reqObj.getRequest().put(JsonKey.CREATED_BY, ctx().flash().get(JsonKey.USER_ID));
            HashMap<String, Object> map = new HashMap<>();
            map.put(JsonKey.PAGE, reqObj.getRequest());
            map.put(JsonKey.HEADER, getAllRequestHeaders(request()));
            reqObj.setRequest(map);

            return Promise.wrap(Patterns.ask((ActorRef) getActorRef(), reqObj, timeout)).map(new Function<Object, Promise<Result>>() {
                @Override
                public Promise<Result> apply(Object o) throws Throwable {
                    if (o instanceof Response) {
                        Response res = (Response) o;
                        Map<String, Object> resResponse = (Map<String, Object>) res.getResult().get("response");

                        Promise<Result> pageResponse = null;
                        if (MapUtils.isNotEmpty(resResponse)) {
                            List<Map<String, Object>> sections = (List<Map<String, Object>>) resResponse.get("sections");
                            if (CollectionUtils.isNotEmpty(sections)) {
                                List<Promise<Map<String, Object>>> futures = sections.stream().map(f ->
                                        {
                                            String query = (String) f.get("searchQuery");
                                            System.out.println("Query: "+ query);


                                            return Promise.wrap(wsClient.url(ContentSearchUtil.contentSearchURL)
                                                    .withHeaders(JavaConverters.asScalaIteratorConverter(Arrays.asList(new Tuple2<String, String>(HttpHeaders.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.SUNBIRD_AUTHORIZATION)), new Tuple2<String, String>(HttpHeaders.CONTENT_TYPE, "application/json")).iterator()).asScala().toSeq())
                                                    .post(query, Writeable.wString(Codec.utf_8()))).map(new Function<WSResponse, Map<String, Object>>() {
                                                @Override
                                                public Map<String, Object> apply(WSResponse wsResponse) throws Throwable {
//                                                    System.out.println("Status for search: " + wsResponse.status() + " Body: " + wsResponse.body());
                                                    f.put("contents", Json.parse(wsResponse.body()));
                                                    return f;
                                                }
                                            });
                                        }
                                ).collect(Collectors.toList());

                                pageResponse = Promise.sequence(futures).map(new Function<List<Map<String, Object>>, Result>() {
                                    @Override
                                    public Result apply(List<Map<String, Object>> maps) throws Throwable {
                                        return Results.ok(Json.toJson(maps));
                                    }
                                });

                                return pageResponse;
                            }
                        }

                        return Promise.pure(Results.ok(Json.toJson(o)));

                    } else if (o instanceof Exception) {
                        return Promise.pure(createCommonExceptionResponse((Exception) o, request()));
                    } else {
                        return Promise.pure(createCommonExceptionResponse(new Exception(), request()));
                    }

                }
            }).flatMap(new Function<Promise<Result>, Promise<Result>>() {
                @Override
                public Promise<Result> apply(Promise<Result> resultPromise) throws Throwable {
                    return resultPromise;
                }
            });
        } catch (Exception e) {
            ProjectLogger.log(
                    "PageController:getPageData: Exception occurred with error message = " + e.getMessage(),
                    LoggerEnum.ERROR.name());
            return  Promise.<Result>pure(createCommonExceptionResponse(e, request()));
        }
    }
}
