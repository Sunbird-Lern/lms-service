package org.sunbird.assessment.service

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.assessment.models._
import org.sunbird.common.models.util.PropertiesCache
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
    metrics.grandTotal should be ("5.0/10.0")
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

  it should "return latest attemptId from aggregate" in {
    val details = List(
      AttemptDetail("att1", 1000L, 5.0, "cont1", 10.0, "assessment"),
      AttemptDetail("att2", 2000L, 8.0, "cont1", 10.0, "assessment")
    )
    val aggregate = UserActivityAggregate("u1", "c1", "b1", Map("score:cont1" -> 8.0), details)
    assessmentService.getLatestAttemptId(aggregate) should be ("att2")
  }

  it should "validate content based on configuration" in {
    val metadata = ContentMetadata(isValid = false, totalQuestions = 10)
    val req = AssessmentRequest("att", "u1", "c1", "b1", "cont1", 1000L, List.empty)
    
    // Default (validation disabled)
    assessmentService.validateContent(req, metadata) should be (true)
    
    // Enabled but valid
    val validMetadata = ContentMetadata(isValid = true, totalQuestions = 10)
    PropertiesCache.getInstance().saveConfigProperty("assessment_enable_content_validation", "true")
    assessmentService.validateContent(req, validMetadata) should be (true)
    
    // Enabled and invalid
    assessmentService.validateContent(req, metadata) should be (false)
    PropertiesCache.getInstance().saveConfigProperty("assessment_enable_content_validation", "false")
  }

  it should "handle empty assessments in computeUserAggregates" in {
    val agg = assessmentService.computeUserAggregates("u1", "c1", "b1", List.empty)
    agg.aggregates should be (Map.empty)
    agg.aggregateDetails should be (List.empty)
  }

  it should "return empty string for getLatestAttemptId if details are empty" in {
    val aggregate = UserActivityAggregate("u1", "c1", "b1", Map.empty, List.empty)
    assessmentService.getLatestAttemptId(aggregate) should be ("")
  }

  it should "return empty string for getLatestAttemptId if multiple content IDs are present" in {
    val details = List(
      AttemptDetail("att1", 1000L, 5.0, "cont1", 10.0, "assessment"),
      AttemptDetail("att2", 2000L, 8.0, "cont2", 10.0, "assessment")
    )
    val aggregate = UserActivityAggregate("u1", "c1", "b1", Map("score:cont1" -> 5.0, "score:cont2" -> 8.0), details)
    assessmentService.getLatestAttemptId(aggregate) should be ("")
  }
}
