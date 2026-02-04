package org.sunbird.assessment.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.common.models.util.JsonKey
import java.util.HashMap

class AssessmentParserSpec extends AnyFlatSpec with Matchers {

  "AssessmentParser" should "convert a map to an AssessmentEvent correctly" in {
    val map = new HashMap[String, AnyRef]()
    map.put(JsonKey.IDENTIFIER, "q1")
    map.put("score", 4.0.asInstanceOf[AnyRef])
    map.put("maxScore", 5.0.asInstanceOf[AnyRef])
    map.put("timestamp", 123456L.asInstanceOf[AnyRef])
    
    val event = AssessmentParser.mapToEvent(map)
    event.questionId should be ("q1")
    event.score should be (4.0)
    event.maxScore should be (5.0)
    event.timestamp should be (123456L)
  }

  it should "handle missing values with defaults" in {
    val map = new HashMap[String, AnyRef]()
    map.put(JsonKey.IDENTIFIER, "q2")
    
    val event = AssessmentParser.mapToEvent(map)
    event.questionId should be ("q2")
    event.score should be (0.0)
    event.maxScore should be (0.0)
    event.timestamp should be > 0L
  }
}
