package org.sunbird.assessment.actor
import org.apache.pekko.actor.{Props, UntypedAbstractActor}
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.assessment.models._
import org.sunbird.assessment.service._
import org.sunbird.assessment.util.AssessmentParser
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import scala.collection.JavaConverters._
import org.apache.commons.lang3.StringUtils

class AssessmentAggregatorActor extends UntypedAbstractActor {

  private val logger = new LoggerUtil(classOf[AssessmentAggregatorActor])
  private lazy val redisService = new RedisService()
  private lazy val contentService = new ContentService()
  private lazy val assessmentService = new AssessmentService(redisService, contentService)
  private lazy val cassandraService = new CassandraService()
  private lazy val kafkaService = new KafkaService()

  override def onReceive(message: Any): Unit = {
    message match {
      case request: Request =>
        val replyTo = sender()
        try { 
          processAggregation(request, replyTo) 
        } catch {
          case ex: Exception =>
            logger.error(request.getRequestContext, "Request failed", ex)
            replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage)
        }
      case _ => sender() ! createErrorResponse("INVALID_MESSAGE", "Invalid message")
    }
  }

  private def processAggregation(request: Request, replyTo: org.apache.pekko.actor.ActorRef): Unit = {
    val body = request.getRequest
    val context = request.getRequestContext
    if (body.containsKey(JsonKey.ASSESSMENT_EVENTS)) {
      handleBatchRequest(body, context)
      replyTo ! createSuccess(body.asScala.getOrElse(JsonKey.ATTEMPT_ID, "N/A").toString)
    } else {
      handleSingleRequest(body, context, replyTo)
    }
  }

  private def handleBatchRequest(body: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val assessments = body.getOrDefault(JsonKey.ASSESSMENT_EVENTS, new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
    assessments.foreach { item =>
      try {
        val assessment = extractFromAggregatorApi(item, body)
        processIndividual(assessment, item, context)
      } catch {
        case ex: Exception =>
          logger.error(context, s"Individual assessment failed in batch. Reason: ${ex.getMessage} | Data: $item", ex)
      }
    }
  }

  private def handleSingleRequest(body: java.util.Map[String, AnyRef], context: RequestContext, replyTo: org.apache.pekko.actor.ActorRef): Unit = {
    try {
      val assessment = extractFromContentConsumptionActor(body)
      processIndividual(assessment, body, context)
      replyTo ! createSuccess(assessment.attemptId)
    } catch {
      case ex: Exception =>
        logger.error(context, s"Assessment request failed. Reason: ${ex.getMessage} | Data: $body", ex)
        replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage)
    }
  }

  private def processIndividual(req: AssessmentRequest, rawMap: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val metadata = assessmentService.getMetadata(req.courseId, req.contentId)
    if (!assessmentService.validateContent(req, metadata)) {
      val msg = s"Content validation failed: contentId ${req.contentId} is not valid for courseId ${req.courseId}"
      logger.error(context, msg + s" (userId: ${req.userId})")
      throw new RuntimeException(msg)
    }
    if (req.events.isEmpty) processUserAggregates(req.userId, req.courseId, req.batchId, context)
    else processAttempt(req, metadata, rawMap, context)
  }

  private def processAttempt(req: AssessmentRequest, metadata: ContentMetadata, rawMap: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val uniqueEvents = assessmentService.getUniqueQuestions(req.events)
    val skipMissing = Option(ProjectUtil.getConfigValue("assessment_skip_missing_records")).getOrElse("true").toBoolean
    if (skipMissing) {
      val totalQuestions = metadata.totalQuestions
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
      val agg = assessmentService.computeUserAggregates(userId, courseId, batchId, assessments)
      cassandraService.updateUserActivity(userId, courseId, batchId, agg, context)
      val attemptId = assessmentService.getLatestAttemptId(agg)
      kafkaService.publishCertificateEvent(userId, courseId, batchId, attemptId)    }
  }

  /**
   * Extraction logic for Content Consumption Actor Flow (Single Request)
   */
  private def extractFromContentConsumptionActor(data: java.util.Map[String, AnyRef]): AssessmentRequest = {
    val userId = getInternalVal(data, JsonKey.USER_ID, JsonKey.REQUESTED_BY)
    val courseId = getInternalVal(data, JsonKey.COURSE_ID, "")
    val batchId = getInternalVal(data, JsonKey.BATCH_ID, "")
    val contentId = getInternalVal(data, JsonKey.CONTENT_ID, "")
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) {
      throw new RuntimeException(s"Missing userId/courseId in Request Body")
    }
    val timestamp = extractTs(data)
    val events = extractEvents(data)
    val attemptId = Option(data.get(JsonKey.ATTEMPT_ID)).map(_.toString).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
    AssessmentRequest(attemptId, userId, courseId, batchId, contentId, timestamp, events)
  }

  /**
   * Extraction logic for Aggregator API Flow (Batch Processing)
   */
  private def extractFromAggregatorApi(item: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef]): AssessmentRequest = {
    // 1. Get IDs from item first, then from root body
    val userId = getApiVal(item, root, JsonKey.USER_ID, JsonKey.REQUESTED_BY)
    val courseId = getApiVal(item, root, JsonKey.COURSE_ID, JsonKey.COURSE_ID)
    val batchId = getApiVal(item, root, JsonKey.BATCH_ID, JsonKey.BATCH_ID)
    val contentId = getApiVal(item, root, JsonKey.CONTENT_ID, JsonKey.CONTENT_ID)
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) {
      throw new RuntimeException(s"Missing ${JsonKey.USER_ID}/${JsonKey.COURSE_ID} in batch item or root body")
    }
    // 2. Get metric data specifically from the item
    val timestamp = extractTs(item)
    val events = extractEvents(item)
    val attemptId = Option(item.get(JsonKey.ATTEMPT_ID)).map(_.toString).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
    AssessmentRequest(attemptId, userId, courseId, batchId, contentId, timestamp, events)
  }

  private def getInternalVal(m: java.util.Map[String, AnyRef], k1: String, k2: String): String = {
    val v = m.get(k1); if (v != null) v.toString else { val v2 = m.get(k2); if (v2 != null) v2.toString else "" }
  }

  private def getApiVal(item: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef], key: String, fallback: String): String = {
    val v = item.get(key)
    if (v != null && StringUtils.isNotBlank(v.toString)) v.toString
    else {
      val rv = root.get(key)
      if (rv != null && StringUtils.isNotBlank(rv.toString)) rv.toString
      else {
        val fv = root.get(fallback)
        if (fv != null && StringUtils.isNotBlank(fv.toString)) fv.toString else ""
      }
    }
  }

  private def extractTs(m: java.util.Map[String, AnyRef]): Long = {
    Option(m.get("assessmentTimestamp")).orElse(Option(m.get("assessmentTs")))
      .map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis())
  }

  private def extractEvents(m: java.util.Map[String, AnyRef]): List[AssessmentEvent] = {
    val eventsRaw = m.get(JsonKey.EVENTS)
    if (eventsRaw != null) eventsRaw.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(AssessmentParser.mapToEvent).toList else List.empty
  }

  private def getD(m: java.util.Map[String, AnyRef], k: String): Double = Option(m.get(k)).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)

  private def createSuccess(aid: String) = { val r = new org.sunbird.common.models.response.Response(); r.put("response", "SUCCESS"); r.put("attemptId", aid); r }
  private def createErrorResponse(code: String, msg: String): ProjectCommonException = new ProjectCommonException(code, msg, ResponseCode.SERVER_ERROR.getResponseCode)
}

object AssessmentAggregatorActor {
  def props(): Props = Props(new AssessmentAggregatorActor())
}
