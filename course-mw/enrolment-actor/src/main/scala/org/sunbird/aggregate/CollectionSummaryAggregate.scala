package org.sunbird.aggregate

import java.util

import akka.actor.ActorRef
import com.mashape.unirest.http.Unirest
import javax.inject.{Inject, Named}
import javax.ws.rs.core.MediaType
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpHeaders
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{JsonKey, ProjectUtil, TelemetryEnvKey}
import org.sunbird.common.request.Request
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.learner.util.Util

class CollectionSummaryAggregate @Inject()(@Named("collection-summary-aggregate-actor") courseBatchNotificationActorRef: ActorRef
                                          )(implicit val cacheUtil: RedisCacheUtil) extends BaseEnrolmentActor {

  val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl"))) ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl").toInt else 60
  val isCacheEnabled: Boolean = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_enable"))) ProjectUtil.getConfigValue("collection_summary_agg_cache_enable").toBoolean else false
  val dataSource: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_data_source"))) ProjectUtil.getConfigValue("collection_summary_agg_data_source") else "telemetry-events-syncts"

  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH)
    val filters = request.getRequest.get(JsonKey.FILTERS).asInstanceOf[util.Map[String, AnyRef]]
    val batchId = filters.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val collectionId = filters.get(JsonKey.COLLECTION_ID).asInstanceOf[String]
    val key = getCacheKey(batchId = batchId, request.getRequest.get("intervals").asInstanceOf[String])
    try {
      val result: String = Option(cacheUtil.get(key)).map(value => if (value.isEmpty) {
        getResponseFromDruid(batchId = batchId, courseId = collectionId, date = "", groupByKeys = List())
      } else {
        value
      }).getOrElse(getResponseFromDruid(batchId = batchId, courseId = collectionId, date = "", groupByKeys = List()))
      cacheUtil.set(key, result)
      val response = new Response()
      response.put(JsonKey.RESULT, result)
      sender().tell(response, self)
    } catch {
      case ex: Exception =>
        System.out.println("CollectionSummaryAggregate: Exception thrown:: " + ex)
        throw ex
    }
  }

  private def getUpdatedHeaders(headers: util.Map[String, String]): util.Map[String, String] = {
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    headers.put("Connection", "Keep-Alive")
    headers
  }


  def getResponseFromDruid(batchId: String, courseId: String, date: String, groupByKeys: List[String]): String = {
    val host = ProjectUtil.getConfigValue("druid_proxy_api_host")
    val port = ProjectUtil.getConfigValue("druid_proxy_api_port")
    val endPoint = ProjectUtil.getConfigValue("/druid/v2/")
    val query = "{\"queryType\":\"timeseries\",\"dataSource\":\"summary-events\",\"aggregations\":[{\"type\":\"count\",\"name\":\"count\"}],\"granularity\":\"all\",\"postAggregations\":[],\"intervals\":\"2019-11-19T00:00:00+00:00/2019-11-19T00:00:00+00:00\"}"
    val request = Unirest.post(s"http://11.2.4.39:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)
    request.asString().getBody
  }

  def getCacheKey(batchId: String, intervals: String): String = {
    val date = intervals.split("/")
    val startDate = date.lift(0).getOrElse("2020901")
    val endDate = date.lift(1).getOrElse("20200901")
    s"bmetircs$batchId:$startDate:$endDate"
  }


}
