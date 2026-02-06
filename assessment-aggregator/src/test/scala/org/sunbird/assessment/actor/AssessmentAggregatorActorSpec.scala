package org.sunbird.assessment.actor

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.assessment.models._
import org.sunbird.keys.JsonKey
import org.sunbird.common.PropertiesCache
import org.sunbird.exception.ProjectCommonException
import org.sunbird.response.Response
import java.util.HashMap
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import org.sunbird.assessment.service.{CassandraService, ContentMetadata, ContentService, KafkaService, RedisService}
import org.sunbird.response.ResponseCode

class AssessmentAggregatorActorSpec extends TestKit(ActorSystem("AssessmentAggregatorActorSpec"))
  with ImplicitSender with AnyFlatSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar {

  val mRedis = mock[RedisService]
  val mContent = mock[ContentService]
  val mCassandra = mock[CassandraService]
  val mKafka = mock[KafkaService]

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def getActorRef = TestActorRef(new AssessmentAggregatorActor(Some(mRedis), Some(mContent), Some(mCassandra), Some(mKafka)))

  "AssessmentAggregatorActor" should "silently ignore unknown message types (standard BaseActor behavior)" in {
    val actorRef = getActorRef
    actorRef ! "unknown"
    expectNoMessage(500.millis)
  }

  it should "process a single assessment request with events" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val existing = ExistingAssessment("a1", "cont1", System.currentTimeMillis(), System.currentTimeMillis(), 10.0, 10.0, List.empty)
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getAssessment(anyString, anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(None)
    when(mCassandra.getUserAssessments(anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(List(existing))

    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.USER_ID, "u1")
    body.put(JsonKey.COURSE_ID, "c1")
    body.put(JsonKey.BATCH_ID, "b1")
    body.put(JsonKey.CONTENT_ID, "cont1")
    
    val events = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val event = new HashMap[String, AnyRef]()
    event.put("questionId", "q1")
    event.put("score", 1.0.asInstanceOf[AnyRef])
    event.put("maxScore", 1.0.asInstanceOf[AnyRef])
    event.put("timestamp", System.currentTimeMillis().asInstanceOf[AnyRef])
    events.add(event)
    body.put(JsonKey.EVENTS, events)
    request.setRequest(body)
    
    actorRef ! request
    val response = expectMsgType[Response]
    response.get("response") should be ("SUCCESS")
    verify(mCassandra).saveAssessment(any[AssessmentResult], any[RequestContext])
    verify(mCassandra).updateUserActivity(anyString, anyString, anyString, any[UserActivityAggregate], any[RequestContext])
  }

  it should "process a sync request (no events) by fetching existing assessments" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val existing = ExistingAssessment("a1", "cont1", System.currentTimeMillis(), System.currentTimeMillis(), 10.0, 10.0, List.empty)
    
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getAssessment(anyString, anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(Some(existing))
    when(mCassandra.getUserAssessments(anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(List(existing))

    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.USER_ID, "u1")
    body.put(JsonKey.COURSE_ID, "c1")
    body.put(JsonKey.BATCH_ID, "b1")
    body.put(JsonKey.CONTENT_ID, "cont1")
    body.put(JsonKey.ATTEMPT_ID, "a1")
    body.put(JsonKey.EVENTS, List.empty.asJava)
    request.setRequest(body)

    actorRef ! request
    val response = expectMsgType[Response]
    response.get("response") should be ("SUCCESS")
    verify(mCassandra).updateUserActivity(anyString, anyString, anyString, any[UserActivityAggregate], any[RequestContext])
  }

  it should "handle batch assessment requests from aggregator API" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val existing = ExistingAssessment("a1", "cont1", System.currentTimeMillis(), System.currentTimeMillis(), 10.0, 10.0, List.empty)
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getAssessment(anyString, anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(None)
    when(mCassandra.getUserAssessments(anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(List(existing))

    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    
    val assessments = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val item = new HashMap[String, AnyRef]()
    item.put(JsonKey.USER_ID, "u1")
    item.put(JsonKey.COURSE_ID, "c1")
    item.put(JsonKey.BATCH_ID, "b1")
    item.put(JsonKey.CONTENT_ID, "cont1")
    
    val events = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val event = new HashMap[String, AnyRef]()
    event.put("questionId", "q1")
    event.put("score", 1.0.asInstanceOf[AnyRef])
    events.add(event)
    item.put(JsonKey.EVENTS, events)
    
    assessments.add(item)
    body.put(JsonKey.ASSESSMENT_EVENTS, assessments)
    request.setRequest(body)

    actorRef ! request
    val response = expectMsgType[Response]
    response.get("response") should be ("SUCCESS")
    verify(mCassandra).saveAssessment(any[AssessmentResult], any[RequestContext])
  }

  it should "continue processing batch if one assessment fails" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    
    val assessments = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    // Invalid item (missing userId)
    val item1 = new HashMap[String, AnyRef]()
    item1.put(JsonKey.COURSE_ID, "c1")
    
    // Valid item
    val item2 = new HashMap[String, AnyRef]()
    item2.put(JsonKey.USER_ID, "u1")
    item2.put(JsonKey.COURSE_ID, "c1")
    item2.put(JsonKey.CONTENT_ID, "cont1")
    
    when(mRedis.isValidContent(any[String], any[String])).thenReturn(true)
    when(mCassandra.getAssessment(any[String], any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(None)
    when(mCassandra.getUserAssessments(any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(List.empty)
    
    assessments.add(item1)
    assessments.add(item2)
    body.put(JsonKey.ASSESSMENT_EVENTS, assessments)
    request.setRequest(body)

    actorRef ! request
    expectMsgType[Response]
    verify(mCassandra, atLeastOnce).getAssessment(any, any, any, any, any, any)
  }

  it should "handle different timestamp formats and empty events" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    when(mCassandra.getAssessment(any[String], any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(None)
    when(mCassandra.getUserAssessments(any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(List.empty)
    when(mRedis.isValidContent(any[String], any[String])).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(any[String])).thenReturn(None)
    when(mContent.fetchMetadata(any[String], any[RequestContext])).thenReturn(ContentMetadata(isValid = true, totalQuestions = 0))
    
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.USER_ID, "u1")
    body.put(JsonKey.COURSE_ID, "c1")
    body.put(JsonKey.CONTENT_ID, "cont1")
    body.put("assessmentTimestamp", 123456L.asInstanceOf[AnyRef])
    body.put(JsonKey.EVENTS, null) // Test null events
    request.setRequest(body)
    
    actorRef ! request
    val response = expectMsgType[Response]
    response.get("attemptId").toString should not be empty
  }

  it should "return error response when userId is missing" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.COURSE_ID, "c1")
    request.setRequest(body)
    
    actorRef ! request
    val response = expectMsgType[ProjectCommonException]
    response.getResponseCode should be (400)
  }

  it should "publish certificate event when enabled" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val existing = ExistingAssessment("a1", "cont1", System.currentTimeMillis(), System.currentTimeMillis(), 10.0, 10.0, List.empty)
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getAssessment(anyString, anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(Some(existing))
    when(mCassandra.getUserAssessments(anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(List(existing))
    
    PropertiesCache.getInstance().saveConfigProperty("assessment_aggregator_publish_certificate", "true")

    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.USER_ID, "u1")
    body.put(JsonKey.COURSE_ID, "c1")
    body.put(JsonKey.BATCH_ID, "b1")
    body.put(JsonKey.CONTENT_ID, "cont1")
    body.put(JsonKey.ATTEMPT_ID, "a1")
    body.put(JsonKey.EVENTS, List.empty.asJava)
    request.setRequest(body)

    actorRef ! request
    expectMsgType[Response]
    verify(mKafka).publishCertificateEvent(anyString, anyString, anyString, anyString)
    
    PropertiesCache.getInstance().saveConfigProperty("assessment_aggregator_publish_certificate", "false")
  }

  it should "skip stale assessment" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put("userId", "u1")
    body.put("courseId", "c1")
    body.put("batchId", "b1")
    body.put("contentId", "cont1")
    body.put("assessmentTimestamp", 1000L.asInstanceOf[AnyRef])
    
    // Add one event to reach processAttempt
    val events = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val event = new HashMap[String, AnyRef]()
    event.put("questionId", "q1")
    events.add(event)
    body.put("events", events)
    request.setRequest(body)
    
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getAssessment(anyString, anyString, anyString, anyString, anyString, any[RequestContext]))
      .thenReturn(Some(ExistingAssessment("att1", "cont1", 2000L, 1000L, 5.0, 10.0, List.empty)))
    
    actorRef ! request
    expectMsgType[Response]
    verify(mCassandra, never).saveAssessment(any[AssessmentResult], any[RequestContext])
  }

  it should "throw exception when content validation fails" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    PropertiesCache.getInstance().saveConfigProperty("assessment_enable_content_validation", "true")
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put("userId", "u1")
    body.put("courseId", "c1")
    body.put("batchId", "b1")
    body.put("contentId", "cont1")
    request.setRequest(body)
    
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(false)
    when(mContent.fetchMetadata(anyString, any[RequestContext])).thenReturn(ContentMetadata(isValid = false, 0))
    
    actorRef ! request
    val response = expectMsgType[ProjectCommonException]
    response.getResponseCode should be (ResponseCode.CLIENT_ERROR.getResponseCode)
    PropertiesCache.getInstance().saveConfigProperty("assessment_enable_content_validation", "false")
  }

  it should "handle unexpected exceptions in onReceive" in {
    // Passing a message that might cause an internal exception (e.g., if a mandatory field is missing in Request)
    val actorRef = getActorRef
    val request = new Request()
    request.setRequest(null) // This should cause a NPE in processAggregation
    actorRef ! request
    expectMsgType[ProjectCommonException].getResponseCode should be (500)
  }

  it should "handle missing config for skipMissing (default to true)" in {
    reset(mRedis, mContent, mCassandra, mKafka)
    when(mRedis.isValidContent(any[String], any[String])).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(any[String])).thenReturn(Some(1))
    when(mCassandra.getAssessment(any[String], any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(None)
    when(mCassandra.getUserAssessments(any[String], any[String], any[String], any[String], any[RequestContext])).thenReturn(List.empty)
    
    val actorRef = getActorRef
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put("userId", "u1")
    body.put("courseId", "c1")
    body.put("contentId", "cont1")
    
    val events = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val e1 = new HashMap[String, AnyRef](); e1.put("questionId", "q1")
    val e2 = new HashMap[String, AnyRef](); e2.put("questionId", "q2")
    events.add(e1)
    events.add(e2) // 2 unique events (exceeds totalQuestions=1)
    body.put("events", events)
    request.setRequest(body)

    actorRef ! request
    expectMsgType[Response]
    // Should skip saveAssessment because uniqueEvents.size (2) > totalQuestions (1)
    verify(mCassandra, never).saveAssessment(any, any)
  }
}
