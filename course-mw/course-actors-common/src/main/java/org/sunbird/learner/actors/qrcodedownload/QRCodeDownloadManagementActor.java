package org.sunbird.learner.actors.qrcodedownload;

import static java.io.File.separator;
import static org.sunbird.common.models.util.JsonKey.*;
import static org.sunbird.common.models.util.ProjectUtil.getConfigValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
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
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.common.util.CloudStorageUtil;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.util.ContentSearchUtil;
import org.sunbird.learner.util.Util;

/**
 * @Author : Rhea Fernandes This actor is used to create an html file for all the qr code images
 * that are linked to courses that are created userIds given
 */
public class QRCodeDownloadManagementActor extends BaseActor {
  private static final List<String> fields = Arrays.asList("identifier", "dialcodes", "name");
  private static final Map<String, String> filtersHelperMap =
      new HashMap<String, String>() {
        {
          put(JsonKey.USER_IDs, JsonKey.CREATED_BY);
          put(JsonKey.STATUS, JsonKey.STATUS);
          put(JsonKey.CONTENT_TYPE, JsonKey.CONTENT_TYPE);
        }
      };
  private static Util.DbInfo courseDialCodeInfo =
      Util.dbInfoMap.get(JsonKey.SUNBIRD_COURSE_DIALCODES_DB);
  private static int SEARCH_CONTENTS_LIMIT = Integer.parseInt(StringUtils.isNotBlank(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_QRCODE_COURSES_LIMIT)) ? ProjectUtil.getConfigValue(JsonKey.SUNBIRD_QRCODE_COURSES_LIMIT) : "2000");

  private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();

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
  private void downloadQRCodes(Request request) {
    Map<String, String> headers = (Map<String, String>) request.getRequest().get(JsonKey.HEADER);
    Map<String, Object> requestMap = (Map<String, Object>) request.getRequest().get(JsonKey.FILTER);
    requestMap.put(JsonKey.CONTENT_TYPE, "course");
    Map<String, Object> searchResponse = searchCourses(request.getRequestContext(), requestMap, headers);
    List<Map<String, Object>> contents = (List<Map<String, Object>>) searchResponse.get("contents");
    if (CollectionUtils.isEmpty(contents))
      throw new ProjectCommonException(
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorCode(),
          ResponseCode.errorUserHasNotCreatedAnyCourse.getErrorMessage(),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    Map<String, List<String>> dialCodesMap =
        contents
            .stream()
            .filter(content -> content.get("dialcodes") != null)
            .filter(content -> content.get("name") != null)
            .collect(
                Collectors.toMap(
                    content ->
                        ((String) content.get("identifier")) + "<<<" + (String) content.get("name"),
                    content -> (List<String>) content.get("dialcodes"), (a,b) -> b, LinkedHashMap::new));
    File file = generateCSVFile(request.getRequestContext(), dialCodesMap);
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
   * Search call to Learning Platform composite search engine
   *
   *
   * @param requestContext
   * @param requestMap
   * @param headers
   * @return
   */
  private Map<String, Object> searchCourses(
          RequestContext requestContext, Map<String, Object> requestMap, Map<String, String> headers) {
    String request = prepareSearchRequest (requestContext, requestMap);
    Map<String, Object> searchResponse =
        ContentSearchUtil.searchContentSync(requestContext, null, request, headers);
    return searchResponse;
  }

  /**
   * Request Preparation for search Request for getting courses created by user and dialcodes linked
   * to them.
   *
   *
   * @param requestContext
   * @param requestMap
   * @return
   */
  private String prepareSearchRequest(RequestContext requestContext, Map<String, Object> requestMap) {
    Map<String, Object> searchRequestMap =
        new HashMap<String, Object>() {
          {
            put(
                JsonKey.FILTERS,
                requestMap
                    .keySet()
                    .stream()
                    .filter(key -> filtersHelperMap.containsKey(key))
                    .collect(
                        Collectors.toMap(
                            key -> filtersHelperMap.get(key), key -> requestMap.get(key))));
            put(JsonKey.FIELDS, fields);
            put(JsonKey.EXISTS, JsonKey.DIAL_CODES);
            put(JsonKey.SORT_BY, new HashMap<String, String>() {{
              put(SunbirdKey.LAST_PUBLISHED_ON, JsonKey.DESC);
            }});
            //TODO: Limit should come from request, need to facilitate this change.
            put(JsonKey.LIMIT, SEARCH_CONTENTS_LIMIT);
          }
        };
    Map<String, Object> request =
        new HashMap<String, Object>() {
          {
            put(JsonKey.REQUEST, searchRequestMap);
          }
        };
    String requestJson = null;
    try {
      requestJson = new ObjectMapper().writeValueAsString(request);
    } catch (JsonProcessingException e) {
      logger.error(requestContext, "QRCodeDownloadManagement:prepareSearchRequest: Exception occurred with error message = "
                      + e.getMessage(), e);
    }
    return requestJson;
  }

  /**
   * Generates the CSV File for the data provided
   *
   * @param requestContext
   * @param dialCodeMap
   * @return
   */
  private File generateCSVFile(RequestContext requestContext, Map<String, List<String>> dialCodeMap) {
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
                              .append(getQRCodeImageUrl(requestContext, dialCode));
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
   * Fetch the QR code Url for the given dialcodes
   *
   * @param requestContext
   * @param dialCode
   * @return
   */
  private String getQRCodeImageUrl(RequestContext requestContext, String dialCode) {
    // TODO: Dialcode as primary key in cassandra
    Response response =
        cassandraOperation.getRecordsByProperty(
                requestContext, courseDialCodeInfo.getKeySpace(),
            courseDialCodeInfo.getTableName(),
            JsonKey.FILE_NAME,
            "0_" + dialCode,
            Arrays.asList("url"));
    if (null != response && response.get(JsonKey.RESPONSE) != null) {
      Object obj = response.get(JsonKey.RESPONSE);
      if (null != obj && obj instanceof List) {
        List<Map<String, Object>> listOfMap = (List<Map<String, Object>>) obj;
        if (CollectionUtils.isNotEmpty(listOfMap)) {
          //TODO Resolve it and store. Assuming dial code db will have the template url with data migration script

          //replace template url with the actual cloud url
          String templateUrl = (String) listOfMap.get(0).get("url");
          if (templateUrl.contains(getConfigValue(DIAL_STORAGE_BASE_PATH_PLACEHOLDER)))
            templateUrl = templateUrl.replace(getConfigValue(DIAL_STORAGE_BASE_PATH_PLACEHOLDER),
                    getConfigValue(CLOUDSTORAGE_BASE_PATH)+"/"+
                    getConfigValue(CLOUD_STORAGE_DIAL_BUCKET_NAME));
          return templateUrl;
        }
      }
    }
    return "";
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
