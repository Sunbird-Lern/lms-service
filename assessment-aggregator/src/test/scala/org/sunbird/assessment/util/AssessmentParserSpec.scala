package org.sunbird.assessment.util

import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.assessment.models.AssessmentEvent
import java.util
import java.util.HashMap
import scala.collection.JavaConverters._

class AssessmentParserSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "AssessmentParser" should "convert a map to an AssessmentEvent correctly" in {
    val map = new HashMap[String, AnyRef]()
    map.put("questionId", "q1")
    map.put("score", 1.0.asInstanceOf[AnyRef])
    map.put("maxScore", 1.0.asInstanceOf[AnyRef])
    map.put("duration", 10.0.asInstanceOf[AnyRef])
    map.put("timestamp", 123456789L.asInstanceOf[AnyRef])
    map.put("questionType", "mcq")
    map.put("title", "Question 1")
    map.put("description", "Desc 1")
    
    val event = AssessmentParser.mapToEvent(map)
    
    event.questionId should be ("q1")
    event.score should be (1.0)
    event.maxScore should be (1.0)
    event.duration should be (10.0)
    event.timestamp should be (123456789L)
    event.questionType should be ("mcq")
    event.title should be ("Question 1")
    event.description should be ("Desc 1")
  }

  it should "handle missing values with defaults" in {
    val map = new HashMap[String, AnyRef]()
    map.put("questionId", "q2")
    
    val event = AssessmentParser.mapToEvent(map)
    event.questionId should be ("q2")
    event.score should be (0.0)
    event.maxScore should be (0.0)
    event.duration should be (0.0)
    event.timestamp should be > 0L
    event.questionType should be ("")
  }

  it should "convert Sunbird V3 telemetry (edata) correctly" in {
    val edata = new HashMap[String, AnyRef]()
    val item = new HashMap[String, AnyRef]()
    item.put("id", "q3")
    item.put("maxscore", 5.0.asInstanceOf[AnyRef])
    item.put("type", "mcq")
    edata.put("item", item)
    edata.put("score", 3.0.asInstanceOf[AnyRef])
    edata.put("duration", 15.0.asInstanceOf[AnyRef])
    
    val map = new HashMap[String, AnyRef]()
    map.put("edata", edata)
    map.put("ets", 987654L.asInstanceOf[AnyRef])
    
    val event = AssessmentParser.mapToEvent(map)
    event.questionId should be ("q3")
    event.score should be (3.0)
    event.maxScore should be (5.0)
    event.duration should be (15.0)
    event.timestamp should be (987654L)
    event.questionType should be ("mcq")
  }

  it should "handle complex objects in resvalues and params" in {
    val map = new HashMap[String, AnyRef]()
    map.put("questionId", "q4")
    
    val resvalues = new util.ArrayList[util.Map[String, AnyRef]]()
    val res1 = new HashMap[String, AnyRef]()
    val nested = new HashMap[String, String]()
    nested.put("nested", "val")
    res1.put("value", nested)
    resvalues.add(res1)
    map.put("resvalues", resvalues)
    
    val event = AssessmentParser.mapToEvent(map)
    // The nested map should be converted to JSON string
    event.resvalues.get(0).get("value").toString should include ("nested")
    event.resvalues.get(0).get("value").toString should include ("val")
  }
}
