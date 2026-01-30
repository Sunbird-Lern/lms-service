package org.sunbird.enrolments

import com.datastax.driver.core.{UDTValue, UserType}
import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import org.sunbird.common.request.RequestContext
import org.sunbird.helper.ServiceFactory
import java.util
import scala.collection.JavaConverters._

object AssessmentAuditRecorder {
  private val logger = new LoggerUtil(AssessmentAuditRecorder.getClass)
  private val mapper = new ObjectMapper()
  private val cassandraOperation = ServiceFactory.getInstance
  
  def record(assessment: util.Map[String, AnyRef], udtType: UserType, ctx: RequestContext): String = {
    try {
      val userId = assessment.get(JsonKey.USER_ID).asInstanceOf[String]
      val contentId = assessment.get(JsonKey.CONTENT_ID).asInstanceOf[String]
      val attemptId = getAttemptId(assessment, userId, contentId)
      val recordMap = createRecordMap(assessment, attemptId, userId, contentId)
      val events = getEvents(assessment)
      recordMap.put("question", transformEventsToUDTs(events, udtType))
      persist(recordMap, ctx)
      attemptId
    } catch {
      case e: Exception => 
        logger.error(ctx, "AssessmentAuditRecorder: Error during record", e)
        Option(assessment.get(JsonKey.ATTEMPT_ID)).map(_.toString).getOrElse(generateId(assessment.get(JsonKey.USER_ID).asInstanceOf[String], assessment.get(JsonKey.CONTENT_ID).asInstanceOf[String]))
    }
  }

  private def getAttemptId(m: util.Map[String, AnyRef], uid: String, cid: String): String = 
    Option(m.get(JsonKey.ATTEMPT_ID)).map(_.toString).getOrElse(generateId(uid, cid))

  private def generateId(uid: String, cid: String): String = 
    s"${uid}_${cid}_${System.currentTimeMillis()}".hashCode.abs.toString

  private def createRecordMap(m: util.Map[String, AnyRef], aid: String, uid: String, cid: String): util.Map[String, AnyRef] = {
    val rec = new util.HashMap[String, AnyRef]()
    rec.put("user_id", uid); rec.put("course_id", m.get(JsonKey.COURSE_ID))
    rec.put("batch_id", m.get(JsonKey.BATCH_ID)); rec.put("content_id", cid)
    rec.put("attempt_id", aid); rec.put("last_attempted_on", new java.sql.Timestamp(System.currentTimeMillis()))
    rec.put("created_on", new java.sql.Timestamp(System.currentTimeMillis()))
    rec
  }

  private def getEvents(m: util.Map[String, AnyRef]): List[util.Map[String, AnyRef]] = 
    m.getOrDefault("events", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList

  private def transformEventsToUDTs(events: List[util.Map[String, AnyRef]], udtType: UserType): util.List[UDTValue] = 
    events.flatMap(e => try { Some(createUDT(e, udtType)) } catch { case _: Exception => None }).asJava

  private def createUDT(event: util.Map[String, AnyRef], udtType: UserType): UDTValue = {
    val src = getSource(event)
    val item = getItem(src)
    udtType.newValue()
      .setString("id", stringify(item.get("id"), event.get("questionId")))
      .setDouble("score", asDouble(src.get("score")))
      .setDouble("max_score", asDouble(item.get("maxscore")).max(asDouble(event.get("maxScore"))))
      .setTimestamp("assess_ts", asTimestamp(event.get("ets"), event.get("timestamp")))
      .setString("type", stringify(item.get("type"), event.get("questionType")))
      .setString("title", stringify(item.get("title"), event.get("title")))
      .setString("description", stringify(item.get("desc"), event.get("description")))
      .setDecimal("duration", java.math.BigDecimal.valueOf(asDouble(src.get("duration"))))
      .setList("resvalues", asUDTList(src.get("resvalues")))
      .setList("params", asUDTList(item.get("params")))
  }

  private def getSource(e: util.Map[String, AnyRef]) = if (e.containsKey("edata")) e.get("edata").asInstanceOf[util.Map[String, AnyRef]] else e
  private def getItem(s: util.Map[String, AnyRef]) = if (s.containsKey("item")) s.get("item").asInstanceOf[util.Map[String, AnyRef]] else s
  private def stringify(v1: AnyRef, v2: AnyRef): String = Option(v1).orElse(Option(v2)).map(_.toString).getOrElse("")
  private def asDouble(v: AnyRef): Double = Option(v).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)
  private def asTimestamp(v1: AnyRef, v2: AnyRef): java.util.Date = new java.util.Date(Option(v1).orElse(Option(v2)).map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis()))

  private def asUDTList(v: AnyRef): util.List[util.Map[String, String]] = {
    val rawList = Option(v).map(_.asInstanceOf[util.List[util.Map[String, AnyRef]]]).getOrElse(new util.ArrayList())
    rawList.asScala.map { m =>
      m.asScala.map { case (k, value) =>
        k -> (if (value == null) "" else if (value.isInstanceOf[String]) value.toString else mapper.writeValueAsString(value))
      }.asJava
    }.toList.asJava
  }

  private def persist(data: util.Map[String, AnyRef], ctx: RequestContext): Unit = {
    val db = org.sunbird.learner.util.Util.dbInfoMap.get(JsonKey.ASSESSMENT_AGGREGATOR_DB)
    cassandraOperation.upsertRecord(db.getKeySpace, db.getTableName, data, ctx)
  }
}
