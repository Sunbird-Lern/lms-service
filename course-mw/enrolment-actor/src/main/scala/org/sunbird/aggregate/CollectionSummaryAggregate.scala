package org.sunbird.aggregate

import akka.actor.ActorRef
import javax.inject.{Inject, Named}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{ProjectLogger, ProjectUtil, TelemetryEnvKey}
import org.sunbird.common.request.Request
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.keys.SunbirdKey
import org.sunbird.learner.util.{JsonUtil, Util}

class CollectionSummaryAggregate @Inject()(@Named("collection-summary-aggregate-actor") courseBatchNotificationActorRef: ActorRef
                                          )(implicit val cacheUtil: RedisCacheUtil) extends BaseEnrolmentActor {

  val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl"))) (ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl")).toInt else 60
  val isCacheEnabled = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_enable"))) (ProjectUtil.getConfigValue("collection_summary_agg_cache_enable")).toBoolean else false


  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH)
    println("Request Object Data" + request)
    val key = getCacheKey(request)
    try {
      val response = Option(getResponseFromRedis(key)).getOrElse(getResponseFromDruid(request))
      if (null != response) {
        setResponseToRedis(key, response)
        sender().tell(response, self)
      }
    } catch {
      case ex: Exception => {
        System.out.println("CollectionSummaryAggregate: Exception thrown:: " + ex)
        throw ex
      }
    }

  }

  def getResponseFromDruid(request: Request): Response = {
    null
  }

  def getCacheKey(request: Request): String = {
    val batchId: String = request.get("batchId").asInstanceOf[String]
    val collectionId: String = request.get("collectionId").asInstanceOf[String]
    batchId + ":" + collectionId + ":collection-summary-agg"
  }

  def setResponseToRedis(key: String, response: Response): Unit = {
    cacheUtil.set(key, JsonUtil.serialize(response), ttl)
  }

  def getResponseFromRedis(key: String): Response = {
    val responseString = cacheUtil.get(key)
    if (responseString != null) {
      JsonUtil.deserialize(responseString, classOf[Response])
    } else null
  }

}
