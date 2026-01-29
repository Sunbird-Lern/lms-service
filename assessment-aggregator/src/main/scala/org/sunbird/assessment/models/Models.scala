package org.sunbird.assessment.models

case class AssessmentRequest(
  attemptId: String,
  userId: String,
  courseId: String,
  batchId: String,
  contentId: String,
  assessmentTimestamp: Long,
  events: List[AssessmentEvent]
)

case class AssessmentEvent(
  questionId: String,
  score: Double,
  maxScore: Double,
  duration: Double,
  timestamp: Long,
  questionType: String = "",
  title: String = "",
  description: String = "",
  resvalues: java.util.List[java.util.Map[String, AnyRef]] = new java.util.ArrayList(),
  params: java.util.List[java.util.Map[String, AnyRef]] = new java.util.ArrayList()
)

case class AssessmentResult(
  attemptId: String,
  userId: String,
  courseId: String,
  batchId: String,
  contentId: String,
  totalScore: Double,
  totalMaxScore: Double,
  grandTotal: String,
  questions: List[QuestionScore],
  createdOn: Long,
  lastAttemptedOn: Long
)

case class QuestionScore(
  questionId: String,
  score: Double,
  maxScore: Double,
  duration: Double,
  questionType: String,
  title: String,
  description: String,
  assessmentTimestamp: Long,
  resvalues: java.util.List[java.util.Map[String, AnyRef]],
  params: java.util.List[java.util.Map[String, AnyRef]]
)

case class UserActivityAggregate(
  userId: String,
  courseId: String,
  batchId: String,
  aggregates: Map[String, Double],
  aggregateDetails: List[AttemptDetail]
)

case class AttemptDetail(attempt_id: String,last_attempted_on: Long,score: Double,content_id: String,max_score: Double,`type`: String = "assessment") {
  def toJson: String = {
    import com.google.gson.GsonBuilder
    import java.util.Date
    val gson = new GsonBuilder().setDateFormat("MMM dd, yyyy, h:mm:ss a").create()
    val map = new java.util.HashMap[String, AnyRef]()
    map.put("attempt_id", attempt_id)
    map.put("last_attempted_on", new Date(last_attempted_on))
    map.put("score", score.asInstanceOf[AnyRef])
    map.put("content_id", content_id)
    map.put("max_score", max_score.asInstanceOf[AnyRef])
    map.put("type", `type`)
    gson.toJson(map)
  }
}

case class ExistingAssessment(
  attemptId: String,
  contentId: String,
  lastAttemptedOn: Long,
  createdOn: Long,
  totalScore: Double,
  totalMaxScore: Double
)

case class ScoreMetrics(
  totalScore: Double,
  totalMaxScore: Double,
  grandTotal: String,
  questions: List[QuestionScore]
)
