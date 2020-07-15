package org.sunbird.group


import java.util
import java.util.List

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{ProjectLogger, PropertiesCache}
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.actor.base.BaseActor
import org.sunbird.cache.CacheFactory
import org.sunbird.cache.interfaces.Cache
import org.sunbird.redis.RedisCache

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class GroupAggregatesActor extends BaseActor {

  private val GROUP_MEMBERS_METADATA: List[String] = util.Arrays.asList("name", "userId", "role", "status", "createdBy")

  var groupDao: GroupDaoImpl = new GroupDaoImpl()
  var groupAggregatesUtil: GroupAggregatesUtil = new GroupAggregatesUtil()
  var redisCache: Cache = CacheFactory.getInstance()
  val ttl: Long = 3600

  @throws[Throwable]
  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "groupActivityAggregates" => getGroupActivityAggregates(request)
      case _ => ProjectCommonException.throwClientErrorException(ResponseCode.invalidRequestData,
        ResponseCode.invalidRequestData.getErrorMessage)
    }
  }

  def getCacheKey(groupId: String, activityId: String, activityType: String) = {
    groupId + ":" + activityId + ":" + activityType + ":activity-agg"
  }

  def getGroupActivityAggregates(request: Request): Unit = {
    val groupId: String = request.get(SunbirdKey.GROUPID).asInstanceOf[String]
    val activityId: String = request.get(SunbirdKey.ACTIVITYID).asInstanceOf[String]
    val activityType: String = request.get(SunbirdKey.ACTIVITYTYPE).asInstanceOf[String]

    try {
      val key = getCacheKey(groupId, activityId, activityType)
      val cachedResponse = redisCache.get("activity-agg", key, classOf[Response])
      val response = {
        if(null != cachedResponse) {
          cachedResponse
        } else {
          val memberList: util.List[util.Map[String, AnyRef]] = getGroupMember(groupId, request)
          val enrolledGroupMember: util.List[util.Map[String, AnyRef]] = getEnrolledGroupMembers(activityId, activityType, memberList)
          populateResponse(groupId, activityId, activityType, enrolledGroupMember, memberList)
        }
      }
      sender().tell(response, self)
    } catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:: Exception thrown:: " + e)
        throw e
    }
  }

  def getGroupMember(groupId: String, request: Request): util.List[util.Map[String, AnyRef]] = {
    try{
      val readResponse = groupAggregatesUtil.getGroupDetails(groupId, request)
      val members: util.List[util.Map[String, AnyRef]] = readResponse.get("members").asInstanceOf[util.List[util.Map[String, AnyRef]]]

      if (CollectionUtils.isEmpty(members))
        ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No member found in this group.")

      members
    }catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getGroupMember:: Exception thrown:: " + e)
        throw e
    }
  }

  def getEnrolledGroupMembers(activityId: String, activityType: String, memberList: util.List[util.Map[String, AnyRef]]): util.List[util.Map[String, AnyRef]]= {
    try {
      val userList: util.List[String] = memberList.asScala.toList.map(obj => obj.getOrDefault("userId", "").asInstanceOf[String]).filter(x => StringUtils.isNotBlank(x)).asJava
      val userActivityDBResponse = groupDao.read(activityId, activityType, userList)
      if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
        ProjectCommonException.throwClientErrorException(ResponseCode.SERVER_ERROR, "Error while fetching group activity record.")

      val enrolledGroupMemberList: util.List[util.Map[String, AnyRef]] = userActivityDBResponse.get(SunbirdKey.RESPONSE).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (CollectionUtils.isEmpty(enrolledGroupMemberList))
        ProjectCommonException.throwClientErrorException(ResponseCode.CLIENT_ERROR, "No user enrolled to this activity.")

      enrolledGroupMemberList
    } catch {
      case e: Exception =>
        ProjectLogger.log("GroupAggregatesAction:getEnrolledGroupMembers:: Exception thrown:: " + e)
        throw e
    }
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
    setResponseToRedis(getCacheKey(groupId, activityId, activityType), response)
    response
  }

  def setResponseToRedis(key: String, response: Response) :Unit = {
    redisCache.put("activity-agg", key, response)
    redisCache.setMapExpiry("activity-agg", ttl)
  }

  def setInstanceVariable(groupAggregateUtil: GroupAggregatesUtil, groupDao: GroupDaoImpl, redisCache: Cache) = {
    this.groupAggregatesUtil = groupAggregateUtil
    this.groupDao = groupDao
    this.redisCache = redisCache
    this
  }
}

