package org.sunbird.assessment.service
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.helper.ServiceFactory
import org.sunbird.common.request.RequestContext
import org.sunbird.assessment.models._
import org.sunbird.common.models.util.ProjectUtil
import scala.collection.JavaConverters._
import com.datastax.driver.core.UserType
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
      val filters = Map("attempt_id" -> aid, "user_id" -> uid, "course_id" -> cid, "batch_id" -> bid).asJava.asInstanceOf[java.util.Map[String, AnyRef]]
      val fields = java.util.Arrays.asList("attempt_id", "content_id", "last_attempted_on", "created_on", "total_score", "total_max_score")
      val records = dao.getRecordsByProperties(keyspace, assessmentTable, filters, fields, ctx).getResult.getOrDefault("response", new java.util.ArrayList).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
      if (records.isEmpty) None else Some(mapToExisting(records.head))
    } catch { case e: Exception => logger.error(s"Get failed for $aid", e); None }
  }

  def saveAssessment(res: AssessmentResult, ctx: RequestContext): Unit = {
    try {
      val rec = new java.util.HashMap[String, AnyRef]()
      rec.putAll(Map("attempt_id" -> res.attemptId, "user_id" -> res.userId, "course_id" -> res.courseId, "batch_id" -> res.batchId, "content_id" -> res.contentId, "total_score" -> res.totalScore.asInstanceOf[AnyRef], "total_max_score" -> res.totalMaxScore.asInstanceOf[AnyRef], "grand_total" -> res.grandTotal, "created_on" -> new java.sql.Timestamp(res.createdOn), "last_attempted_on" -> new java.sql.Timestamp(res.lastAttemptedOn), "updated_on" -> new java.sql.Timestamp(System.currentTimeMillis())).asJava)
      val qs = res.questions.map(q => questionType.newValue().setString("id", q.questionId).setTimestamp("assess_ts", new java.util.Date(q.assessmentTimestamp)).setDouble("max_score", q.maxScore).setDouble("score", q.score).setString("type", q.questionType).setString("title", q.title).setString("description", q.description).setDecimal("duration", java.math.BigDecimal.valueOf(q.duration)).setList("resvalues", q.resvalues).setList("params", q.params)).asJava
      rec.put("question", qs)
      dao.upsertRecord(keyspace, assessmentTable, rec, ctx)
    } catch { case e: Exception => logger.error(s"Save failed for ${res.attemptId}", e); throw e }
  }

  def getUserAssessments(uid: String, cid: String, bid: String, ctx: RequestContext): List[ExistingAssessment] = {
    try {
      val filters = Map("user_id" -> uid, "course_id" -> cid, "batch_id" -> bid).asJava.asInstanceOf[java.util.Map[String, AnyRef]]
      val fields = java.util.Arrays.asList("content_id", "attempt_id", "last_attempted_on", "total_max_score", "total_score")
      val records = dao.getRecordsByProperties(keyspace, assessmentTable, filters, fields, ctx).getResult.getOrDefault("response", new java.util.ArrayList).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
      records.map(mapToExisting).toList
    } catch { case e: Exception => logger.error(s"List failed for $uid", e); List.empty }
  }

  def updateUserActivity(uid: String, cid: String, bid: String, agg: UserActivityAggregate, ctx: RequestContext): Unit = {
    try {
      if (agg.aggregates.nonEmpty || agg.aggregateDetails.nonEmpty) {
        val data = new java.util.HashMap[String, AnyRef]()
        val lastUpdated = agg.aggregates.map { case (k, _) => k -> new java.util.Date() }
        data.putAll(Map("activity_id" -> cid, "activity_type" -> "Course", "context_id" -> s"cb:$bid", "user_id" -> uid, "aggregates" -> agg.aggregates.asJava, "agg_details" -> agg.aggregateDetails.map(_.toJson).asJava, "agg_last_updated" -> lastUpdated.asJava).asJava)
        dao.upsertRecord(keyspace, activityTable, data, ctx)
      }
    } catch { case e: Exception => logger.error(s"Activity update failed for $uid", e); throw e }
  }
  
  private def mapToExisting(row: java.util.Map[String, AnyRef]): ExistingAssessment = {
    def get[T](k1: String, k2: String, default: T): T = Option(row.get(k1)).orElse(Option(row.get(k2))).map(_.asInstanceOf[T]).getOrElse(default)
    def getTime(k1: String, k2: String): Long = Option(row.get(k1)).orElse(Option(row.get(k2))).map(v => if (v.isInstanceOf[java.util.Date]) v.asInstanceOf[java.util.Date].getTime else 0L).getOrElse(0L)
    ExistingAssessment(get("attempt_id", "attemptId", ""), get("content_id", "contentId", ""), getTime("last_attempted_on", "lastAttemptedOn"), getTime("created_on", "createdOn"), get("total_score", "totalScore", 0.0), get("total_max_score", "totalMaxScore", 0.0))
  }
}
