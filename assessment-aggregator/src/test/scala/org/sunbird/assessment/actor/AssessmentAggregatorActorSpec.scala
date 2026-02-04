package org.sunbird.assessment.actor

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.assessment.models._
import org.sunbird.common.models.util.JsonKey
import java.util.HashMap
import scala.collection.JavaConverters._

class AssessmentAggregatorActorSpec extends TestKit(ActorSystem("AssessmentAggregatorActorSpec"))
  with ImplicitSender with AnyFlatSpecLike with Matchers with BeforeAndAfterAll with MockitoSugar {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "AssessmentAggregatorActor" should "send back error response for unknown message" in {
    val actorRef = TestActorRef(new AssessmentAggregatorActor())
    actorRef ! "unknown"
    val response = expectMsgType[org.sunbird.common.exception.ProjectCommonException]
    response.getResponseCode should be (400)
    response.getCode should be ("INVALID_MESSAGE")
  }

  it should "process a single assessment request" in {
    val mRedis = mock[org.sunbird.assessment.service.RedisService]
    val mContent = mock[org.sunbird.assessment.service.ContentService]
    val mCassandra = mock[org.sunbird.assessment.service.CassandraService]
    val mKafka = mock[org.sunbird.assessment.service.KafkaService]
    
    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))
    when(mCassandra.getUserAssessments(anyString, anyString, anyString, anyString, any[RequestContext])).thenReturn(List.empty)

    val actorRef = TestActorRef(new AssessmentAggregatorActor(Some(mRedis), Some(mContent), Some(mCassandra), Some(mKafka)))
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    body.put(JsonKey.USER_ID, "u1")
    body.put(JsonKey.COURSE_ID, "c1")
    body.put(JsonKey.BATCH_ID, "b1")
    body.put(JsonKey.CONTENT_ID, "cont1")
    body.put(JsonKey.EVENTS, List.empty.asJava)
    request.setRequest(body)
    
    actorRef ! request
    val response = expectMsgType[org.sunbird.common.models.response.Response]
    response.get("response") should be ("SUCCESS")
    verify(mCassandra).saveAssessment(any[AssessmentResult], any[RequestContext])
  }

  it should "handle batch assessment requests" in {
    val mRedis = mock[org.sunbird.assessment.service.RedisService]
    val mContent = mock[org.sunbird.assessment.service.ContentService]
    val mCassandra = mock[org.sunbird.assessment.service.CassandraService]
    val mKafka = mock[org.sunbird.assessment.service.KafkaService]

    when(mRedis.isValidContent(anyString, anyString)).thenReturn(true)
    when(mRedis.getTotalQuestionsCount(anyString)).thenReturn(Some(10))

    val actorRef = TestActorRef(new AssessmentAggregatorActor(Some(mRedis), Some(mContent), Some(mCassandra), Some(mKafka)))
    val request = new Request()
    request.setRequestContext(mock[RequestContext])
    val body = new HashMap[String, AnyRef]()
    val events = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    val item = new HashMap[String, AnyRef]()
    item.put(JsonKey.USER_ID, "u1")
    item.put(JsonKey.COURSE_ID, "c1")
    item.put(JsonKey.BATCH_ID, "b1")
    item.put(JsonKey.CONTENT_ID, "cont1")
    events.add(item)
    body.put(JsonKey.ASSESSMENT_EVENTS, events)
    request.setRequest(body)

    actorRef ! request
    val response = expectMsgType[org.sunbird.common.models.response.Response]
    response.get("response") should be ("SUCCESS")
  }
}
