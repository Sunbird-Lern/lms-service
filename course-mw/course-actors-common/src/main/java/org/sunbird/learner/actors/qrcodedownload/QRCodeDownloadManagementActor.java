package org.sunbird.learner.actors.qrcodedownload;

import static java.io.File.separator;
import static org.sunbird.common.models.util.JsonKey.CLOUD_FOLDER_CONTENT;
import static org.sunbird.common.models.util.JsonKey.CONTENT_AZURE_STORAGE_CONTAINER;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.CloudStorageUtil;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.ContentSearchUtil;
import org.sunbird.learner.util.Util;

/**
 * @Author : Rhea Fernandes This actor is used to create an html file for all the qr code images
 * that are linked to courses that are created userIds given
 */
public class QRCodeDownloadManagementActor extends BaseActor {
  private static final List<String> fields = Arrays.asList("identifier", JsonKey.DIAL_CODES, "name");
  private static final Map<String, String> filtersHelperMap = new HashMap<>();
  private static Util.DbInfo courseDialCodeInfo =
      Util.dbInfoMap.get(JsonKey.SUNBIRD_COURSE_DIALCODES_DB);
  private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();

  static{
    filtersHelperMap.put(JsonKey.USER_IDs, JsonKey.CREATED_BY);;
    filtersHelperMap.put(JsonKey.STATUS, JsonKey.STATUS);
    filtersHelperMap.put(JsonKey.CONTENT_TYPE, JsonKey.CONTENT_TYPE);
  }
  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.QR_CODE_DOWNLOAD);
    ExecutionContext.setRequestId(request.getRequestId());
    String requestedOperation = request.getOperation();
    if(requestedOperation.equals("downloadQRCodes")){
      downloadQRCodes(request);
    }
    else{
      onReceiveUnsupportedOperation(requestedOperation);
    }
  }

  /**
   * The request must contain list of userIds (Users Ids of people who have created courses)
   *
   * @param request
   */
  private void downloadQRCodes(Request request) {
    Map<String, String> headers = (Map<String, String>) request.getRequest().get(JsonKey.HEADER);
    Map<String, Object> requestMap = (Map<String, Object>) request.getRequest().get(JsonKey.FILTER);
    requestMap.put(JsonKey.CONTENT_TYPE, "course");
    Map<String, Object> searchResponse = searchCourses(requestMap, headers);
    List<Map<String, Object>> contents = (List<Map<String, Object>>) searchResponse.get("contents");
    if (CollectionUtils.isEmpty(contents))
      throw new ProjectCommonException(
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorCode(),
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    Map<String, List<String>> dialCodesMap =
        contents
            .stream()
            .filter(content -> content.get(JsonKey.DIAL_CODES) != null)
            .filter(content -> content.get("name") != null)
            .collect(
                Collectors.toMap(
                    content ->
                        ((String) content.get("identifier")) + "<<<" + (String) content.get("name"),
                    content -> (List) content.get(JsonKey.DIAL_CODES)));
    File file = generateCSVFile(dialCodesMap);
    if (null == file)
      throw new ProjectCommonException(
          ResponseCode.errorProcessingFile.getErrorCode(),
          ResponseCode.errorProcessingFile.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());

    Response response = uploadFile(file);
    sender().tell(response, self());
  }

  /**
   * Search call to Learning Platform composite search engine
   *
   * @param requestMap
   * @param headers
   * @return
   */
  private Map<String, Object> searchCourses(
      Map<String, Object> requestMap, Map<String, String> headers) {
    String request = prepareSearchRequest(requestMap);
    return ContentSearchUtil.searchContentSync(null, request, headers);
  }

  /**
   * Request Preparation for search Request for getting courses created by user and dialcodes linked
   * to them.
   *
   * @param requestMap
   * @return
   */
  private String prepareSearchRequest(Map<String, Object> requestMap) {
    Map<String, Object> searchRequestMap = new HashMap<>();
    searchRequestMap.put(JsonKey.FILTERS,
            requestMap
                    .keySet()
                    .stream()
                    .filter(key -> filtersHelperMap.containsKey(key))
                    .collect(
                            Collectors.toMap(
                                    key -> filtersHelperMap.get(key), key -> requestMap.get(key))));
    searchRequestMap.put(JsonKey.FIELDS, fields);
    searchRequestMap.put(JsonKey.EXISTS, JsonKey.DIAL_CODES);
    searchRequestMap.put(JsonKey.LIMIT, 200);
    Map<String, Object> request = new HashMap<>();
    request.put(JsonKey.REQUEST, searchRequestMap);
    String requestJson = null;
    try {
      requestJson = new ObjectMapper().writeValueAsString(request);
    } catch (JsonProcessingException e) {
      ProjectLogger.log(
          "QRCodeDownloadManagement:prepareSearchRequest: Exception occurred with error message = "
              + e.getMessage(),
          e);
    }
    return requestJson;
  }

  /**
   * Generates the CSV File for the data provided
   *
   * @param dialCodeMap
   * @return
   */
  private File generateCSVFile(Map<String, List<String>> dialCodeMap) {
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
                              .append(getQRCodeImageUrl(dialCode));
                        });
              });
      FileUtils.writeStringToFile(file, csvFile.toString(), Charset.defaultCharset(), false);
    } catch (IOException e) {
      ProjectLogger.log(
          "QRCodeDownloadManagement:createCSVFile: Exception occurred with error message = "
              + e.getMessage(),
          e);
    }
    return file;
  }

  /**
   * Fetch the QR code Url for the given dialcodes
   *
   * @param dialCode
   * @return
   */
  private String getQRCodeImageUrl(String dialCode) {
    // TODO: Dialcode as primary key in cassandra
    Response response =
        cassandraOperation.getRecordsByProperty(
            courseDialCodeInfo.getKeySpace(),
            courseDialCodeInfo.getTableName(),
            JsonKey.FILE_NAME,
            "0_" + dialCode,
            Arrays.asList("url"));
    if (null != response && response.get(JsonKey.RESPONSE) != null) {
      Object obj = response.get(JsonKey.RESPONSE);
      if (obj instanceof List) {
        List<Map<String, Object>> listOfMap = (List<Map<String, Object>>) obj;
        if (CollectionUtils.isNotEmpty(listOfMap)) {
          return (String) listOfMap.get(0).get("url");
        }
      }
    }
    return "";
  }

  /**
   * Uploading the generated csv to aws
   *
   * @param file
   * @return
   */
  private Response uploadFile(File file) {
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
        String fileUrl =
            CloudStorageUtil.upload(
                CloudStorageUtil.CloudStorageType.AZURE,
                getConfigValue(CONTENT_AZURE_STORAGE_CONTAINER),
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
      ProjectLogger.log(
          "QRCodeDownloadManagement:uploadFile: Exception occurred with error message = "
              + e.getMessage(),
          e);
      throw e;
    } finally {
      FileUtils.deleteQuietly(file);
    }
  }
}
