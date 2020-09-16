package org.sunbird.aggregate

import java.io.IOException
import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.mashape.unirest.http.Unirest
import javax.ws.rs.core.MediaType
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.apache.http.HttpHeaders
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

import scala.concurrent.duration.FiniteDuration

class CollectionSummaryAggregateTest extends FlatSpec with Matchers with BeforeAndAfterAll with MockFactory {

  val system = ActorSystem.create("system")


  var redisServer: RedisServer = _
  redisServer = new RedisServer(6379)
  redisServer.start()

  var jedis: Jedis = _
  var server = new MockWebServer
  server.start(3000)
  override def afterAll() {
    super.afterAll()
    redisServer.stop();

  }

  override def beforeAll() {
    if (!redisServer.isActive()) {
      redisServer.start();
    }
  }

  def getCacheKey(batchId: String, intervals: String): String = {
    val date = intervals.split("/")
    val startDate = date.lift(0).getOrElse("2020901")
    val endDate = date.lift(1).getOrElse("20200901")
    s"bmetircs:$batchId:$startDate:$endDate"
  }

  private def getUpdatedHeaders(headers: util.Map[String, String]): util.Map[String, String] = {
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    headers.put("Connection", "Keep-Alive")
    headers
  }

  "CollectionSummaryActivityAgg" should "return success response from redis" in {
    startServer("[{\"timestamp\":\"2019-11-19T00:00:00.000Z\",\"result\":{\"count\":0}}]")
    val query = "{\"queryType\":\"timeseries\",\"dataSource\":\"summary-events\",\"aggregations\":[{\"type\":\"count\",\"name\":\"count\"}],\"granularity\":\"all\",\"postAggregations\":[],\"intervals\":\"2019-11-19T00:00:00+00:00/2019-11-19T00:00:00+00:00\"}"
    Unirest.post(s"http://localhost:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)

    val redisConnect = new RedisCacheUtil()
    redisConnect.set(getCacheKey("0130929928739635202", "2019-09-21/2019-09-21"), "\"{\\\"error\\\":\\\"FORBIDDEN\\\",\\\"errorMessage\\\":\\\"Date Range(intervals) can not be more than \\\\\\\"30\\\\\\\" day's\\\\\\\"\\\",\\\"isValid\\\":false}\"")
    val response = callActor(getRequest("0130929928739635202", "do_31309287232935526411138", "2019-09-23/2019-09-24"), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
  }

  "CollectionSummaryActivityAgg" should "return success response from druid" in {
    startServer("[{\"timestamp\":\"2019-11-19T00:00:00.000Z\",\"result\":{\"count\":0}}]")
    val query = "{\"queryType\":\"timeseries\",\"dataSource\":\"summary-events\",\"aggregations\":[{\"type\":\"count\",\"name\":\"count\"}],\"granularity\":\"all\",\"postAggregations\":[],\"intervals\":\"2019-11-19T00:00:00+00:00/2019-11-19T00:00:00+00:00\"}"
    Unirest.post(s"http://localhost:8082/druid/v2/").headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(query)
    val response = callActor(getRequest("batch-001", "course-001", "1993-08-24/1993-08-25"), Props(new CollectionSummaryAggregate()(new RedisCacheUtil())))
    assert(response.getResponseCode == ResponseCode.OK)
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

  def getRequest(batchId: String, courseId: String, date: String): Request = {
    val custom = new util.HashMap[String, AnyRef]()
    val filterMap = new util.HashMap[String, AnyRef]()
    filterMap.put("collectionId", courseId)
    filterMap.put("batchId", batchId)
    custom.put("filters", filterMap)
    custom.put("intervals", date)
    val request = new Request
    request.setRequest(custom)
    request
  }

  def getInvalidRequest(): Request = {
    val request = new Request()
    request.setOperation("groupActivity")
    request.put("groupId", "groupid")
    request.put("activityId", "activityid")
    request.put("activityType", "activitytype")
    request
  }

  def defaultStringHandler(name: String): String = {
    ""
  }

  @throws[IOException]
  def startServer(response: String): Unit = {
    server.enqueue(new MockResponse().setBody(response))
    server.enqueue(new MockResponse().setHeader("Authorization", ""))
    server.url("http://localhost:8082/druid/v2/")
    val headers = new util.HashMap[String, String]()
    headers.put("Authorization", "")
  }


}

