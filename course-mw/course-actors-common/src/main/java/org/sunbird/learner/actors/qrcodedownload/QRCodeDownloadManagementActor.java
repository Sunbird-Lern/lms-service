package org.sunbird.learner.actors.qrcodedownload;

import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.CloudStorageUtil;
import org.sunbird.learner.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.io.File.separator;
import static org.sunbird.common.models.util.JsonKey.*;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

/**
 * @Author : Rhea Fernandes This actor is used to create an html file for all the qr code images
 * that are linked to courses that are created userIds given
 */
public class QRCodeDownloadManagementActor extends BaseActor {

  private final QRCodeDownloadManager downloadManager = new QRCodeDownloadManager();

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.QR_CODE_DOWNLOAD, this.getClass().getName());

    String requestedOperation = request.getOperation();
    switch (requestedOperation) {
      case "downloadQRCodes":
        downloadQRCodes(request);
        break;

      default:
        onReceiveUnsupportedOperation(requestedOperation);
        break;
    }
  }

  /**
   * The request must contain list of userIds (Users Ids of people who have created courses)
   *
   * @param request
   */
  private void downloadQRCodes(Request request) throws UnirestException {
    Map<String, String> headers = (Map<String, String>) request.getRequest().get(JsonKey.HEADER);
    Map<String, Object> requestMap = (Map<String, Object>) request.getRequest().get(JsonKey.FILTER);
    requestMap.put(JsonKey.CONTENT_TYPE, "course");
    Map<String, Object> searchResponse = downloadManager.searchCourses(request.getRequestContext(), requestMap, headers);
    List<Map<String, Object>> contents = (List<Map<String, Object>>) searchResponse.get("contents");
    if (CollectionUtils.isEmpty(contents))
      throw new ProjectCommonException(
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorCode(),
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    String channel = (String) contents.get(0).get("channel");
    Map<String, List<String>> dialCodesMap =
        contents
            .stream()
            .filter(content -> content.get("dialcodes") != null)
            .filter(content -> content.get("name") != null)
            .collect(
                Collectors.toMap(
                    content -> ((String) content.get("identifier")) + "<<<" + (String) content.get("name"),
                    content -> (List<String>) content.get("dialcodes"), (a,b) -> b, LinkedHashMap::new));
    File file = generateCSVFile(request.getRequestContext(), dialCodesMap, channel);
    Response response = new Response();
    if (null == file)
      throw new ProjectCommonException(
          ResponseCode.errorProcessingFile.getErrorCode(),
          ResponseCode.errorProcessingFile.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());

    response = uploadFile(request.getRequestContext(), file);
    sender().tell(response, self());
  }


  /**
   * Generates the CSV File for the data provided
   *
   * @param requestContext
   * @param dialCodeMap
   * @return
   */
  private File generateCSVFile(RequestContext requestContext, Map<String, List<String>> dialCodeMap, String channel) {
    File file = null;
    if (MapUtils.isEmpty(dialCodeMap))
      throw new ProjectCommonException(
          ResponseCode.errorNoDialcodesLinked.getErrorCode(),
          ResponseCode.errorNoDialcodesLinked.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    try {
      file = new File(UUID.randomUUID().toString() + ".csv");
      StringBuilder csvFile = new StringBuilder();
      csvFile.append("Course Name,Dialcodes,Image Url");

      Set<String> dialCodes = dialCodeMap.values().stream()
              .flatMap(List::stream)
              .collect(Collectors.toSet());

      Map<String, String> dialcodeImageUrlMap = downloadManager.getQRCodeImageURLs(dialCodes, channel);

      dialCodeMap
          .keySet()
          .forEach(
              name -> {
                dialCodeMap
                    .get(name)
                    .forEach(
                        dialCode -> {
                          csvFile.append("\n");
                          csvFile
                              .append(name.split("<<<")[1])
                              .append(",")
                              .append(dialCode)
                              .append(",")
                              .append(dialcodeImageUrlMap.get(dialCode));
                        });
              });
      FileUtils.writeStringToFile(file, csvFile.toString());
    } catch (IOException e) {
      logger.error(requestContext, "QRCodeDownloadManagement:createCSVFile: Exception occurred with error message = "
                      + e.getMessage(), e);
    }
    return file;
  }

  /**
   * Uploading the generated csv to aws
   *
   *
   * @param requestContext
   * @param file
   * @return
   */
  private Response uploadFile(RequestContext requestContext, File file) {
    String objectKey =
        getConfigValue(CLOUD_FOLDER_CONTENT)
            + separator
            + "textbook"
            + separator
            + "toc"
            + separator;
    Response response = new Response();
    try {
      if (file.isFile()) {
        objectKey += file.getName();
        //CSP related changes
        String cloudStorage = getConfigValue(CONTENT_CLOUD_STORAGE_TYPE);
        if (cloudStorage == null) {
          ProjectCommonException.throwClientErrorException(
                  ResponseCode.errorUnsupportedCloudStorage,
                  ProjectUtil.formatMessage(
                          ResponseCode.errorUnsupportedCloudStorage.getErrorMessage()));
          return null;
        }
        String fileUrl =
                CloudStorageUtil.upload(cloudStorage,
                        getConfigValue(CONTENT_CLOUD_STORAGE_CONTAINER),
                        objectKey,
                        file.getAbsolutePath());
        if (StringUtils.isBlank(fileUrl))
          throw new ProjectCommonException(
              ResponseCode.errorUploadQRCodeCSVfailed.getErrorCode(),
              ResponseCode.errorUploadQRCodeCSVfailed.getErrorMessage(),
              ResponseCode.SERVER_ERROR.getResponseCode());
        response.put("fileUrl", fileUrl);
      }
      return response;
    } catch (Exception e) {
      logger.error(requestContext, "QRCodeDownloadManagement:uploadFile: Exception occurred with error message = "
                      + e.getMessage(), e);
      throw e;
    } finally {
      FileUtils.deleteQuietly(file);
    }
  }
}
