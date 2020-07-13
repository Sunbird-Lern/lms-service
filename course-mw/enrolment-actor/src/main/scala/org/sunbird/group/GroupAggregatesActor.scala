package org.sunbird.group


import java.util
import java.util.{List, Map}

import com.fasterxml.jackson.databind.ObjectMapper
import com.mashape.unirest.http.Unirest
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.JsonKey
import org.sunbird.common.models.util.LoggerEnum
import org.sunbird.common.models.util.ProjectLogger
import org.sunbird.common.request.HeaderParam
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.actor.base.BaseActor

import org.sunbird.common.models.util.ProjectUtil.getConfigValue

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class GroupAggregatesActor extends BaseActor {

  private val GROUP_SERVICE_API_BASE_URL = if (StringUtils.isNotBlank(getConfigValue("sunbird_group_service_api_base_url"))) getConfigValue("sunbird_group_service_api_base_url")
  else "https://dev.sunbirded.org/api"

  private val mapper = new ObjectMapper

  @throws[Throwable]
  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "groupActivityAggregates" => getGroupActivityAggregates(request)
      case _ => ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData,
        ResponseCode.invalidRequestData.getErrorMessage)
    }
  }

  def getGroupActivityAggregates(request: Request): Unit = {
    val groupId: String = request.get(SunbirdKey.GROUPID).asInstanceOf[String]
    val activityId: String = request.get(SunbirdKey.ACTIVITYID).asInstanceOf[String]
    val activityType: String = request.get(SunbirdKey.ACTIVITYTYPE).asInstanceOf[String]

    try {
      val memberList: List[String] = getGroupMember(groupId, request)
      val enrolledGroupMember: List[util.Map[String, AnyRef]] = getEnrolledGroupMembers(activityId, activityType, memberList)
      sender().tell(populateResponse(groupId, activityId, activityType, enrolledGroupMember), self)
    } catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:: Exception thrown:: " + e)
        throw e
    }
  }

  def getGroupMember(groupId: String, request: Request): List[String] = {
    val requestUrl = GROUP_SERVICE_API_BASE_URL + "/v1/group/read/" + groupId + "?fields=members"
    val headers = new util.HashMap[String, String]() {{
      put(SunbirdKey.CONTENT_TYPE_HEADER, SunbirdKey.APPLICATION_JSON)
      put("x-authenticated-user-token", request.getContext.get(JsonKey.HEADER).asInstanceOf[Map[String, String]].get(HeaderParam.X_Authenticated_User_Token.getName))
    }}
    try{
      ProjectLogger.log("GroupAggregatesActor:getGroupActivityAggregates : Read request group : " + request.get(SunbirdKey.GROUPID), LoggerEnum.INFO.name)
      val groupResponse = Unirest.get(requestUrl).headers(headers).asString

      if ( null== groupResponse && groupResponse.getStatus != ResponseCode.OK.getResponseCode)
        ProjectCommonException.throwClientErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group members record.")

      val readResponse = mapper.readValue(groupResponse.getBody, classOf[Response])
      val members: util.List[util.Map[String, AnyRef]] = readResponse.get("members").asInstanceOf[util.List[util.Map[String, AnyRef]]]

      if (CollectionUtils.isEmpty(members))
        ProjectCommonException.throwClientErrorException(ResponseCode.missingData, "No member found in this group.")

      members.asScala.toList.map(obj => obj.getOrDefault("userId", "").asInstanceOf[String]).filter(x => StringUtils.isNotBlank(x))
    }catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getGroupMember:: Exception thrown:: " + e)
        throw e
    }
  }

  def getEnrolledGroupMembers(activityId: String, activityType: String, memberList: List[String]): List[util.Map[String, AnyRef]]= {
    try {
      val userActivityDBResponse = GroupDaoImpl.read(activityId, activityType)
      if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
        ProjectCommonException.throwClientErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group activity record.")

      val userActivityList: util.List[util.Map[String, AnyRef]] = userActivityDBResponse.get(SunbirdKey.RESPONSE).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (CollectionUtils.isEmpty(userActivityList))
        ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No user enrolled to this activity.")

      val enrolledGroupMemberList: List[util.Map[String, AnyRef]] = userActivityList.asScala.toList.filter(obj => memberList.contains(obj.getOrDefault("user_id", "").asInstanceOf[String]))
      if (null == enrolledGroupMemberList || enrolledGroupMemberList.isEmpty)
        ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No group members enrolled to this activity.")

      enrolledGroupMemberList
    } catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getEnrolledGroupMembers:: Exception thrown:: " + e)
        throw e
    }
  }


  def populateResponse(groupId: String, activityId: String, activityType: String, enrolledGroupMember: List[util.Map[String, AnyRef]]): Response= {
    val response: Response = new Response()

    response.put("groupId", groupId)
    response.put("activity", new util.HashMap[String, AnyRef](){{
      put("id", activityId)
      put("type", activityType)
      put("agg", util.Arrays.asList(new util.HashMap[String, AnyRef](){{
        put("metric", "enrolmentCount")
        put("lastUpdatedOn", System.currentTimeMillis().toString)
        put("value", enrolledGroupMember.size.asInstanceOf[AnyRef])
      }}))
    }})

    val membersList = new util.ArrayList[util.Map[String, AnyRef]]
    for(member <- enrolledGroupMember){
      membersList.add(new util.HashMap[String, AnyRef]() {{
        put("id", member.get("user_id"))
        put("agg", util.Arrays.asList(new util.HashMap[String, AnyRef]() {{
          put("metric", "completedCount")
          val aggLastUpdated = member.get("agg_last_updated").asInstanceOf[util.Map[String, AnyRef]]
          if(MapUtils.isNotEmpty(aggLastUpdated))
            put("lastUpdatedOn", aggLastUpdated.get("completedCount"))
          val agg = member.get("agg").asInstanceOf[util.Map[String, AnyRef]]
          if(MapUtils.isNotEmpty(agg))
            put("value", agg.get("completedCount"))
        }}))
      }})
    }
    response.put("members", membersList)
    response
  }
}

