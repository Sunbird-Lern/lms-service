package org.sunbird.assessment.service
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.helper.ServiceFactory
import org.sunbird.common.request.RequestContext
import org.sunbird.assessment.models._
import org.sunbird.common.models.util.ProjectUtil
import scala.collection.JavaConverters._
import com.datastax.driver.core.{UserType, UDTValue}
import org.slf4j.LoggerFactory

class CassandraService {

  private val logger = LoggerFactory.getLogger(classOf[CassandraService])
  private lazy val dao = ServiceFactory.getInstance.asInstanceOf[CassandraOperation]
  private val keyspace = Option(ProjectUtil.getConfigValue("sunbird_course_keyspace")).getOrElse("sunbird_courses")
  private val assessmentTable = Option(ProjectUtil.getConfigValue("assessment_aggregator_table")).getOrElse("assessment_aggregator")
  private val activityTable = Option(ProjectUtil.getConfigValue("user_activity_agg_table")).getOrElse("user_activity_agg")
  private lazy val questionType: UserType = dao.getUDTType(keyspace, Option(ProjectUtil.getConfigValue("assessment_question_udt_type")).getOrElse("question"))

  def getAssessment(aid: String, uid: String, cid: String, bid: String, contId: String, ctx: RequestContext): Option[ExistingAssessment] = {
    try {
      val filters = buildFilters("attempt_id" -> aid, "user_id" -> uid, "course_id" -> cid, "batch_id" -> bid, "content_id" -> contId)
      val fields = java.util.Arrays.asList("attempt_id", "content_id", "last_attempted_on", "created_on", "total_score", "total_max_score", "question")
      val records = fetchRecords(filters, fields, ctx)
      records.headOption.map(mapToExisting)
    } catch { case e: Exception => logger.error(s"Get failed for $aid", e); None }
  }

  def getUserAssessments(uid: String, cid: String, bid: String, contId: String, ctx: RequestContext): List[ExistingAssessment] = {
    try {
      val filters = buildFilters("user_id" -> uid, "course_id" -> cid, "batch_id" -> bid, "content_id" -> contId)
      val fields = java.util.Arrays.asList("content_id", "attempt_id", "last_attempted_on", "total_max_score", "total_score", "question")
      fetchRecords(filters, fields, ctx).map(mapToExisting)
    } catch { case e: Exception => logger.error(s"List failed for $uid", e); List.empty }
  }

  def saveAssessment(res: AssessmentResult, ctx: RequestContext): Unit = {
    try {
      val baseRecord = buildBaseRecord(res)
      val questionUDTs = buildQuestionUDTs(res.questions)
      baseRecord.put("question", questionUDTs)
      dao.upsertRecord(keyspace, assessmentTable, baseRecord, ctx)
    } catch { case e: Exception => logger.error(s"Save failed for ${res.attemptId}", e); throw e }
  }

  def updateUserActivity(uid: String, cid: String, bid: String, agg: UserActivityAggregate, ctx: RequestContext): Unit = {
    try {
      if (agg.aggregates.nonEmpty || agg.aggregateDetails.nonEmpty) {
        val lastUpdated = agg.aggregates.map { case (k, _) => k -> new java.util.Date() }
        val data = Map(
          "activity_id" -> cid, 
          "activity_type" -> "Course", 
          "context_id" -> s"cb:$bid", 
          "user_id" -> uid, 
          "aggregates" -> agg.aggregates.asJava, 
          "agg_details" -> agg.aggregateDetails.map(_.toJson).asJava, 
          "agg_last_updated" -> lastUpdated.asJava
        ).asJava.asInstanceOf[java.util.Map[String, AnyRef]]
        dao.upsertRecord(keyspace, activityTable, data, ctx)
      }
    } catch { case e: Exception => logger.error(s"Activity update failed for $uid", e); throw e }
  }

  private def buildFilters(pairs: (String, String)*): java.util.Map[String, AnyRef] = 
    pairs.toMap.asJava.asInstanceOf[java.util.Map[String, AnyRef]]

  private def fetchRecords(filters: java.util.Map[String, AnyRef], fields: java.util.List[String], ctx: RequestContext): List[java.util.Map[String, AnyRef]] = 
    dao.getRecordsByProperties(keyspace, assessmentTable, filters, fields, ctx)
      .getResult.getOrDefault("response", new java.util.ArrayList)
      .asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.toList

