package org.sunbird.assessment.actor
import org.apache.pekko.actor.{Props, UntypedAbstractActor}
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.util.{LoggerUtil, ProjectUtil}
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.assessment.models._
import org.sunbird.assessment.service._
import scala.collection.JavaConverters._
import org.apache.commons.lang3.StringUtils

class AssessmentAggregatorActor extends UntypedAbstractActor {

  private val logger = new LoggerUtil(classOf[AssessmentAggregatorActor])
  private lazy val assessmentService = new AssessmentService()
  private lazy val cassandraService = new CassandraService()
  private lazy val redisService = new RedisService()
  private lazy val kafkaService = new KafkaService()
  private lazy val contentService = new ContentService()

  override def onReceive(message: Any): Unit = {
    message match {
      case request: Request =>
        val replyTo = sender()
        try { processAggregation(request, replyTo) } catch {
          case ex: Exception =>
            logger.error(request.getRequestContext, "Request failed", ex)
            replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage)
        }
      case _ => sender() ! createErrorResponse("INVALID_MESSAGE", "Invalid message")
    }
  }

  private def processAggregation(request: Request, replyTo: org.apache.pekko.actor.ActorRef): Unit = {
    val context = request.getRequestContext
    val body = request.getRequest
    if (body.containsKey("assessments") && body.get("assessments").isInstanceOf[java.util.List[_]]) {
      val assessments = body.get("assessments").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
      assessments.foreach { item => processIndividual(extractAssessment(item, body), context) }
      replyTo ! createSuccess(body.asScala.getOrElse("attemptId", "N/A").toString)
    } else {
      val assessment = extractAssessment(body, body)
      processIndividual(assessment, context)
      replyTo ! createSuccess(assessment.attemptId)
    }
  }

  private def processIndividual(req: AssessmentRequest, context: RequestContext): Unit = {
    validateAssessment(req, context)
    if (req.events.isEmpty) processUserAggregates(req.userId, req.courseId, req.batchId, context)
    else processAttempt(req, context)
  }

  private def processAttempt(req: AssessmentRequest, context: RequestContext): Unit = {
    val uniqueEvents = assessmentService.getUniqueQuestions(req.events)
    val skipMissing = Option(ProjectUtil.getConfigValue("assessment_skip_missing_records")).getOrElse("true").toBoolean
    if (skipMissing) {
      val totalQuestions = redisService.getTotalQuestionsCount(req.contentId).getOrElse(contentService.getQuestionCount(req.contentId))
      logger.info(context, s"Total questions for contentId=${req.contentId} is $totalQuestions, unique events=${uniqueEvents.size}")
      if (totalQuestions > 0 && uniqueEvents.size > totalQuestions) {
        logger.warn(context, s"Skipping assessment ${req.attemptId}: unique events (${uniqueEvents.size}) exceed total questions ($totalQuestions)")
        return
      }
    }

    val scoreMetrics = assessmentService.computeScoreMetrics(uniqueEvents)
    val existing = cassandraService.getAssessment(req.attemptId, req.userId, req.courseId, req.batchId, req.contentId, context)
    if (existing.exists(_.lastAttemptedOn >= req.assessmentTimestamp)) {
      logger.info(context, s"Skipping stale assessment: ${req.attemptId}")
      return
    }
    val result = AssessmentResult(req.attemptId, req.userId, req.courseId, req.batchId, req.contentId, scoreMetrics.totalScore, scoreMetrics.totalMaxScore, scoreMetrics.grandTotal, scoreMetrics.questions, existing.map(_.createdOn).getOrElse(System.currentTimeMillis()), req.assessmentTimestamp)
    cassandraService.saveAssessment(result, context)
    processUserAggregates(req.userId, req.courseId, req.batchId, context)
  }

  private def processUserAggregates(userId: String, courseId: String, batchId: String, context: RequestContext): Unit = {
    val assessments = cassandraService.getUserAssessments(userId, courseId, batchId, context)
    if (assessments.nonEmpty) {
      val agg = assessmentService.computeUserAggregates(assessments).copy(userId = userId, courseId = courseId, batchId = batchId)
      cassandraService.updateUserActivity(userId, courseId, batchId, agg, context)
      val attemptId = assessmentService.getLatestAttemptId(agg)
      if (StringUtils.isNotBlank(attemptId)) kafkaService.publishCertificateEvent(userId, courseId, batchId, attemptId)
    }
  }

  private def extractAssessment(data: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef]): AssessmentRequest = {
    val userId = getVal(data, "userId", getVal(root, "userId", "requestedBy"))
    val courseId = getVal(data, "courseId", getVal(root, "courseId", ""))
    val batchId = getVal(data, "batchId", getVal(root, "batchId", ""))
    val contentId = getVal(data, "contentId", getVal(root, "contentId", ""))
    val tsValue = Option(data.get("assessmentTimestamp")).orElse(Option(data.get("assessmentTs"))).orElse(Option(root.get("assessmentTimestamp")))
    val timestamp = tsValue.map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis())
    val eventsRaw = data.get("events")
    val events = if (eventsRaw != null) eventsRaw.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(mapToEvent).toList else List.empty
    val attemptId = Option(data.get("attemptId")).map(_.asInstanceOf[String]).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) throw new RuntimeException("Missing userId/courseId")
    AssessmentRequest(attemptId, userId, courseId, batchId, contentId, timestamp, events)
  }

  private def mapToEvent(m: java.util.Map[String, AnyRef]): AssessmentEvent = {
    if (m.containsKey("edata")) {
      val edata = m.get("edata").asInstanceOf[java.util.Map[String, AnyRef]]
      val item = edata.getOrDefault("item", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
      AssessmentEvent(item.getOrDefault("id","").toString, getD(edata, "score"), getD(item, "maxscore"), getD(edata, "duration"), Option(m.get("ets")).getOrElse(0L).asInstanceOf[Number].longValue(), item.getOrDefault("type","").toString, item.getOrDefault("title","").toString, item.getOrDefault("desc","").toString)
    } else {
      AssessmentEvent(m.getOrDefault("questionId","").toString, getD(m, "score"), getD(m, "maxScore"), getD(m, "duration"), Option(m.get("timestamp")).getOrElse(0L).asInstanceOf[Number].longValue(), m.getOrDefault("questionType","").toString, m.getOrDefault("title","").toString, m.getOrDefault("description","").toString)
    }
  }

  private def validateAssessment(req: AssessmentRequest, context: RequestContext): Unit = {
    val enableVal = ProjectUtil.getConfigValue("assessment_enable_content_validation") == "true"
    if (enableVal) {
      val isValidInRedis = redisService.isValidContent(req.courseId, req.contentId)
      if (!isValidInRedis && !contentService.isValidContent(req.contentId)) {
        throw new RuntimeException("Invalid Content")
      }
    }
  }

  private def getVal(m: java.util.Map[String, AnyRef], k1: String, k2: String): String = {
    val v = m.get(k1); if (v != null) v.toString else { val v2 = m.get(k2); if (v2 != null) v2.toString else "" }
  }

  private def getD(m: java.util.Map[String, AnyRef], k: String): Double = Option(m.get(k)).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)
  private def createSuccess(aid: String) = { val r = new org.sunbird.common.models.response.Response(); r.put("response", "SUCCESS"); r.put("attemptId", aid); r }
  private def createErrorResponse(code: String, msg: String): ProjectCommonException = new ProjectCommonException(code, msg, ResponseCode.SERVER_ERROR.getResponseCode)
}

object AssessmentAggregatorActor {
  def props(): Props = Props(new AssessmentAggregatorActor())
}
