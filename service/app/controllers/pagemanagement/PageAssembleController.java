package controllers.pagemanagement;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
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
import scala.Tuple2;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PageAssembleController extends BaseController {

    @Inject
    WSClient wsClient;

    @Inject
    ActorSystem system;

    public Promise<Result> getPageData() {

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


                        if (MapUtils.isNotEmpty(resResponse)) {
                            List<Map<String, Object>> sections = (List<Map<String, Object>>) resResponse.get("sections");
                            if (CollectionUtils.isNotEmpty(sections)) {
                                List<Promise<Map<String, Object>>> futures = sections.stream().map(f ->
                                        {
                                            String query = (String) f.get("searchQuery");
                                            List<Tuple2<String, String>> headers = Arrays.asList(
                                                    new Tuple2<String, String>(HttpHeaders.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.SUNBIRD_AUTHORIZATION)),
                                                    new Tuple2<String, String>(HttpHeaders.CONTENT_TYPE, "application/json"),
                                                    new Tuple2<String, String>(HttpHeaders.CONNECTION, "Keep-Alive"));

                                            long startTime = System.currentTimeMillis();
                                            return Promise.wrap(wsClient.url("http://28.0.3.10:9000/v3/search")
                                                    .withHeaders(JavaConverters.asScalaIteratorConverter(headers.iterator()).asScala().toSeq())
                                                    .post(query, Writeable.wString(Codec.utf_8()))).map(new Function<WSResponse, Map<String, Object>>() {
                                                @Override
                                                public Map<String, Object> apply(WSResponse wsResponse) throws Throwable {
                                                    System.out.println("Time taken for content-search: " + (System.currentTimeMillis() - startTime));
                                                    f.put("contents", Json.parse(wsResponse.body()));
                                                    return f;
                                                }
                                            });
//                                            return Promise.pure(f);
                                        }
                                ).parallel().collect(Collectors.toList());

                                return Promise.sequence(futures, system.dispatcher()).map(new Function<List<Map<String, Object>>, Result>() {
                                    @Override
                                    public Result apply(List<Map<String, Object>> maps) throws Throwable {
                                        return Results.ok(Json.toJson(maps));
                                    }
                                });

//                                return Promise.pure(Results.ok());

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
