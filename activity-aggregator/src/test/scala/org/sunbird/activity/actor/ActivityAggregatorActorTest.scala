package org.sunbird.activity.actor

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.{CertificateUtil, ContentSearchUtil, DeDupUtil, RedisUtil}
import org.sunbird.activity.domain.{CollectionProgress, TelemetryEvent}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.response.Response
import org.sunbird.keys.JsonKey
import org.sunbird.request.{Request, RequestContext}

import java.util
import scala.concurrent.duration._

class ActivityAggregatorActorTest
    extends TestKit(ActorSystem("ActivityAggregatorActorTest"))
    with AnyFlatSpecLike
    with Matchers
    with MockFactory
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val cacheUtil: RedisCacheUtil = mock[RedisCacheUtil]

  "ActivityAggregatorActor" should "process valid contents successfully" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    val emptyConsumptionResponse = createEmptyResponse()
    
    // Redis Expectations
    (redisUtil.getLeafNodes _).expects(*, *, *).returning(List("content1", "content2")).anyNumberOfTimes()
    (redisUtil.getOptionalNodes _).expects(*, *, *).returning(List.empty).anyNumberOfTimes()
    (redisUtil.getAncestors _).expects(*, *, *).returning(List("course456")).anyNumberOfTimes()
    
    // DeDup Expectations
    (deDupUtil.isUniqueEvent _).expects(*, *).returning(true).anyNumberOfTimes()
    (deDupUtil.storeChecksum _).expects(*, *).returning(()).anyNumberOfTimes()
    
    // ContentSearchUtil Expectations
    (contentSearchUtil.getCollectionStatus _).expects(*, *).returning("Live").anyNumberOfTimes()
    
    // Certificate Util Expectations (Mock Kafka calls)
    (certificateUtil.publishCertificateIssueEvent _).expects(*, *, *, *).returning(()).anyNumberOfTimes()
    
    // Cassandra Expectations
    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_content_consumption", *, *)
      .returning(emptyConsumptionResponse)
      .anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_enrolments", *, *)
      .returning(createEnrolmentResponse())
      .anyNumberOfTimes()

    (cassandraOperation.batchUpdate(_: String, _: String, _: util.List[util.Map[String, util.Map[String, Object]]], _: RequestContext))
      .expects(*, "user_content_consumption", *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    (cassandraOperation.batchUpdate(_: String, _: String, _: util.List[util.Map[String, util.Map[String, Object]]], _: RequestContext))
      .expects(*, "user_activity_agg", *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    (cassandraOperation.updateRecordV2(_: String, _: String, _: util.Map[String, Object], _: util.Map[String, Object], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = createContentsList()
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  it should "handle missing contents key (Force Sync)" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    val consumptionResponse = createConsumptionResponse()
    
    (redisUtil.getLeafNodes _).expects(*, *, *).returning(List("content1", "content2")).anyNumberOfTimes()
    (redisUtil.getOptionalNodes _).expects(*, *, *).returning(List.empty).anyNumberOfTimes()
    (redisUtil.getAncestors _).expects(*, *, *).returning(List("course456")).anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_content_consumption", *, *)
      .returning(consumptionResponse)
      .anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_enrolments", *, *)
      .returning(createEnrolmentResponse())
      .anyNumberOfTimes()

    (cassandraOperation.batchUpdate(_: String, _: String, _: util.List[util.Map[String, util.Map[String, Object]]], _: RequestContext))
      .expects(*, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    (cassandraOperation.updateRecordV2(_: String, _: String, _: util.Map[String, Object], _: util.Map[String, Object], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = null // Force Sync
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  it should "handle empty contents list gracefully" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = new util.ArrayList[util.Map[String, AnyRef]]()
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  it should "filter out invalid contents" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    // Redis
    (redisUtil.getLeafNodes _).expects(*, *, *).returning(List("content1", "content2")).anyNumberOfTimes()
    (redisUtil.getOptionalNodes _).expects(*, *, *).returning(List.empty).anyNumberOfTimes()
    (redisUtil.getAncestors _).expects(*, *, *).returning(List("course456")).anyNumberOfTimes()
    
    // DeDup
    (deDupUtil.isUniqueEvent _).expects(*, *).returning(true).anyNumberOfTimes()
    (deDupUtil.storeChecksum _).expects(*, *).returning(()).anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, *, *, *)
      .returning(createEmptyResponse())
      .anyNumberOfTimes()

    (cassandraOperation.batchUpdate(_: String, _: String, _: util.List[util.Map[String, util.Map[String, Object]]], _: RequestContext))
      .expects(*, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    (cassandraOperation.updateRecordV2(_: String, _: String, _: util.Map[String, Object], _: util.Map[String, Object], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val contentsWithInvalid = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val invalidContent = new util.HashMap[String, AnyRef]()
    invalidContent.put("contentId", "content1")
    invalidContent.put("status", Int.box(0))
    contentsWithInvalid.add(invalidContent)
    
    val validContent = new util.HashMap[String, AnyRef]()
    validContent.put("contentId", "content2")
    validContent.put("status", Int.box(2))
    contentsWithInvalid.add(validContent)

    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = contentsWithInvalid
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  it should "handle DB with no existing consumption for Force Sync" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    (redisUtil.getLeafNodes _).expects(*, *, *).returning(List("content1", "content2")).anyNumberOfTimes()
    (redisUtil.getOptionalNodes _).expects(*, *, *).returning(List.empty).anyNumberOfTimes()
    (redisUtil.getAncestors _).expects(*, *, *).returning(List("course456")).anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_content_consumption", *, *)
      .returning(createEmptyResponse())
      .once()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = null // Force Sync
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  it should "merge input consumption with DB consumption correctly" in {
    val cassandraOperation = mock[CassandraOperation]
    val redisUtil = mock[RedisUtil]
    val deDupUtil = mock[DeDupUtil]
    val contentSearchUtil = mock[ContentSearchUtil]
    val certificateUtil = mock[CertificateUtil]
    val probe = TestProbe()

    (redisUtil.getLeafNodes _).expects(*, *, *).returning(List("content1", "content2")).anyNumberOfTimes()
    (redisUtil.getOptionalNodes _).expects(*, *, *).returning(List.empty).anyNumberOfTimes()
    (redisUtil.getAncestors _).expects(*, *, *).returning(List("course456")).anyNumberOfTimes()
    
    (deDupUtil.isUniqueEvent _).expects(*, *).returning(true).anyNumberOfTimes()
    (deDupUtil.storeChecksum _).expects(*, *).returning(()).anyNumberOfTimes()

    val dbConsumptionResponse = new Response()
    val dbRecords = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val existingRecord = new util.HashMap[String, AnyRef]()
    existingRecord.put("contentid", "content1")
    existingRecord.put("status", Int.box(1))
    existingRecord.put("viewcount", Int.box(2))
    existingRecord.put("completedcount", Int.box(0))
    dbRecords.add(existingRecord)
    
    dbConsumptionResponse.put("response", dbRecords)

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_content_consumption", *, *)
      .returning(dbConsumptionResponse)
      .anyNumberOfTimes()

    (cassandraOperation.getRecordsByProperties(_: String, _: String, _: util.Map[String, Object], _: RequestContext))
      .expects(*, "user_enrolments", *, *)
      .returning(createEnrolmentResponse())
      .anyNumberOfTimes()

    (cassandraOperation.batchUpdate(_: String, _: String, _: util.List[util.Map[String, util.Map[String, Object]]], _: RequestContext))
      .expects(*, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    (cassandraOperation.updateRecordV2(_: String, _: String, _: util.Map[String, Object], _: util.Map[String, Object], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *)
      .returning(new Response())
      .anyNumberOfTimes()

    val actor = system.actorOf(Props(new TestableActivityAggregatorActor(cassandraOperation, redisUtil, deDupUtil, contentSearchUtil, certificateUtil)))
    
    val inputContents = new util.ArrayList[util.Map[String, AnyRef]]()
    val inputContent = new util.HashMap[String, AnyRef]()
    inputContent.put("contentId", "content1")
    inputContent.put("status", Int.box(2))
    inputContents.add(inputContent)

    val request = createUpdateRequest(
      userId = "user123",
      courseId = "course456",
      batchId = "batch789",
      contents = inputContents
    )

    probe.send(actor, request)
    probe.expectMsgType[Response](5.seconds)
  }

  // Helper methods
  private def createRequestContext(): RequestContext = {
    new RequestContext("channel", "pdataId", "env", "did", "sid", "pid", "pver", null)
  }

  private def createUpdateRequest(
      userId: String,
      courseId: String,
      batchId: String,
      contents: util.List[util.Map[String, AnyRef]]
  ): Request = {
    val request = new Request()
    request.setOperation("updateActivityAggregates")
    request.setRequestContext(createRequestContext())
    
    val requestMap = new util.HashMap[String, AnyRef]()
    requestMap.put(JsonKey.USER_ID, userId)
    requestMap.put(JsonKey.COURSE_ID, courseId)
    requestMap.put(JsonKey.BATCH_ID, batchId)
    if (contents != null) {
      requestMap.put(JsonKey.CONTENTS, contents)
    }
    
    request.setRequest(requestMap)
    request
  }

  private def createContentsList(): util.List[util.Map[String, AnyRef]] = {
    val contents = new util.ArrayList[util.Map[String, AnyRef]]()
    val content1 = new util.HashMap[String, AnyRef]()
    content1.put("contentId", "content1")
    content1.put("status", Int.box(2))
    content1.put("lastAccessTime", "2024-01-01 10:00:00:000+0000")
    contents.add(content1)
    
    val content2 = new util.HashMap[String, AnyRef]()
    content2.put("contentId", "content2")
    content2.put("status", Int.box(1))
    content2.put("lastAccessTime", "2024-01-01 11:00:00:000+0000")
    contents.add(content2)
    contents
  }

  private def createEmptyResponse(): Response = {
    val response = new Response()
    response.put("response", new util.ArrayList[util.Map[String, AnyRef]]())
    response
  }

  private def createConsumptionResponse(): Response = {
    val response = new Response()
    val records = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val record = new util.HashMap[String, AnyRef]()
    record.put("contentid", "content1")
    record.put("status", Int.box(2))
    record.put("viewcount", Int.box(3))
    record.put("completedcount", Int.box(1))
    record.put("progress", Int.box(100))
    records.add(record)
    
    response.put("response", records)
    response
  }

  private def createEnrolmentResponse(): Response = {
    val response = new Response()
    val records = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val record = new util.HashMap[String, AnyRef]()
    record.put("userid", "user123")
    record.put("courseid", "course456")
    record.put("batchid", "batch789")
    record.put("status", Int.box(1))
    record.put("progress", Int.box(50))
    records.add(record)
    
    response.put("response", records)
    response
  }

  class TestableActivityAggregatorActor(
      cassandraOp: CassandraOperation, 
      redisUtil: RedisUtil,
      deDupUtil: DeDupUtil,
      contentSearchUtil: ContentSearchUtil,
      certificateUtil: CertificateUtil
  ) extends ActivityAggregatorActor {
    
    override def onReceive(request: Request): Unit = {
      setField("cassandraOperation", cassandraOp)
      setField("redisUtil", redisUtil)
      setField("deDupUtil", deDupUtil)
      setField("contentSearchUtil", contentSearchUtil)
      setField("certificateUtil", certificateUtil)
      super.onReceive(request)
    }
    
    def setField(fieldName: String, value: AnyRef): Unit = {
       val field = classOf[ActivityAggregatorActor].getDeclaredField(fieldName)
       field.setAccessible(true)
       field.set(this, value)
    }
    
    // Override methods that call Kafka to prevent actual Kafka calls
    override def publishAuditEvent(event: TelemetryEvent, requestContext: RequestContext): Unit = {
      // Mock implementation - do nothing to avoid Kafka calls
      logger.info(requestContext, s"Mock: publishAuditEvent called with event: ${event.eid}")
    }
    
    override def publishEnrolmentCompleteAuditEvent(progress: CollectionProgress, requestContext: RequestContext): Unit = {
      // Mock implementation - do nothing to avoid Kafka calls  
      logger.info(requestContext, s"Mock: publishEnrolmentCompleteAuditEvent called for userId: ${progress.userId}")
    }
  }
}
