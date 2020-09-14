package org.sunbird.aggregate

import java.util

import akka.actor.ActorRef
import javax.inject.{Inject, Named}
import org.apache.commons.lang3.StringUtils
import org.sunbird.cache.util.RedisCacheUtil
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
    val batchId = filters.get("batchId").asInstanceOf[String]
    val collectionId = filters.get("collectionId").asInstanceOf[String]
    println("Request Object Data" + request)
    val key = getCacheKey(collectionId = collectionId, batchId = batchId)
    try {
      val response: String = Option(cacheUtil.get(key)).map(value => if (value.isEmpty) {
        getResponseFromDruid(batchId = batchId, courseId = collectionId, date = "", groupByKeys = List())
      } else {
        value
      }).getOrElse(getResponseFromDruid(batchId = batchId, courseId = collectionId, date = "", groupByKeys = List()))

      println("druidResponse" + response)

      cacheUtil.set(key, response)
      sender().tell(response, self)
    } catch {
      case ex: Exception =>
        System.out.println("CollectionSummaryAggregate: Exception thrown:: " + ex)
        throw ex
    }

  }

  def getResponseFromDruid(batchId: String, courseId: String, date: String, groupByKeys: List[String]): String = {
    println("InvokedDruid")
    val query = "{\"queryType\":\"groupBy\",\"dataSource\":\"telemetry-events-syncts\",\"dimensions\":[\"edata_type\",\"collection_name\",{\"type\":\"extraction\",\"dimension\":\"derived_loc_district\",\"outputName\":\"district_slug\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"districtLookup\",\"replaceMissingValueWith\":\"Unknown\"}},{\"type\":\"extraction\",\"dimension\":\"derived_loc_state\",\"outputName\":\"state\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"stateSlugLookup\",\"replaceMissingValueWith\":\"Unknown\"}}],\"aggregations\":[{\"fieldName\":\"actor_id\",\"fieldNames\":[\"actor_id\"],\"type\":\"cardinality\",\"name\":\"COUNT_DISTINCT(actor_id)\"}],\"granularity\":\"all\",\"postAggregations\":[],\"intervals\":\"2020-09-07T00:00:00+00:00/2020-09-14T00:00:00+00:00\",\"filter\":{\"type\":\"and\",\"fields\":[{\"type\":\"or\",\"fields\":[{\"type\":\"selector\",\"dimension\":\"edata_type\",\"value\":\"complete\"},{\"type\":\"selector\",\"dimension\":\"edata_type\",\"value\":\"enrollment\"},{\"type\":\"selector\",\"dimension\":\"edata_type\",\"value\":\"certificate-issued\"}]},{\"type\":\"and\",\"fields\":[{\"type\":\"selector\",\"dimension\":\"context_cdata_id\",\"value\":\"0130929928739635201\"},{\"type\":\"and\",\"fields\":[{\"type\":\"selector\",\"dimension\":\"object_rollup_l1\",\"value\":\"do_31309287232935526411138\"},{\"type\":\"selector\",\"dimension\":\"eid\",\"value\":\"AUDIT\"}]}]}]},\"limitSpec\":{\"type\":\"default\",\"limit\":10000,\"columns\":[{\"dimension\":\"COUNT_DISTINCT(actor_id)\",\"direction\":\"descending\"}]}}"
    "{\n  \"queryType\": \"groupBy\",\n  \"dataSource\": \"telemetry-events-syncts\"\n}"
  }

  def getCacheKey(collectionId: String, batchId: String): String = {
    batchId + ":" + collectionId + ":collection-summary-agg"
  }

}
