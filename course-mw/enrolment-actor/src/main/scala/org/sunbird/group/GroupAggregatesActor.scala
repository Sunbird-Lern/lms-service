package org.sunbird.group


import java.util

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{JsonKey, ProjectLogger}
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.actor.base.BaseActor
import org.sunbird.common.models.util.ProjectUtil.getConfigValue

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class GroupAggregatesActor extends BaseActor {


  private val GROUP_MEMBERS_METADATA = if (StringUtils.isNotBlank(getConfigValue(JsonKey.GROUP_MEMBERS_METADATA))) getConfigValue(JsonKey.GROUP_MEMBERS_METADATA).split(",")
  else util.Arrays.asList("name", "userId", "role", "status", "createdBy")

  var groupDao: GroupDaoImpl = new GroupDaoImpl()
  var groupAggregatesUtil: GroupAggregatesUtil = new GroupAggregatesUtil()

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
      val memberList: util.List[util.Map[String, AnyRef]] = getGroupMember(groupId, request)
      val enrolledGroupMember: util.List[util.Map[String, AnyRef]] = getEnrolledGroupMembers(activityId, activityType, memberList)
      sender().tell(populateResponse(groupId, activityId, activityType, enrolledGroupMember, memberList), self)
    } catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:: Exception thrown:: " + e)
        throw e
    }
  }

  def getGroupMember(groupId: String, request: Request): util.List[util.Map[String, AnyRef]] = {
    val readResponse = groupAggregatesUtil.getGroupDetails(groupId, request)
    val members: util.List[util.Map[String, AnyRef]] = readResponse.get("members").asInstanceOf[util.List[util.Map[String, AnyRef]]]

    if (CollectionUtils.isEmpty(members))
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No member found in this group.")

    members
  }

  def getEnrolledGroupMembers(activityId: String, activityType: String, memberList: util.List[util.Map[String, AnyRef]]): util.List[util.Map[String, AnyRef]]= {
    val userList: util.List[String] = memberList.asScala.toList.map(obj => obj.getOrDefault("userId", "").asInstanceOf[String]).filter(x => StringUtils.isNotBlank(x)).asJava
    val userActivityDBResponse = groupDao.read(activityId, activityType, userList)
    if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
      ProjectCommonException.throwClientErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group activity record.")

    val enrolledGroupMemberList: util.List[util.Map[String, AnyRef]] = userActivityDBResponse.get(SunbirdKey.RESPONSE).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isEmpty(enrolledGroupMemberList))
      ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No user enrolled to this activity.")

    enrolledGroupMemberList
  }


  def populateResponse(groupId: String, activityId: String, activityType: String, enrolledGroupMember: util.List[util.Map[String, AnyRef]], memberList: util.List[util.Map[String, AnyRef]]): Response= {

    val memberMap:util.Map[String, util.Map[String, AnyRef]] = memberList.asScala.toList.filter(x=>StringUtils.isNotBlank(x.getOrDefault("userId", "").asInstanceOf[String])).map(obj => (obj.getOrDefault("userId", "").asInstanceOf[String], obj)).toMap.asJava
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
        for(metadata <- GROUP_MEMBERS_METADATA){
          put(metadata, memberMap.get(member.get("user_id")).get(metadata))
        }

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

  def setInsranceVariable(groupAggregateUtil: GroupAggregatesUtil, groupDao: GroupDaoImpl) = {
    this.groupAggregatesUtil = groupAggregateUtil
    this.groupDao = groupDao
    this
  }
}

