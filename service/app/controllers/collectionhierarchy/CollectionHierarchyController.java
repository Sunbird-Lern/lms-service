package controllers.collectionhierarchy;

import akka.actor.ActorRef;
import akka.util.Timeout;
import controllers.BaseController;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import play.libs.Files;
import play.mvc.Http;
import play.mvc.Result;
import util.Attrs;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.sunbird.common.exception.ProjectCommonException.throwClientErrorException;

/**
 * Handles Collection TOC CSV APIs.
 *
 * @author jayaprakash
 */
public class CollectionHierarchyController extends BaseController {

  @Inject
  @Named("collection-toc-actor")
  private ActorRef collectionTOCActorRef;

  private static final int UPLOAD_TOC_TIMEOUT = 30;

  public CompletionStage<Result> uploadTOC(String collectionId, Http.Request httpRequest) {
    try {

      Request request = createAndInitUploadRequest(JsonKey.COLLECTION_CSV_TOC_UPLOAD, httpRequest);
      request.put(JsonKey.COLLECTION_ID, collectionId);
      request.setTimeout(UPLOAD_TOC_TIMEOUT);
      Timeout uploadTimeout = new Timeout(UPLOAD_TOC_TIMEOUT, TimeUnit.SECONDS);
      return actorResponseHandler(collectionTOCActorRef, request, uploadTimeout, null, httpRequest);

    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  public CompletionStage<Result> getTOCUrl(String collectionId, Http.Request httpRequest) {
    try {
      return handleRequest(collectionTOCActorRef, JsonKey.COLLECTION_CSV_TOC_DOWNLOAD, collectionId, JsonKey.COLLECTION_ID, httpRequest);
    } catch (Exception e) {
      return CompletableFuture.completedFuture(createCommonExceptionResponse(e, httpRequest));
    }
  }

  //  @Override
  public Request createAndInitUploadRequest(String operation, Http.Request httpRequest) throws IOException {
    logger.info(null, "API call for operation : " + operation);
    Request reqObj = new Request();
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> header = new HashMap<>();
    InputStream inputStream = null;

    String fileUrl = httpRequest.getQueryString(JsonKey.FILE_URL);
    if (StringUtils.isNotBlank(fileUrl)) {
      logger.info(null, "Got fileUrl from path parameter: " + fileUrl);
      URL url = new URL(fileUrl.trim());
      inputStream = url.openStream();
    } else {
      Http.MultipartFormData body = httpRequest.body().asMultipartFormData();
      if (body != null) {
        Map<String, String[]> data = body.asFormUrlEncoded();
        if (MapUtils.isNotEmpty(data) && data.containsKey(JsonKey.FILE_URL)) {
          fileUrl = data.getOrDefault(JsonKey.FILE_URL, new String[] {""})[0];
          if (StringUtils.isBlank(fileUrl) || !StringUtils.endsWith(fileUrl, ".csv")) {
            throwClientErrorException(ResponseCode.csvError, ResponseCode.csvError.getErrorMessage());
          }
          URL url = new URL(fileUrl.trim());
          inputStream = url.openStream();
        } else {
          List<Http.MultipartFormData.FilePart<Files.TemporaryFile>> filePart = body.getFiles();
          if (CollectionUtils.isEmpty(filePart)) {
            throwClientErrorException(ResponseCode.fileNotFound, ResponseCode.fileNotFound.getErrorMessage());
          }
          inputStream = new FileInputStream(filePart.get(0).getRef().path().toFile());
        }
      } else {
        logger.info(null, "Collection toc upload request body is empty");
        throwClientErrorException(ResponseCode.invalidData, ResponseCode.invalidData.getErrorMessage());
      }
    }

    byte[] byteArray = IOUtils.toByteArray(inputStream);
    try {
      if (null != inputStream) {
        inputStream.close();
      }
    } catch (Exception e) {
      logger.error(null, "CollectionHierarchyController:createAndInitUploadRequest : Exception occurred while closing stream", e);
    }
    reqObj.setOperation(operation);
    reqObj.setRequestId(httpRequest.attrs().getOptional(Attrs.REQUEST_ID).orElse(null));
    reqObj.setEnv(getEnvironment());
    map.put(JsonKey.CREATED_BY, httpRequest.attrs().getOptional(Attrs.USER_ID).orElse(null));
    map.put(JsonKey.DATA, byteArray);
    reqObj.setRequest(map);
    header.put(JsonKey.HEADER, getAllRequestHeaders(httpRequest));
    reqObj.setContext(header);

    return reqObj;
  }
}
