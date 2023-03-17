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
import redis.clients.jedis.Jedis
import scala.collection.JavaConverters._

import scala.concurrent.duration.FiniteDuration

class CollectionSummaryAggregateTest extends FlatSpec with Matchers with BeforeAndAfterAll with MockFactory {

  val system = ActorSystem.create("system")

  val gson = new Gson()
  EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
  var server = new MockWebServer()
  server.start(8082)
  var jedis: Jedis = _
  val redisConnect = new RedisCacheUtil()

  override def afterAll() {
    super.afterAll()
    EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()

  }

  override def beforeAll() {
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
    s"bmetrics:$batchId:${date(0).replaceAll(regex, "")}:${date(1).replaceAll(regex, "")}:${groupByKeys.mkString(" ").replaceAll(" ", "_")}"
  }

  def getDate(date: String): String = {
    val dateTimeFormate = DateTimeFormat.forPattern("yyyy-MM-dd")
    val nofDates = date.replaceAll("[^0-9]", "")
    val presentDate = dateTimeFormate.print(DateTime.now(DateTimeZone.UTC).plusDays(1))
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
    redisConnect.set(getCacheKey("0130929928739635202", getDate("LAST_30DAYS"), List("state")), "{\"metrics\":[{\"type\":\"complete\",\"count\":10},{\"type\":\"enrolment\",\"count\":5}],\"groupBy\":[{\"district\":\"PUNE\",\"values\":[{\"count\":1,\"type\":\"enrolment\"},{\"count\":1,\"type\":\"complete\"}],\"state\":\"Maharashtra\"},{\"district\":\"DADRA AND NAGAR HAVELI(UT)\",\"values\":[{\"count\":1,\"type\":\"enrolment\"},{\"count\":1,\"type\":\"complete\"}],\"state\":\"Dadra & Nagar Haveli\"}]}")
    val groupByKeys = new util.ArrayList[String]
    groupByKeys.add("state")
    val response = callActor(getRequest("0130929928739635202", "do_31309287232935526411138", "LAST_30DAYS", groupByKeys), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
    assert(response.getResult != null)
    val result = response.getResult
    assert(result != null)
    val metricsResult = gson.fromJson(gson.toJson(result.get("metrics")), classOf[util.ArrayList[util.Map[String, AnyRef]]])
    val groupByResult = gson.fromJson(gson.toJson(result.get("groupBy")), classOf[util.ArrayList[util.Map[String, AnyRef]]])
    metricsResult.isEmpty should be(false)
    groupByResult.isEmpty should be(false)
    groupByResult.size() should be(2)
    metricsResult.size() should be(2)
    groupByResult.asScala.map(x => {
      x.get("district") should not be (null)
      x.get("state") should not be (null)
      x.get("values") should not be (null)
    })
  }

  "CollectionSummaryActivityAgg" should "return success response from druid" in {
    val groupByKeys = new util.ArrayList[String]
    groupByKeys.add("dist")
    groupByKeys.add("state")
    mockDruid("[{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":null,\"userCount\":2.000977198748901,\"edata_type\":\"enrol\",\"state\":null}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":null,\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":null}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"PUNE\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Maharashtra\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"Tumkur\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Karnataka\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"DADRA AND NAGAR HAVELI(UT)\",\"userCount\":1.0002442201269182,\"edata_type\":\"enrol\",\"state\":\"Dadra & Nagar Haveli\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"PUNE\",\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":\"Maharashtra\"}},{\"version\":\"v1\",\"timestamp\":\"1901-01-01T00:00:00.000Z\",\"event\":{\"district\":\"DADRA AND NAGAR HAVELI(UT)\",\"userCount\":1.0002442201269182,\"edata_type\":\"complete\",\"state\":\"Dadra & Nagar Haveli\"}}]")
    val query = "{\"request\":{\"filters\":{\"collectionId\":\"do_31309287232935526411138\",\"batchId\":\"0130929928739635201\"},\"groupBy\":[],\"intervals\":\"20120-01-23/2020-09-24\"}}"
    Unirest.post(s"http://localhost:8082/obsrv/v1/query").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)
    val response = callActor(getRequest("0130929928739635201", "do_31309287232935526411138", "LAST_7DAYS", groupByKeys), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
    val result = response.getResult
    assert(result != null)

    result.get("batchId") should be("0130929928739635201")
    result.get("collectionId") should be("do_31309287232935526411138")
    result.get("lastUpdatedOn") should not be(null)
    val metricsResult = gson.fromJson(gson.toJson(result.get("metrics")), classOf[util.ArrayList[util.Map[String, AnyRef]]])
    val groupByResult = gson.fromJson(gson.toJson(result.get("groupBy")), classOf[util.ArrayList[util.Map[String, AnyRef]]])
    metricsResult.isEmpty should be(false)
    groupByResult.isEmpty should be(false)
    groupByResult.size() should be(4)
    metricsResult.size() should be(2)
  }
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
    server.url("http://localhost:8082/obsrv/v1/query")
    val headers = new util.HashMap[String, String]()
    headers.put("Authorization", "")
  }

}
