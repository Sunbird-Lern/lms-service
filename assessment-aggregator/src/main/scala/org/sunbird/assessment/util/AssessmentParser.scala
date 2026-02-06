package org.sunbird.assessment.util

import org.sunbird.assessment.models.AssessmentEvent
import scala.collection.JavaConverters._
import com.google.gson.Gson
import org.sunbird.keys.JsonKey

object AssessmentParser {
  private val gson = new Gson()

  def mapToEvent(m: java.util.Map[String, AnyRef]): AssessmentEvent = {
    if (m.containsKey("edata")) {
      val edata = m.get("edata").asInstanceOf[java.util.Map[String, AnyRef]]
      val item = edata.getOrDefault("item", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
      val resvalues = getListValues(edata.getOrDefault("resvalues", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      val params = getListValues(item.getOrDefault("params", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      AssessmentEvent(
        item.getOrDefault("id", "").toString,
        getD(edata, "score"),
        getD(item, "maxscore"),
        getD(edata, "duration"),
        Option(m.get("ets")).map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis()),
        item.getOrDefault("type", "").toString,
        item.getOrDefault("title", "").toString,
        item.getOrDefault("desc", "").toString,
        resvalues.asJava,
        params.asJava
      )
    } else {
      val resvalues = getListValues(m.getOrDefault("resvalues", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      val params = getListValues(m.getOrDefault("params", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      AssessmentEvent(
        Option(m.get("questionId")).getOrElse(m.getOrDefault(JsonKey.IDENTIFIER, "")).toString,
        getD(m, "score"),
        getD(m, "maxScore"),
        getD(m, "duration"),
        Option(m.get("timestamp")).map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis()),
        m.getOrDefault("questionType", "").toString,
        m.getOrDefault("title", "").toString,
        m.getOrDefault("description", "").toString,
        resvalues.asJava,
        params.asJava
      )
    }
  }

  private def getD(m: java.util.Map[String, AnyRef], k: String): Double = Option(m.get(k)).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)

  private def getListValues(values: java.util.List[java.util.Map[String, AnyRef]]): List[java.util.Map[String, AnyRef]] = {
    values.asScala.map { res =>
      res.asScala.map {
        case (key, value) =>
          val finalValue = if (null != value && !value.isInstanceOf[String]) gson.toJson(value) else value
          key -> finalValue.asInstanceOf[AnyRef]
      }.asJava
    }.toList
  }
}
