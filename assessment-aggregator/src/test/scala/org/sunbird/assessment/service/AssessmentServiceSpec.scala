package org.sunbird.assessment.service

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.assessment.models._
import org.sunbird.common.request.RequestContext

class AssessmentServiceSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  val mockRedis = mock[RedisService]
  val mockContent = mock[ContentService]
  val assessmentService = new AssessmentService(mockRedis, mockContent)

  "AssessmentService" should "filter unique questions keeping the latest" in {
    val events = List(
      AssessmentEvent("q1", 2, 5, 10, 1000L),
      AssessmentEvent("q1", 4, 5, 15, 2000L),
      AssessmentEvent("q2", 5, 5, 5, 1500L)
    )
    
    val unique = assessmentService.getUniqueQuestions(events)
    unique.size should be (2)
    unique.find(_.questionId == "q1").get.score should be (4)
    unique.find(_.questionId == "q2").get.score should be (5)
  }

  it should "compute correct score metrics" in {
    val events = List(
      AssessmentEvent("q1", 2.0, 5.0, 10, 1000L),
      AssessmentEvent("q2", 3.0, 5.0, 10, 1000L)
    )
    
    val metrics = assessmentService.computeScoreMetrics(events)
    metrics.totalScore should be (5.0)
    metrics.totalMaxScore should be (10.0)
    metrics.grandTotal should be ("5/10")
    metrics.questions.size should be (2)
  }

  it should "fetch metadata from Redis when available" in {
    when(mockRedis.isValidContent("c1", "cont1")).thenReturn(true)
    when(mockRedis.getTotalQuestionsCount("cont1")).thenReturn(Some(10))
    
    val metadata = assessmentService.getMetadata("c1", "cont1", null)
    metadata.isValid should be (true)
    metadata.totalQuestions should be (10)
    verifyNoMoreInteractions(mockContent)
  }

  it should "fetch metadata from Content Service when Redis is empty" in {
    when(mockRedis.isValidContent("c1", "cont1")).thenReturn(false)
    when(mockRedis.getTotalQuestionsCount("cont1")).thenReturn(None)
    val ctx = mock[RequestContext]
    when(mockContent.fetchMetadata(anyString, any[RequestContext])).thenReturn(ContentMetadata(isValid = true, totalQuestions = 15))
    
    val metadata = assessmentService.getMetadata("c1", "cont1", ctx)
    metadata.isValid should be (true)
    metadata.totalQuestions should be (15)
  }

  it should "compute user aggregates correctly" in {
    val assessments = List(
      ExistingAssessment("a1", "cont1", 2000L, 1000L, 5.0, 10.0, List.empty),
      ExistingAssessment("a2", "cont1", 3000L, 1000L, 8.0, 10.0, List.empty)
    )
    
    val agg = assessmentService.computeUserAggregates("u1", "c1", "b1", assessments)
    agg.aggregates("score:cont1") should be (8.0)
    agg.aggregates("attempts_count:cont1") should be (2.0)
    agg.aggregateDetails.size should be (2)
  }
}
