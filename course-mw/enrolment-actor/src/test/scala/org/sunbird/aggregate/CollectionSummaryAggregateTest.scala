package org.sunbird.aggregate

import java.io.IOException
import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.datastax.driver.core.Cluster
import com.google.gson.Gson
import com.mashape.unirest.http.Unirest
import javax.ws.rs.core.MediaType
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpHeaders
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.util.JsonUtil
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

import scala.concurrent.duration.FiniteDuration

class CollectionSummaryAggregateTest extends FlatSpec with Matchers with BeforeAndAfterAll with MockFactory {

  val system = ActorSystem.create("system")


  var redisServer: RedisServer = _
  redisServer = new RedisServer(6379)
  EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
  var server = new MockWebServer()
  server.start(8082)
  if (!redisServer.isActive) {
    redisServer.start()
  }

  var jedis: Jedis = _
  val redisConnect = new RedisCacheUtil()

  override def afterAll() {
    super.afterAll()
    redisServer.stop();
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()

  }

  override def beforeAll() {
    if (!redisServer.isActive()) {
      redisServer.start();
    }
    val cluster = {
      Cluster.builder()
        .addContactPoints("localhost")
        .withPort(9142)
        .withoutJMXReporting()
        .build()
    }
    var session = cluster.connect()
    import org.cassandraunit.CQLDataLoader
    val dataLoader = new CQLDataLoader(session)
    //dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))

  }

  def getCacheKey(batchId: String, intervals: String, groupByKeys: List[String]): String = {
    val regex = "[^a-zA-Z0-9]"
    val date = intervals.split("/")
    s"bmetircs:$batchId:${date(0).replaceAll(regex, "")}:${date(1).replaceAll(regex, "")}:${groupByKeys.mkString(" ")}"
  }

  def getDate(date: String): String = {
    val dateTimeFormate = DateTimeFormat.forPattern("yyyy-MM-dd")
    val nofDates = date.replaceAll("[^0-9]", "")
    val presentDate = dateTimeFormate.print(DateTime.now(DateTimeZone.UTC))
    var fromDate = ""
    if (!StringUtils.equalsIgnoreCase(date, "ALL")) {
      fromDate = dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).minusDays(nofDates.toInt))
    } else {
      // batch start date fetch from the cassandra
    }
    s"$fromDate/$presentDate"
  }

  private def getUpdatedHeaders(headers: util.Map[String, String]): util.Map[String, String] = {
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    headers.put("Connection", "Keep-Alive")
    headers
  }


  "CollectionSummaryActivityAgg" should "return success response from redis" in {
    redisConnect.set(getCacheKey("0130929928739635202", getDate("LAST_30DAYS"), List("state")), "[{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":482.6580757318888,\"district\":\"Tumkur\",\"edata_type\":\"complete\",\"state\":\"Karnatka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":473.81686557810804,\"district\":\"Tumkur\",\"edata_type\":\"certificated_issued\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":458.7487411983637,\"district\":\"Tumkur\",\"edata_type\":\"enrolled\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":433.87897596484714,\"district\":\"Kolar\",\"edata_type\":\"complete\",\"state\":\"karnatka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":411.75150981167434,\"district\":\"Mysore\",\"edata_type\":\"enrollement\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":408.0867310228416,\"district\":\"Mysore\",\"edata_type\":\"complete\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":400.7767886993949,\"district\":\"Banglore\",\"edata_type\":\"complete\",\"state\":\"Karnataka\"}}]")
    val groupByKeys = new util.ArrayList[String]
    groupByKeys.add("state")
    val response = callActor(getRequest("0130929928739635202", "do_31309287232935526411138", "LAST_30DAYS", groupByKeys), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
  }

  "CollectionSummaryActivityAgg" should "return success response from druid" in {
    val groupByKeys = new util.ArrayList[String]
    groupByKeys.add("dist")
    groupByKeys.add("state")
    mockDruid("[{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":482.6580757318888,\"district\":\"Tumkur\",\"edata_type\":\"complete\",\"state\":\"Karnatka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":473.81686557810804,\"district\":\"Tumkur\",\"edata_type\":\"certificated_issued\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":458.7487411983637,\"district\":\"Tumkur\",\"edata_type\":\"enrolled\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":433.87897596484714,\"district\":\"Kolar\",\"edata_type\":\"complete\",\"state\":\"karnatka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":411.75150981167434,\"district\":\"Mysore\",\"edata_type\":\"enrollement\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":408.0867310228416,\"district\":\"Mysore\",\"edata_type\":\"complete\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"2020-09-07T00:00:00.000Z\",\"event\":{\"userCount\":400.7767886993949,\"district\":\"Banglore\",\"edata_type\":\"complete\",\"state\":\"Karnataka\"}}]")
    val query = "{\"request\":{\"filters\":{\"collectionId\":\"do_31309287232935526411138\",\"batchId\":\"0130929928739635201\"},\"groupBy\":[],\"intervals\":\"20120-01-23/2020-09-24\"}}"
    Unirest.post(s"http://localhost:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)
    val response = callActor(getRequest("0130929928739635201", "do_31309287232935526411138", "LAST_7DAYS", groupByKeys), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
  }

  //    "CollectionSummaryActivityAgg" should "should not store the data into redis" in {
  //      mockDruid("{}")
  //      val groupByKeys = new util.ArrayList[String]
  //      val query = "{\"request\":{\"filters\":{\"collectionId\":\"course-01\",\"batchId\":\"batch-01\"},\"groupBy\":[],\"intervals\":\"20120-01-23/2020-09-24\"}}"
  //      Unirest.post(s"http://localhost:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)
  //      val response = callActor(getRequest("batch-01", "course-01", "LAST_30DAYS", groupByKeys), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
  //      assert(response.getResponseCode == ResponseCode.OK)
  //      val redisResp = redisConnect.get(getCacheKey("batch-01", getDate("LAST_30DAYS"), List()))
  //      redisResp.isEmpty should be(true)
  //    }

  def blankRestResponse(): Response = {
    val response = new Response()
    response
  }

  def callActor(request: Request, props: Props): Response = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(10, TimeUnit.SECONDS))
  }

  def callActorForFailure(request: Request, props: Props): ProjectCommonException = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[ProjectCommonException](FiniteDuration.apply(10, TimeUnit.SECONDS))
  }

  def getRequest(batchId: String, courseId: String, date: String, groupByKeys: util.ArrayList[String]): Request = {
    println("groupbykeuys" + groupByKeys)
    val custom = new util.HashMap[String, AnyRef]()
    val filterMap = new util.HashMap[String, AnyRef]()
    filterMap.put("collectionId", courseId)
    filterMap.put("batchId", batchId)
    custom.put("filters", filterMap)
    custom.put("granularity", date)
    custom.put("groupBy", groupByKeys)
    val request = new Request
    request.setRequest(custom)
    request
  }

  def defaultStringHandler(name: String): String = {
    ""
  }

  @throws[IOException]
  def mockDruid(response: String): Unit = {
    server.enqueue(new MockResponse().setBody(response))
    server.enqueue(new MockResponse().setHeader("Authorization", ""))
    server.url("http://localhost:8082/druid/v2/")
    val headers = new util.HashMap[String, String]()
    headers.put("Authorization", "")
  }

}