  private def buildBaseRecord(res: AssessmentResult): java.util.HashMap[String, AnyRef] = {
    val rec = new java.util.HashMap[String, AnyRef]()
    rec.putAll(Map(
      "attempt_id" -> res.attemptId, 
      "user_id" -> res.userId, 
      "course_id" -> res.courseId, 
      "batch_id" -> res.batchId, 
      "content_id" -> res.contentId, 
      "total_score" -> res.totalScore.asInstanceOf[AnyRef], 
      "total_max_score" -> res.totalMaxScore.asInstanceOf[AnyRef], 
      "grand_total" -> res.grandTotal, 
      "created_on" -> new java.sql.Timestamp(res.createdOn), 
      "last_attempted_on" -> new java.sql.Timestamp(res.lastAttemptedOn), 
      "updated_on" -> new java.sql.Timestamp(System.currentTimeMillis())
    ).asJava)
    rec
  }

  private def buildQuestionUDTs(questions: List[QuestionScore]): java.util.List[UDTValue] = {
    val udts = new java.util.ArrayList[UDTValue]()
    questions.foreach(q => udts.add(toQuestionUDT(q)))
    udts
  }

  private def toQuestionUDT(q: QuestionScore): UDTValue = 
    questionType.newValue()
      .setString("id", q.questionId)
      .setTimestamp("assess_ts", new java.util.Date(q.assessmentTimestamp))
      .setDouble("max_score", q.maxScore)
      .setDouble("score", q.score)
      .setString("type", q.questionType)
      .setString("title", q.title)
      .setString("description", q.description)
      .setDecimal("duration", java.math.BigDecimal.valueOf(q.duration))
      .setList("resvalues", q.resvalues)
      .setList("params", q.params)

  private def mapToExisting(row: java.util.Map[String, AnyRef]): ExistingAssessment = {
    ExistingAssessment(
      getString(row, "attempt_id", "attemptId"), 
      getString(row, "content_id", "contentId"), 
      getTimestamp(row, "last_attempted_on", "lastAttemptedOn"), 
      getTimestamp(row, "created_on", "createdOn"), 
      getDouble(row, "total_score", "totalScore"), 
      getDouble(row, "total_max_score", "totalMaxScore"),
      parseQuestions(row)
    )
  }

  private def parseQuestions(row: java.util.Map[String, AnyRef]): List[AssessmentEvent] = 
    Option(row.get("question")).map { q =>
      q.asInstanceOf[java.util.List[UDTValue]].asScala
        .filterNot(_.getString("id") == "telemetry")
        .map(toAssessmentEvent)
        .toList
    }.getOrElse(List.empty)

  private def toAssessmentEvent(udt: UDTValue): AssessmentEvent = 
    AssessmentEvent(
      udt.getString("id"),
      udt.getDouble("score"),
      udt.getDouble("max_score"),
      Option(udt.getDecimal("duration")).map(_.doubleValue()).getOrElse(0.0),
      Option(udt.getTimestamp("assess_ts")).map(_.getTime).getOrElse(0L),
      udt.getString("type"),
      udt.getString("title"),
      udt.getString("description"),
      Option(udt.getList("resvalues", classOf[java.util.Map[String, String]])).map(_.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]).getOrElse(new java.util.ArrayList()),
      Option(udt.getList("params", classOf[java.util.Map[String, String]])).map(_.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]).getOrElse(new java.util.ArrayList())
    )

  private def getString(row: java.util.Map[String, AnyRef], k1: String, k2: String): String = 
    Option(row.get(k1)).orElse(Option(row.get(k2))).map(_.toString).getOrElse("")

  private def getDouble(row: java.util.Map[String, AnyRef], k1: String, k2: String): Double = 
    Option(row.get(k1)).orElse(Option(row.get(k2))).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)

  private def getTimestamp(row: java.util.Map[String, AnyRef], k1: String, k2: String): Long = 
    Option(row.get(k1)).orElse(Option(row.get(k2)))
      .map(v => if (v.isInstanceOf[java.util.Date]) v.asInstanceOf[java.util.Date].getTime else 0L)
      .getOrElse(0L)
}
