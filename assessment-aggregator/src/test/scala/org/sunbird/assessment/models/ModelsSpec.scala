package org.sunbird.assessment.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModelsSpec extends AnyFlatSpec with Matchers {

  "AssessmentRequest" should "be initialized correctly" in {
    val req = AssessmentRequest("a1", "u1", "c1", "b1", "cont1", 1000L, List.empty)
    req.attemptId should be ("a1")
    req.ignoreTimestampValidation should be (false)
  }

  "AssessmentEvent" should "be initialized with defaults" in {
    val event = AssessmentEvent("q1", 1.0, 1.0, 10.0, 1000L)
    event.questionType should be ("")
    event.resvalues should not be null
  }

  "AttemptDetail" should "convert to JSON correctly" in {
    val detail = AttemptDetail("att1", 1609459200000L, 10.0, "cont1", 10.0) // 2021-01-01
    val json = detail.toJson
    json should include ("att1")
    json should include ("cont1")
    // Date format check (Gson default or specified)
    json should include ("2021")
  }

  "UserActivityAggregate" should "be initialized correctly" in {
    val agg = UserActivityAggregate("u1", "c1", "b1", Map("score" -> 10.0), List.empty)
    agg.userId should be ("u1")
    agg.aggregates("score") should be (10.0)
  }

  "ExistingAssessment" should "handle default empty questions" in {
    val existing = ExistingAssessment("a1", "cont1", 1000L, 1000L, 10.0, 10.0)
    existing.questions should be (List.empty)
  }

  "ScoreMetrics" should "be initialized correctly" in {
    val metrics = ScoreMetrics(10.0, 10.0, "10/10", List.empty)
    metrics.grandTotal should be ("10/10")
  }
}
