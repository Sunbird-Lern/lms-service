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
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.learner.actors.coursebatch.dao.CourseBatchDao
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseBatchDaoImpl
import org.sunbird.learner.util.{JsonUtil, Util}

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.JavaConverters._

class CollectionSummaryAggregate @Inject()(implicit val cacheUtil: RedisCacheUtil) extends BaseActor {
  val ttl: Int = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl"))) ProjectUtil.getConfigValue("collection_summary_agg_cache_ttl").toInt else 60
  val dataSource: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("collection_summary_agg_data_source"))) ProjectUtil.getConfigValue("collection_summary_agg_data_source") else "telemetry-events-syncts"
  val stateLookUpQuery = "{\"type\":\"extraction\",\"dimension\":\"derived_loc_state\",\"outputName\":\"state\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"stateLookup\",\"retainMissingValue\":true}}"
  val districtLookUpQuery = "{\"type\":\"extraction\",\"dimension\":\"derived_loc_district\",\"outputName\":\"district\",\"extractionFn\":{\"type\":\"registeredLookup\",\"lookup\":\"districtLookup\",\"retainMissingValue\":true}}"
  val gson = new Gson
  var courseBatchDao: CourseBatchDao = new CourseBatchDaoImpl()

  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH, this.getClass.getName)

    val response = new Response()
    val filters = request.getRequest.get(JsonKey.FILTERS).asInstanceOf[util.Map[String, AnyRef]]
    val groupByKeys = request.getRequest.getOrDefault(JsonKey.GROUPBY, new util.ArrayList[String]()).asInstanceOf[util.ArrayList[String]].asScala.toList
    val batchId = filters.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val collectionId = filters.get(JsonKey.COLLECTION_ID).asInstanceOf[String]
    val granularity = getDate(request.getRequestContext,request.getRequest.getOrDefault("granularity", "ALL").asInstanceOf[String], collectionId, batchId)
    val key = getCacheKey(batchId = batchId, granularity, groupByKeys)
    println(s"Druid granularity: $granularity & Cache Key: $key")
    try {
      val redisData = cacheUtil.get(key)
      val result: util.Map[String, AnyRef] = if (null != redisData && !redisData.isEmpty) {
        JsonUtil.deserialize(redisData, new util.HashMap[String, AnyRef]().getClass)
      } else {
        val druidResponse = getResponseFromDruid(batchId = batchId, courseId = collectionId, granularity, groupByKeys = groupByKeys)
        val transformedResult = transform(druidResponse, groupByKeys)
        if (!transformedResult.isEmpty) cacheUtil.set(key, JsonUtil.serialize(transformedResult), ttl)
        transformedResult
      }
      response.put("metrics", result.get("metrics"))
      response.put("collectionId", collectionId)
      response.put("batchId", batchId)
      if (result.get("lastUpdatedOn") != null) {
        response.put("lastUpdatedOn", new BigDecimal(result.get("lastUpdatedOn").toString).toBigInteger()) // Converting scientific notation number bigInteger(Long)
      } else {
        response.put("lastUpdatedOn", System.currentTimeMillis().asInstanceOf[AnyRef]) // This scenarios won't occurre, for the safer side adding this condition
      }
      if (groupByKeys.nonEmpty) {
        response.put("groupBy", result.get("groupBy"))
      }
      sender().tell(response, self)
    } catch {
      case ex: Exception =>
        ProjectLogger.log("CollectionSummaryAggregate: Exception thrown = " + ex)
        throw ex
    }
  }

  def transform(druidResponse: String, groupByKeys: List[String]): util.HashMap[String, AnyRef] = {
    val transformedResult = new util.HashMap[String, AnyRef]()
    import scala.collection.JavaConversions._
    val parsedResult: AnyRef = JsonUtil.deserialize(druidResponse, classOf[AnyRef])
    if (isArray(druidResponse) && parsedResult.asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].nonEmpty) {
      val groupingObj = parsedResult.asInstanceOf[util.ArrayList[util.Map[String, AnyRef]]].map(x => {
        val eventObj = x.get("event").asInstanceOf[util.Map[String, AnyRef]]
        val eData_type = if (StringUtils.equalsIgnoreCase(eventObj.get("edata_type").asInstanceOf[String], "enrol") || StringUtils.equalsIgnoreCase(eventObj.get("edata_type").asInstanceOf[String], "enroled")) "enrolment" else eventObj.get("edata_type").asInstanceOf[String]
        (eventObj.get("state"), eventObj.get("district")) -> Map("type" -> eData_type, "count" -> eventObj.get("userCount"))
      }).groupBy(x => x._1)
      val groupingResult = groupingObj.map(obj => {
        val groupByMap = new util.HashMap[String, AnyRef]()
        val valuesList = new util.ArrayList[util.HashMap[String, Any]]()
        obj._2.map(x => {
          val valuesMap = new util.HashMap[String, Any]()
          valuesMap.put("type", x._2("type"))
          valuesMap.put("count", x._2("count").asInstanceOf[Double].longValue())
          valuesList.add(valuesMap)
        })
        if (groupByKeys.contains("dist")) groupByMap.put("district", obj._1._2)
        if (groupByKeys.contains("state")) groupByMap.put("state", obj._1._1)
        groupByMap.put("values", valuesList)
        groupByMap
      }).asJava
      val metrics = groupingResult.flatMap(metrics => metrics.get("values").asInstanceOf[util.ArrayList[util.HashMap[String, AnyRef]]])
        .groupBy(x => x.get("type").asInstanceOf[String])
        .mapValues(_.map(_ ("count").asInstanceOf[Long]).sum.longValue()).map(value => {
        Map("type" -> value._1, "count" -> value._2).asJava
      }).asJava
      transformedResult.put("metrics", metrics)
      transformedResult.put("lastUpdatedOn", System.currentTimeMillis().asInstanceOf[AnyRef])
      if (groupByKeys.nonEmpty) {
        transformedResult.put("groupBy", groupingResult)
      }
    }
    transformedResult
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
         |            "value": "enrol"
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
    println("Druid Query" + JsonUtil.serialize(druidQuery))
    val host: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_host"))) ProjectUtil.getConfigValue("druid_proxy_api_host") else "localhost"
    val port: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_port"))) ProjectUtil.getConfigValue("druid_proxy_api_port") else "8081"
    val endPoint: String = if (StringUtils.isNotBlank(ProjectUtil.getConfigValue("druid_proxy_api_endpoint"))) ProjectUtil.getConfigValue("druid_proxy_api_endpoint") else "/druid/v2/"
    val request = Unirest.post(s"http://$host:$port$endPoint").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(druidQuery)
    val response = request.asString().getBody
    println("=====Druid Response======" + response)
    response
  }

  def getCacheKey(batchId: String, intervals: String, groupByKeys: List[String]): String = {
    val regex = "[^a-zA-Z0-9]"
    val date = intervals.split("/")
    s"bmetrics:$batchId:${date(0).replaceAll(regex, "")}:${date(1).replaceAll(regex, "")}:${groupByKeys.mkString(" ").replaceAll(" ", "_")}"
  }

  def isArray(value: String): Boolean = {
    val redisValue = value.trim
    redisValue.length > 0 && redisValue.startsWith("[")
  }

  def getDate(requestContext: RequestContext, date: String, courseId: String, batchId: String): String = {
    val dateTimeFormate = DateTimeFormat.forPattern("yyyy-MM-dd")
    val sd = new SimpleDateFormat("yyyy-MM-dd");
    val defaultStartDate = sd.format(sd.parse(dateTimeFormate.print(DateTime.now(DateTimeZone.UTC))))
    val defaultEndDate = sd.format(sd.parse(dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).plusDays(1)))) // Adding 1 Day extra
    if (StringUtils.equalsIgnoreCase(date, "ALL")) {

      val batchOldStartDate: String = Option(courseBatchDao.readById(courseId, batchId, requestContext).getOldStartDate).map(date => if (date.nonEmpty) date else defaultStartDate).getOrElse(defaultStartDate)
      val batchOldEndDate: String = Option(courseBatchDao.readById(courseId, batchId, requestContext).getOldEndDate).map(date => if (date.nonEmpty) date else defaultEndDate).getOrElse(defaultEndDate)

      val batchLatestStartDate: Date = courseBatchDao.readById(courseId, batchId, requestContext).getStartDate
      val batchLatestEndDate: Date = courseBatchDao.readById(courseId, batchId, requestContext).getEndDate

      val startDate: String = Option(batchLatestStartDate).map(date => sd.format(date)).getOrElse(batchOldStartDate)
      val endDate: String = Option(batchLatestEndDate).map(date => sd.format(date)).getOrElse(batchOldEndDate)

      s"$startDate/$endDate"
    } else {
      val nofDates = date.replaceAll("[^0-9]", "")
      val batchStartDate: String = sd.format(sd.parse(dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).minusDays(nofDates.toInt))))
      s"$batchStartDate/$defaultEndDate"
    }
  }

}