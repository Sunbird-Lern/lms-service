package org.sunbird.group

import java.text.MessageFormat

import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{LoggerEnum, ProjectLogger, ProjectUtil}
import org.sunbird.common.request.{Request, RequestContext}
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
  val ttl: Long = if(StringUtils.isNotBlank(ProjectUtil.getConfigValue("group_activity_agg_cache_ttl"))) (ProjectUtil.getConfigValue("group_activity_agg_cache_ttl")).toLong else 60
  val isCacheEnabled = if(StringUtils.isNotBlank(ProjectUtil.getConfigValue("group_activity_agg_cache_enable"))) (ProjectUtil.getConfigValue("group_activity_agg_cache_enable")).toBoolean else false

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
          ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:cachedResponse :: Data fetched from cache.", LoggerEnum.INFO.name)
          cachedResponse
        } else {
          val groupMembers: java.util.List[java.util.Map[String, AnyRef]] = getGroupMember(groupId, request)
          ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:groupMembers :: Group: " + groupId + ":: Member Count : " + groupMembers.size(), LoggerEnum.INFO.name)
          val usersAggs: java.util.List[java.util.Map[String, AnyRef]] = if (CollectionUtils.isEmpty(groupMembers)) {
            groupMembers
          } else {
            getUserActivityAggs(activityId, activityType, groupMembers, request.getRequestContext)
          }
          ProjectLogger.log("GroupAggregatesAction:getGroupActivityAggregates:usersAggs :: Group: " + groupId + " :: Activity : " + activityId + " :: Enrolled Member Count: " + usersAggs.size(), LoggerEnum.INFO.name)
          populateResponse(groupId, activityId, activityType, usersAggs, groupMembers)
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
      ProjectLogger.log("GroupAggregatesAction:getGroupMember:: No member associated with the group: " + groupId, LoggerEnum.INFO.name)
      new java.util.ArrayList[java.util.Map[String, AnyRef]]
    }else
      members
  }

  def getUserActivityAggs(activityId: String, activityType: String, memberList: java.util.List[java.util.Map[String, AnyRef]], requestContext: RequestContext): java.util.List[java.util.Map[String, AnyRef]]= {
    val userList: java.util.List[String] = memberList.asScala.toList.map(obj => obj.getOrDefault("userId", "").asInstanceOf[String]).filter(x => StringUtils.isNotBlank(x)).asJava
    val userActivityDBResponse = groupDao.read(activityId, activityType, userList, requestContext)
    if (userActivityDBResponse.getResponseCode != ResponseCode.OK)
      ProjectCommonException.throwServerErrorException(ResponseCode.erroCallGrooupAPI,
        MessageFormat.format(ResponseCode.erroCallGrooupAPI.getErrorMessage()))

    val enrolledGroupMemberList: java.util.List[java.util.Map[String, AnyRef]] = userActivityDBResponse.get(SunbirdKey.RESPONSE).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    if (CollectionUtils.isEmpty(enrolledGroupMemberList)){
      ProjectLogger.log("GroupAggregatesAction:getGroupMember:: No member enrolled to the activity: " + activityId, LoggerEnum.INFO.name)
      new java.util.ArrayList[java.util.Map[String, AnyRef]]
    }else
      enrolledGroupMemberList
  }

  def populateResponse(groupId: String, activityId: String, activityType: String, usersAggs: java.util.List[java.util.Map[String, AnyRef]], groupMembers: java.util.List[java.util.Map[String, AnyRef]]): Response= {
    val finalMemberList = if(CollectionUtils.isNotEmpty(usersAggs) && CollectionUtils.isNotEmpty(groupMembers)) {
      val membersMap = groupMembers.asScala.toList.filter(x => StringUtils.isNotBlank(x.getOrDefault("userId", "").asInstanceOf[String]))
        .map(obj => (obj.getOrDefault("userId", "").asInstanceOf[String], obj)).toMap.asJava
      val aggName = "completedCount"
      usersAggs.map(dbAggRecord => {
        val dbAgg = dbAggRecord.get("agg").asInstanceOf[java.util.Map[String, AnyRef]]
        val aggLastUpdated = dbAggRecord.get("agg_last_updated").asInstanceOf[java.util.Map[String, AnyRef]]
        val agg = List(Map("metric"-> aggName, "value" -> dbAgg.get(aggName), "lastUpdatedOn" -> aggLastUpdated.get(aggName)).asJava).asJava
        val userId = dbAggRecord.get("user_id").asInstanceOf[String]
        (membersMap.get(userId).filterKeys(key => GROUP_MEMBERS_METADATA.contains(key)) ++ Map("agg" -> agg)).asJava
      }).toList
    } else List()

    ProjectLogger.log("GroupAggregatesAction:populateResponse:finalMemberList :: Group: " + groupId + " :: Activity : " + activityId + " :: Final Member List Count: " + finalMemberList.size, LoggerEnum.INFO.name)

    val response: Response = new Response()
    response.put("groupId", groupId)
    val enrolmentCount = finalMemberList.map(m => m.getOrDefault("userId", "").asInstanceOf[String])
      .filter(uId => StringUtils.isNotBlank(uId)).distinct.size
    val activityLastUpdatedOn = activityLastUpdated(finalMemberList)
    val activityAggs = List(Map("metric" -> "enrolmentCount", "lastUpdatedOn" -> activityLastUpdatedOn, "value" -> enrolmentCount).asJava).asJava
    response.put("activity", Map("id" -> activityId, "type" -> activityType, "agg" -> activityAggs).asJava)
    response.put("members", finalMemberList.asJava)
    if (finalMemberList.nonEmpty && finalMemberList.size > 0) {
      setResponseToRedis(getCacheKey(groupId, activityId, activityType), response)
    }
    response
  }

  def activityLastUpdated(membersAggList: List[java.util.Map[String, AnyRef]]) = {
    if (membersAggList.nonEmpty) {
      val aggLatestUpdated = membersAggList.map(m => m.get("agg").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
        .flatten.map(agg => agg.get("lastUpdatedOn").asInstanceOf[java.util.Date]).max
      aggLatestUpdated.getTime
    } else System.currentTimeMillis
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