package org.sunbird.group

import java.util
import java.util.Map

import com.fasterxml.jackson.databind.ObjectMapper
import com.mashape.unirest.http.Unirest
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil.getConfigValue
import org.sunbird.common.models.util.{JsonKey, LoggerEnum, ProjectLogger}
import org.sunbird.common.request.{HeaderParam, Request}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.keys.SunbirdKey

class GroupAggregatesUtil {

  private val GROUP_SERVICE_API_BASE_URL = if (StringUtils.isNotBlank(getConfigValue(JsonKey.GROUP_SERVICE_API_BASE_URL))) getConfigValue(JsonKey.GROUP_SERVICE_API_BASE_URL)+"/private/v1/group/read/"
  else "https://dev.sunbirded.org/api/group/v1/read/"

  private val mapper = new ObjectMapper

  def getGroupDetails(groupId: String, request: Request): Response = {
    try{
      val requestUrl = GROUP_SERVICE_API_BASE_URL + groupId + "?fields=members"
      val authToken = request.getContext.get(JsonKey.HEADER).asInstanceOf[Map[String, String]].get(HeaderParam.X_Authenticated_User_Token.getName)
      ProjectLogger.log("GroupAggregatesActor:getGroupDetails : Token Size: " + StringUtils.length(authToken))
      val headers = new util.HashMap[String, String]() {{
        put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON)
        put("x-authenticated-user-token", authToken)
      }}

      ProjectLogger.log("GroupAggregatesActor:getGroupDetails : Read request group : " + request.get(SunbirdKey.GROUPID), LoggerEnum.INFO.name)
      val groupResponse = Unirest.get(requestUrl).headers(headers).asString
      ProjectLogger.log("GroupAggregatesActor:getGroupDetails : groupResponse.getBody : " + groupResponse.getBody, LoggerEnum.INFO.name)
      ProjectLogger.log("GroupAggregatesActor:getGroupDetails : groupResponse.getStatus : " + groupResponse.getStatus, LoggerEnum.INFO.name)
      ProjectLogger.log("GroupAggregatesActor:getGroupDetails : groupResponse.getStatusText : " + groupResponse.getStatusText, LoggerEnum.INFO.name)

      if ( null== groupResponse || groupResponse.getStatus != ResponseCode.OK.getResponseCode){
        ProjectLogger.log("GroupAggregatesActor:getGroupDetails:NOT_OK : groupResponse.getStatus : " + groupResponse.getStatus, LoggerEnum.INFO.name)
        ProjectCommonException.throwServerErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group members record.")
      }

      mapper.readValue(groupResponse.getBody, classOf[Response])

    }catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesUtil:getGroupDetails:: Exception thrown:: " + e)
        throw e
    }
  }
}
