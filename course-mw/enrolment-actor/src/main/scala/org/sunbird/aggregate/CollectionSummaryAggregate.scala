package org.sunbird.aggregate

import java.util

import com.google.gson.Gson
import com.mashape.unirest.http.Unirest
import javax.inject.Inject
import javax.ws.rs.core.MediaType
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpHeaders
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.sunbird.actor.base.BaseActor
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.{JsonKey, ProjectLogger, ProjectUtil, TelemetryEnvKey}
import org.sunbird.common.request.Request
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl
import org.sunbird.learner.util.{JsonUtil, Util}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class CollectionSummaryAggregate @Inject()(implicit val cacheUtil: RedisCacheUtil) extends BaseActor {
  val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl"))) ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl").toInt else 60
  val dataSource: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_data_source"))) ProjectUtil.getConfigValue("collection_summary_agg_data_source") else "telemetry-events-syncts"
  val stateLookUpQuery = "{\"type\":\"extraction\",\"dimension\":\"derived_loc_state\",\"outputName\":\"state\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"stateLookup\",\"replaceMissingValueWith\":\"Unknown\"}}"
  val districtLookUpQuery = "{\"type\":\"extraction\",\"dimension\":\"derived_loc_district\",\"outputName\":\"district\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"districtLookup\",\"replaceMissingValueWith\":\"Unknown\"}}"
  val response = new Response()
  val gson = new Gson
  var courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()

  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH)
    val filters = request.getRequest.get(JsonKey.FILTERS).asInstanceOf[util.Map[String, AnyRef]]
    val groupByKeys = request.getRequest.getOrDefault(JsonKey.GROUPBY, new util.ArrayList[String]()).asInstanceOf[util.ArrayList[String]].asScala.toList
    val batchId = filters.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val collectionId = filters.get(JsonKey.COLLECTION_ID).asInstanceOf[String]
    val granularity = getDate(request.getRequest.getOrDefault("granularity", "ALL").asInstanceOf[String], collectionId, batchId)
    val key = getCacheKey(batchId = batchId, granularity, groupByKeys)
    try {
      val redisData = cacheUtil.get(key)
      val result: String = Option(redisData).map(value => if (value.isEmpty) {
        getResponseFromDruid(batchId = batchId, courseId = collectionId, granularity, groupByKeys = groupByKeys)
      } else {
        value
      }).getOrElse(getResponseFromDruid(batchId = batchId, courseId = collectionId, granularity, groupByKeys = groupByKeys))
      import scala.collection.JavaConversions._
      val parsedResult: AnyRef = JsonUtil.deserialize(result, classOf[AnyRef])
      if (isArray(result) && parsedResult.asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].nonEmpty) {
        cacheUtil.set(key, result, ttl)
        val metricsList = new util.ArrayList[util.HashMap[String, AnyRef]]()
        val groupByMetricsList = new util.ArrayList[util.HashMap[String, AnyRef]]()
        parsedResult.asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].map(metric => {
          val valuesList = new util.ArrayList[util.HashMap[String, AnyRef]]()
          val valuesMap = new util.HashMap[String, AnyRef]()
          val metricsMap = new util.HashMap[String, AnyRef]()
          val metricsObj = metric.get("event").asInstanceOf[util.Map[String, AnyRef]]
          valuesMap.put("type", metricsObj.get("edata_type"))
          valuesMap.put("count", metricsObj.get("userCount"))
          metricsMap.put("district", metricsObj.get("district"))
          metricsMap.put("state", metricsObj.get("state"))
          metricsMap.put("values", valuesList.add(valuesMap))
          groupByMetricsList.add(metricsMap)
          metricsList.add(valuesMap)
        })
        response.put("metrics", metricsList)
        if (groupByKeys.nonEmpty) response.put("groupBy", groupByMetricsList)
      } else {
        response.put("metrics", parsedResult)
      }
      sender().tell(response, self)
    } catch {
      case ex: Exception =>
        ProjectLogger.log("CollectionSummaryAggregate: Exception thrown = " + ex)
        throw ex
    }
  }

  private def getUpdatedHeaders(headers: util.Map[String, String]): util.Map[String, String] = {
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    headers.put("Connection", "Keep-Alive")
    headers
  }

  def getResponseFromDruid(batchId: String, courseId: String, date: String, groupByKeys: List[String]): String = {
    val druidQuery =
      s"""{
         |  "queryType": "groupBy",
         |  "dataSource": "$dataSource",
         |  "dimensions": [
         |    "edata_type"
         |    ${if (groupByKeys.contains("dist") || groupByKeys.contains("state")) "," else null}
         |    ${if (groupByKeys.contains("dist")) districtLookUpQuery else null}
         |    ${if (groupByKeys.contains("dist") && groupByKeys.contains("state")) "," else null}
         |    ${if (groupByKeys.contains("state")) stateLookUpQuery else null}
         |  ],
         |  "aggregations": [
         |    {
         |      "fieldName": "actor_id",
         |      "fieldNames": [
         |        "actor_id"
         |      ],
         |      "type": "cardinality",
         |      "name": "userCount"
         |    }
         |  ],
         |  "granularity": "all",
         |  "postAggregations": [],
         |  "intervals": "$date",
         |  "filter": {
         |    "type": "and",
         |    "fields": [
         |      {
         |        "type": "or",
         |        "fields": [
         |          {
         |            "type": "selector",
         |            "dimension": "edata_type",
         |            "value": "complete"
         |          },
         |          {
         |            "type": "selector",
         |            "dimension": "edata_type",
         |            "value": "enrollment"
         |          },
         |          {
         |            "type": "selector",
         |            "dimension": "edata_type",
         |            "value": "certificate-issued"
         |          }
         |        ]
         |      },
         |      {
         |        "type": "and",
         |        "fields": [
         |          {
         |            "type": "selector",
         |            "dimension": "context_cdata_id",
         |            "value": "$batchId"
         |          },
         |          {
         |            "type": "and",
         |            "fields": [
         |              {
         |                "type": "selector",
         |                "dimension": "object_rollup_l1",
         |                "value": "$courseId"
         |              },
         |              {
         |                "type": "selector",
         |                "dimension": "eid",
         |                "value": "AUDIT"
         |              }
         |            ]
         |          }
         |        ]
         |      }
         |    ]
         |  },
         |  "limitSpec": {
         |    "type": "default",
         |    "limit": 10000,
         |    "columns": [
         |      {
         |        "dimension": "userCount",
         |        "direction": "descending"
         |      }
         |    ]
         |  }
         |}""".stripMargin.replaceAll("null", " ")
    ProjectLogger.log("Druid Query Is " + druidQuery)
    println("Query" + druidQuery)
    val host: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_host"))) ProjectUtil.getConfigValue("druid_proxy_api_host") else "localhost"
    val port: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_port"))) ProjectUtil.getConfigValue("druid_proxy_api_port") else "8081"
    val endPoint: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_endpoint"))) ProjectUtil.getConfigValue("druid_proxy_api_endpoint") else "/druid/v2/"
    val request = Unirest.post(s"http://11.2.1.20:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(druidQuery)
    request.asString().getBody
  }

  def getCacheKey(batchId: String, intervals: String, groupByKeys: List[String]): String = {
    val regex = "[^a-zA-Z0-9]"
    val date = intervals.split("/")
    s"bmetircs:$batchId:${date(0).replaceAll(regex, "")}:${date(1).replaceAll(regex, "")}:${groupByKeys.mkString(" ")}"
  }

  def isArray(value: String): Boolean = {
    val redisValue = value.trim
    redisValue.length > 0 && redisValue.startsWith("[")
  }

  def getDate(date: String, courseId: String, batchId: String): String = {
    val dateTimeFormate = DateTimeFormat.forPattern("yyyy-MM-dd")
    // When endate is null in the table considering default date as 7
    val defaultEndDate = dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).minusDays(7))
    val nofDates = date.replaceAll("[^0-9]", "")
    val endDate = dateTimeFormate.print(DateTime.now(DateTimeZone.UTC))
    val startDate: String = if (!StringUtils.equalsIgnoreCase(date, "ALL")) {
      dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).minusDays(nofDates.toInt))
    } else {
      val batchEndDate = courseBatchDao.readById(courseId, batchId).getEndDate
      ProjectLogger.log(s"BatchId: $batchId, CourseId: $courseId, EndDate" + batchEndDate)
      Option(batchEndDate).map(d => if (d.isEmpty) defaultEndDate else batchEndDate).getOrElse(defaultEndDate)
    }
    s"$startDate/$endDate"
  }
}
