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
  description: String = ""
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
  assessmentTimestamp: Long
)

case class UserActivityAggregate(
  userId: String,
  courseId: String,
  batchId: String,
  aggregates: Map[String, Double],
  aggregateDetails: List[AttemptDetail]
)

case class AttemptDetail(
  attemptId: String,
  lastAttemptedOn: Long,
  score: Double,
  contentId: String,
  maxScore: Double,
  `type`: String = "assessment"
) {
  def toJson: String = {
    import com.google.gson.Gson
    new Gson().toJson(this)
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
