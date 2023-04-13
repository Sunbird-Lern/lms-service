package org.sunbird.collectionhierarchy.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mashape.unirest.http.Unirest
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{JsonKey, LoggerUtil}
import org.sunbird.common.responsecode.ResponseCode
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.sunbird.common.models.util.JsonKey.BEARER
import org.sunbird.common.models.util.JsonKey.EKSTEP_BASE_URL
import org.sunbird.common.models.util.JsonKey.SUNBIRD_AUTHORIZATION
import org.sunbird.common.models.util.ProjectUtil.getConfigValue
import org.sunbird.common.request.{HeaderParam, Request}
import org.sunbird.common.responsecode.ResponseCode.SERVER_ERROR
import org.sunbird.common.responsecode.ResponseCode.errorProcessingRequest
import org.sunbird.keys.SunbirdKey

import java.util
import scala.collection.JavaConverters._
import java.io.IOException
import java.text.MessageFormat
import scala.collection.immutable.{HashMap, Map}
import scala.collection.JavaConversions.mapAsJavaMap

object CollectionTOCUtil {

  val logger: LoggerUtil = new LoggerUtil(CollectionTOCUtil.getClass)

  @transient val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def getRelatedFrameworkById(frameworkId: String, request: Request): Response = {
    logger.info(request.getRequestContext, "CollectionTocUtil::getRelatedFrameworkById: frameworkId = " + frameworkId)
    val requestParams: Map[String, String] = HashMap[String, String]("categories" -> "topic")
    handleReadRequest(frameworkId, JsonKey.LEARNING_SERVICE_BASE_URL, JsonKey.FRAMEWORK_READ_API_URL, requestParams, request: Request)
  }

  private def requestParams(params: Map[String, String]): String = {
    if (null != params) {
      val sb: StringBuilder = new StringBuilder()
      sb.append("?")
      var i: Int = 0
      for ((key, value) <- params) {
        if ({ i += 1; i - 1 } > 1) {
          sb.append("&")
        }
        sb.append(key).append("=").append(value)
      }
      sb.toString
    } else {
      ""
    }
  }

  def readContent(contentId: String, url: String, request: Request): Response = {
    logger.info(request.getRequestContext, "CollectionTocUtil::readContent: contentId = " + contentId)
    val requestParams: Map[String, String] =  HashMap[String, String]("mode" -> "edit")
    handleReadRequest(contentId, "", url, requestParams, request)
  }

