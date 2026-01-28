package org.sunbird.assessment.service
import org.sunbird.assessment.models._
import java.text.DecimalFormat

class AssessmentService {

  private val decimalFormat = new DecimalFormat("0.0#")
  private val aggType = Option(org.sunbird.common.models.util.ProjectUtil.getConfigValue("user_activity_agg_type")).getOrElse("assessment")

  def getUniqueQuestions(events: List[AssessmentEvent]): List[AssessmentEvent] = {
    events.sortBy(_.timestamp)(Ordering[Long].reverse).groupBy(_.questionId).values.map(_.head).toList
  }

  def computeScoreMetrics(events: List[AssessmentEvent]): ScoreMetrics = {
    val scores = events.map(e => (e.score, e.maxScore))
    val totalScore = scores.map(_._1).sum
    val totalMaxScore = scores.map(_._2).sum
    val questions = events.map(e => QuestionScore(e.questionId, e.score, e.maxScore, e.duration, e.questionType, e.title, e.description, e.timestamp))
    val grandTotal = s"${decimalFormat.format(totalScore)}/${decimalFormat.format(totalMaxScore)}"
    ScoreMetrics(totalScore, totalMaxScore, grandTotal, questions)
  }

  def computeUserAggregates(assessments: List[ExistingAssessment]): UserActivityAggregate = {
    if (assessments.isEmpty) return UserActivityAggregate("", "", "", Map.empty, List.empty)
    val aggregates = assessments.groupBy(_.contentId).flatMap { case (cid, attempts) =>
      val best = attempts.maxBy(_.totalScore)
      Map(s"score:$cid" -> best.totalScore, s"max_score:$cid" -> best.totalMaxScore, s"attempts_count:$cid" -> attempts.size.toDouble)
    }
    val details = assessments.map(a => AttemptDetail(a.attemptId, a.lastAttemptedOn, a.totalScore, a.contentId, a.totalMaxScore, aggType))
    UserActivityAggregate("", "", "", aggregates, details)
  }
  
  def getLatestAttemptId(aggregate: UserActivityAggregate): String = {
    if (aggregate.aggregateDetails.isEmpty) return ""
    val scoreKeys = aggregate.aggregates.keySet.filter(_.startsWith("score:"))
    if (scoreKeys.size == 1) {
      aggregate.aggregateDetails.maxBy(_.lastAttemptedOn).attemptId
    } else ""
  }
}
