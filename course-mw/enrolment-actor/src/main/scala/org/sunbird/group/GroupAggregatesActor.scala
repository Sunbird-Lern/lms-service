package org.sunbird.group

import java.text.MessageFormat

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.collections.MapUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{ProjectLogger, ProjectUtil}
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.actor.base.BaseActor
import org.sunbird.cache.CacheFactory
import org.sunbird.cache.interfaces.Cache

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class GroupAggregatesActor extends BaseActor {

  private val GROUP_MEMBERS_METADATA: java.util.List[String] = java.util.Arrays.asList("name", "userId", "role", "status", "createdBy")

  var groupDao: GroupDaoImpl = new GroupDaoImpl()
  var groupAggregatesUtil: GroupAggregatesUtil = new GroupAggregatesUtil()
  var redisCache: Cache = CacheFactory.getInstance()
  val cacheTtl: String = ProjectUtil.getConfigValue("cache_activity-agg_ttl")
  val cacheEnabled: String = ProjectUtil.getConfigValue("cache_activity-agg_enable")
  val ttl: Long = if(StringUtils.isNotBlank(cacheTtl)) cacheTtl.toLong else 3600
  val isCacheEnabled : Boolean = if(StringUtils.isNotBlank(cacheEnabled)) cacheEnabled.toBoolean else false

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
      val cachedResponse = if(isCacheEnabled) redisCache.get("activity-agg", key, classOf[Response]) else null
      val response = {
        if(null != cachedResponse) {
          cachedResponse
        } else {
          val memberList: java.util.List[java.util.Map[String, AnyRef]] = getGroupMember(groupId, request)
          val enrolledGroupMember: java.util.List[java.util.Map[String, AnyRef]] = if (CollectionUtils.isEmpty(memberList)){
            memberList
          }else{
            getEnrolledGroupMembers(activityId, activityType, memberList)
          }
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

  def getGroupMember(groupId: String, request: Request): java.util.List[java.util.Map[String, AnyRef]] = {
    val readResponse = groupAggregatesUtil.getGroupDetails(groupId, request)
    val members: java.util.List[java.util.Map[String, AnyRef]] = readResponse.get("members").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]

    if (CollectionUtils.isEmpty(members)){
      ProjectLogger.log("GroupAggregatesAction:getGroupMember:: No member associated with the group: " + groupId)
      new java.util.ArrayList[java.util.Map[String, AnyRef]]
    }else
      members
  }

  def getEnrolledGroupMembers(activityId: String, activityType: String, memberList: java.util.List[java.util.Map[String, AnyRef]]): java.util.List[java.util.Map[String, AnyRef]]= {
    val userList: java.util.List[String] = memberList.asScala.toList.map(obj => obj.getOrDefault("userId", "").asInstanceOf[String]).filter(x => StringUtils.isNotBlank(x)).asJava
    val userActivityDBResponse = groupDao.read(activityId, activityType, userList)
    if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
      ProjectCommonException.throwServerErrorException(ResponseCode.erroCallGrooupAPI,
        MessageFormat.format(ResponseCode.erroCallGrooupAPI.getErrorMessage()))

    val enrolledGroupMemberList: java.util.List[java.util.Map[String, AnyRef]] = userActivityDBResponse.get(SunbirdKey.RESPONSE).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    if (CollectionUtils.isEmpty(enrolledGroupMemberList)){
      ProjectLogger.log("GroupAggregatesAction:getGroupMember:: No member enrolled to the activity: " + activityId)
      new java.util.ArrayList[java.util.Map[String, AnyRef]]
    }else
      enrolledGroupMemberList
  }


  def populateResponse(groupId: String, activityId: String, activityType: String, enrolledGroupMember: java.util.List[java.util.Map[String, AnyRef]], memberList: java.util.List[java.util.Map[String, AnyRef]]): Response= {

    val response: Response = new Response()

    response.put("groupId", groupId)
    response.put("activity", new java.util.HashMap[String, AnyRef](){{
      put("id", activityId)
      put("type", activityType)
      put("agg", java.util.Arrays.asList(new java.util.HashMap[String, AnyRef](){{
        put("metric", "enrolmentCount")
        put("lastUpdatedOn", System.currentTimeMillis().toString)
        put("value", enrolledGroupMember.size.asInstanceOf[AnyRef])
      }}))
    }})

    val membersList = new java.util.ArrayList[java.util.Map[String, AnyRef]]
    if(CollectionUtils.isNotEmpty(enrolledGroupMember) && CollectionUtils.isNotEmpty(memberList)){
      val memberMap: java.util.Map[String, java.util.Map[String, AnyRef]] = memberList.asScala.toList.filter(x=>StringUtils.isNotBlank(x.getOrDefault("userId", "").asInstanceOf[String])).map(obj => (obj.getOrDefault("userId", "").asInstanceOf[String], obj)).toMap.asJava
      for(member <- enrolledGroupMember){
        membersList.add(new java.util.HashMap[String, AnyRef]() {{
          for(metadata <- GROUP_MEMBERS_METADATA){
            put(metadata, memberMap.get(member.get("user_id")).get(metadata))
          }

          put("agg", java.util.Arrays.asList(new java.util.HashMap[String, AnyRef]() {{
            put("metric", "completedCount")
            val aggLastUpdated = member.get("agg_last_updated").asInstanceOf[java.util.Map[String, AnyRef]]
            if(MapUtils.isNotEmpty(aggLastUpdated))
              put("lastUpdatedOn", aggLastUpdated.get("completedCount"))
            val agg = member.get("agg").asInstanceOf[java.util.Map[String, AnyRef]]
            if(MapUtils.isNotEmpty(agg))
              put("value", agg.get("completedCount"))
          }}))
        }})
      }
      response.put("members", membersList)
      setResponseToRedis(getCacheKey(groupId, activityId, activityType), response)
    }else{
      response.put("members", membersList)
    }

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