  private def handleReadRequest(id: String, basePath: String, urlPath: String, reqParams: Map[String, String], request: Request):Response = {
    try {
      val headers = new util.HashMap[String, String]() {
        put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON)
        put(AUTHORIZATION, BEARER + getConfigValue(SUNBIRD_AUTHORIZATION))
      }

      val requestUrl = {
        if (basePath.isBlank)
          getConfigValue(EKSTEP_BASE_URL) + getConfigValue(urlPath) + "/" + id + requestParams(reqParams)
        else
          getConfigValue(basePath) + getConfigValue(urlPath) + "/" + id + requestParams(reqParams)
      }

      logger.info(request.getRequestContext, "CollectionTocUtil:handleReadRequest: Sending GET Request | Collection Id: " + id +
        ", Request URL: " + requestUrl)
      val httpResponse = Unirest.get(requestUrl).headers(headers).asString
      logger.info(request.getRequestContext, "CollectionTOCUtil:handleReadRequest : httpResponse.getStatus : " + httpResponse.getStatus)
      if ( null== httpResponse || httpResponse.getStatus != ResponseCode.OK.getResponseCode) {
        logger.info(request.getRequestContext, "CollectionTOCUtil:handleReadRequest : httpResponse.getBody : " + httpResponse.getBody)
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while fetching content data.")
      }
      mapper.readValue(httpResponse.getBody, classOf[Response])

    } catch {
      case e: Exception =>
        logger.error(request.getRequestContext, "CollectionTOCUtil:handleReadRequest:: Exception thrown:: " , e)
        throw e
    }
  }

  def getObjectFrom[T](s: String, clazz: Class[T], request: Request): T = {
    if (s.isEmpty) {
      logger.error(request.getRequestContext, "Invalid String cannot be converted to Map.", null)
      throw new ProjectCommonException(errorProcessingRequest.getErrorCode, errorProcessingRequest.getErrorMessage, SERVER_ERROR.getResponseCode)
    }
    try mapper.readValue(s, clazz)
    catch {
      case e: IOException =>
        logger.error(request.getRequestContext, "Error Mapping File input Mapping Properties.", e)
        throw new ProjectCommonException(errorProcessingRequest.getErrorCode, errorProcessingRequest.getErrorMessage, SERVER_ERROR.getResponseCode)
    }
  }

  def serialize[T](o: T): String = try mapper.writeValueAsString(o)
  catch {
    case e: JsonProcessingException =>
      logger.error(null, "Error Serializing Object To String", e)
      throw new ProjectCommonException(errorProcessingRequest.getErrorCode, errorProcessingRequest.getErrorMessage, SERVER_ERROR.getResponseCode)
  }

  def validateDialCodes(channelId: String, dialcodes: List[String], request: Request): List[String] = {
      val reqMap = new util.HashMap[String, AnyRef]() {
        {
          put(JsonKey.REQUEST, new util.HashMap[String, AnyRef]() {
            {
              put(JsonKey.SEARCH, new util.HashMap[String, AnyRef]() {
                {
                  put(JsonKey.IDENTIFIER, dialcodes.distinct.asJava)
                }
              })
            }
          })
        }
      }

    val headerParam = HashMap[String, String](JsonKey.X_CHANNEL_ID -> channelId, JsonKey.AUTHORIZATION -> (BEARER + getConfigValue(SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = getConfigValue(JsonKey.SUNBIRD_CS_BASE_URL) + getConfigValue(JsonKey.SUNBIRD_DIALCODE_SEARCH_API)
    logger.info(request.getRequestContext, "CollectionTOCUtil:validateDialCodes : reqMap : " + reqMap)
    logger.info(request.getRequestContext, "CollectionTOCUtil:validateDialCodes : requestUrl : " + requestUrl)

    val searchResponse = Unirest.post(requestUrl).headers(headerParam).body(mapper.writeValueAsString(reqMap)).asString

    logger.info(request.getRequestContext, "CollectionTOCUtil:validateDialCodes : searchResponse : " + searchResponse)
    logger.info(request.getRequestContext, "CollectionTOCUtil:validateDialCodes : searchResponse.getStatus : " + searchResponse.getStatus)

    if (null == searchResponse || searchResponse.getStatus != ResponseCode.OK.getResponseCode) {
      logger.info(request.getRequestContext, "CollectionTOCUtil:validateDialCodes : searchResponse.getBody : " + searchResponse.getBody)
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while fetching DIAL Codes List.")
    }

    val response = mapper.readValue(searchResponse.getBody, classOf[Response])

    try {
      response.getResult.getOrDefault(JsonKey.DIALCODES, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]].map(_.getOrElse(JsonKey.IDENTIFIER, "")).asInstanceOf[List[String]]
    }
    catch {
      case _:Exception =>
        List.empty
    }
  }

  def searchLinkedContents(linkedContents: List[String], request: Request): List[Map[String, AnyRef]] = {

    val reqMap = new util.HashMap[String, AnyRef]() {
      {
        put(JsonKey.REQUEST, new util.HashMap[String, AnyRef]() {
          {
            put(JsonKey.FILTERS, new util.HashMap[String, AnyRef]() {
                put(JsonKey.IDENTIFIER, linkedContents.distinct.asJava)
            })
            put(JsonKey.LIMIT, linkedContents.size.asInstanceOf[AnyRef])
          }
        })
      }
    }

    val headerParam = HashMap[String, String](JsonKey.AUTHORIZATION -> (BEARER + getConfigValue(SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = getConfigValue(JsonKey.SUNBIRD_CS_BASE_URL) + getConfigValue(JsonKey.SUNBIRD_CONTENT_SEARCH_URL)

    logger.info(request.getRequestContext, "CollectionTOCUtil -> searchLinkedContents --> requestUrl: " + requestUrl)
    logger.info(request.getRequestContext, "CollectionTOCUtil -> searchLinkedContents --> headerParam: " + headerParam)
    logger.info(request.getRequestContext, "CollectionTOCUtil -> searchLinkedContents --> reqMap: " + reqMap)

    val searchResponse = Unirest.post(requestUrl).headers(headerParam).body(mapper.writeValueAsString(reqMap)).asString
    logger.info(request.getRequestContext, "CollectionTOCUtil -> searchLinkedContents --> searchResponse.getStatus: " + searchResponse.getStatus)
    if (null == searchResponse || searchResponse.getStatus != ResponseCode.OK.getResponseCode) {
      logger.info(request.getRequestContext, "CollectionTOCUtil:searchLinkedContents : searchResponse.getBody : " + searchResponse.getBody)
      ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while fetching Linked Contents List.")

    }

    val response = mapper.readValue(searchResponse.getBody, classOf[Response])
    try {
      response.getResult.getOrDefault(JsonKey.CONTENT, new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]]
    }
    catch {
      case _:Exception =>
        List.empty
    }
  }

  def updateHierarchy(createHierarchyRequestObj: String, request: Request): Response = {

    val authToken = request.getContext.get(JsonKey.HEADER).asInstanceOf[util.Map[String, String]].get(HeaderParam.X_Authenticated_User_Token.getName)
    val headerParam = HashMap[String, String](JsonKey.AUTHORIZATION -> (BEARER + getConfigValue(SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json", JsonKey.X_AUTHENTICATED_USER_TOKEN -> authToken)
    val requestUrl = getConfigValue(JsonKey.EKSTEP_BASE_URL) + getConfigValue(JsonKey.UPDATE_HIERARCHY_API)

    logger.info(request.getRequestContext, "CollectionTOCUtil -> updateHierarchy --> requestUrl: " + requestUrl)
    logger.info(request.getRequestContext, "CollectionTOCUtil -> updateHierarchy --> headerParam: " + headerParam)
    logger.info(request.getRequestContext, "CollectionTOCUtil -> updateHierarchy --> reqMap: " + createHierarchyRequestObj)

    val updateResponse = Unirest.patch(requestUrl).headers(headerParam).body(createHierarchyRequestObj).asString
    logger.info(request.getRequestContext, "CollectionTOCUtil -> updateHierarchy --> updateResponse.getStatus: " + updateResponse.getStatus)

    val response = mapper.readValue(updateResponse.getBody, classOf[Response])
    logger.info(request.getRequestContext, "CollectionTOCUtil -> updateHierarchy -->response.getResult: " + response.getResult)

    if (null == updateResponse || updateResponse.getStatus != ResponseCode.OK.getResponseCode) {
      logger.info(request.getRequestContext, "CollectionTOCUtil:updateHierarchy : updateResponse.getBody : " + updateResponse.getBody)

      if(updateResponse.getStatus == 400) {
        val msgsResult = response.getResult.getOrDefault(JsonKey.TB_MESSAGES, new util.ArrayList[String]())
        throw new ProjectCommonException(ResponseCode.collectionUpdateError.getErrorCode, MessageFormat.format(ResponseCode.collectionUpdateError.getErrorMessage, msgsResult), ResponseCode.CLIENT_ERROR.getResponseCode)
      } else {
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while updating collection hierarchy.")
      }
    }
    response
  }

  def linkDIALCode(channelId: String, collectionID: String, linkDIALCodesMap: List[Map[String,String]], request: Request): Response = {

    val reqMap = new util.HashMap[String, AnyRef]() {
      {
        put(JsonKey.REQUEST, new util.HashMap[String, AnyRef]() {
          {
            put(JsonKey.CONTENT, linkDIALCodesMap.asJava)
          }
        })
      }
    }

    val headerParam = HashMap[String, String](JsonKey.X_CHANNEL_ID -> channelId, JsonKey.AUTHORIZATION -> (BEARER + getConfigValue(SUNBIRD_AUTHORIZATION)), "Content-Type" -> "application/json")
    val requestUrl = getConfigValue(JsonKey.LEARNING_SERVICE_BASE_URL) + getConfigValue(JsonKey.LINK_DIAL_CODE_API) + "/" + collectionID

    logger.info(request.getRequestContext,"CollectionTOCUtil -> linkDIALCode --> requestUrl: " + requestUrl)
    logger.info(request.getRequestContext,"CollectionTOCUtil -> linkDIALCode --> headerParam: " + headerParam)
    logger.info(request.getRequestContext,"CollectionTOCUtil -> linkDIALCode --> reqMap: " + mapper.writeValueAsString(reqMap))

    val linkResponse = Unirest.post(requestUrl).headers(headerParam).body(mapper.writeValueAsString(reqMap)).asString

    val response = mapper.readValue(linkResponse.getBody, classOf[Response])
    logger.info(request.getRequestContext, "CollectionTOCUtil:updateHierarchy : linkDIALCode.getStatus : " + linkResponse.getStatus)
    if (null == linkResponse || linkResponse.getStatus != ResponseCode.OK.getResponseCode) {
      logger.info(request.getRequestContext, "CollectionTOCUtil:updateHierarchy : linkDIALCode.getBody : " + linkResponse.getBody)

      if(linkResponse.getStatus == 400) {
        val msgsResult = response.getResult.getOrDefault(JsonKey.TB_MESSAGES, new util.ArrayList[String])
        throw new ProjectCommonException(ResponseCode.errorDialCodeLinkingClientError.getErrorCode, MessageFormat.format(ResponseCode.errorDialCodeLinkingClientError.getErrorMessage, msgsResult), ResponseCode.CLIENT_ERROR.getResponseCode)
      } else {
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while updating collection hierarchy.")
      }
    }
    response
  }

}
