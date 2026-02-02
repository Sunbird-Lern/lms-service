package org.sunbird.group

import java.util
import java.util.Map

import com.fasterxml.jackson.databind.ObjectMapper
import com.mashape.unirest.http.Unirest
import org.apache.commons.lang3.StringUtils
import org.sunbird.exception.ProjectCommonException
import org.sunbird.response.Response
import org.sunbird.common.ProjectUtil.getConfigValue
import org.sunbird.keys.JsonKey
import org.sunbird.logging.{LoggerEnum, LoggerUtil, ProjectLogger}
import org.sunbird.request.{HeaderParam, Request}
import org.sunbird.response.ResponseCode
import org.sunbird.keys.SunbirdKey

class GroupAggregatesUtil {

  private val GROUP_SERVICE_API_BASE_URL = if (StringUtils.isNotBlank(getConfigValue(JsonKey.GROUP_SERVICE_API_BASE_URL))) getConfigValue(JsonKey.GROUP_SERVICE_API_BASE_URL)+"/private/v1/group/read/"
  else "https://dev.sunbirded.org/api/group/v1/read/"

  private val mapper = new ObjectMapper
  private val logger = new LoggerUtil(classOf[GroupAggregatesUtil])

  def getGroupDetails(groupId: String, request: Request): Response = {
    try{
      val requestUrl = GROUP_SERVICE_API_BASE_URL + groupId + "?fields=members"
      val authToken = request.getContext.get(JsonKey.HEADER).asInstanceOf[Map[String, String]].get(HeaderParam.X_Authenticated_User_Token.getName)
      logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails : Token Size: " + StringUtils.length(authToken))
      val headers = new util.HashMap[String, String]() {{
        put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON)
        put("x-authenticated-user-token", authToken)
      }}

      logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails : Read request group : " + request.get(SunbirdKey.GROUPID))
      val groupResponse = Unirest.get(requestUrl).headers(headers).asString

      if ( null== groupResponse || groupResponse.getStatus != ResponseCode.OK.getResponseCode) {
        logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails : groupResponse.getBody : " + groupResponse.getBody)
        logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails : groupResponse.getStatus : " + groupResponse.getStatus)
        logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails : groupResponse.getStatusText : " + groupResponse.getStatusText)
        logger.info(request.getRequestContext, "GroupAggregatesActor:getGroupDetails:NOT_OK : groupResponse.getStatus : " + groupResponse.getStatus)
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group members record.")
      }

      mapper.readValue(groupResponse.getBody, classOf[Response])

    }catch {
      case e: Exception =>
        logger.error(request.getRequestContext, "GroupAggregatesUtil:getGroupDetails:: Exception thrown:: " , e)
        throw e
    }
  }
}
