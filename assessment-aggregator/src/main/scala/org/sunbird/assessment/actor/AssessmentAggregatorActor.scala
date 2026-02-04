package org.sunbird.assessment.actor
import org.sunbird.actor.core.BaseActor
import javax.inject.Inject
import org.apache.pekko.actor.Props
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.assessment.models._
import org.sunbird.assessment.service._
import org.sunbird.assessment.util.AssessmentParser
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import scala.collection.JavaConverters._
import org.apache.commons.lang3.StringUtils

class AssessmentAggregatorActor @Inject()(_redisService: Option[RedisService],_contentService: Option[ContentService],_cassandraService: Option[CassandraService],_kafkaService: Option[KafkaService]) extends BaseActor {

  def this() = this(None, None, None, None)

  private lazy val redisService = _redisService.getOrElse(new RedisService())
  private lazy val contentService = _contentService.getOrElse(new ContentService())
  private lazy val assessmentService = new AssessmentService(redisService, contentService)
  private lazy val cassandraService = _cassandraService.getOrElse(new CassandraService())
  private lazy val kafkaService = _kafkaService.getOrElse(new KafkaService())

  override def onReceive(request: Request): Unit = {
    val replyTo = sender()
    try { 
      processAggregation(request, replyTo) 
    } catch {
      case ex: Exception =>
        logger.error(request.getRequestContext, "Request failed", ex)
        replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage, ResponseCode.SERVER_ERROR.getResponseCode)
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
        replyTo ! createErrorResponse("CLIENT_ERROR", ex.getMessage, ResponseCode.CLIENT_ERROR.getResponseCode)
    }
  }

  private def processIndividual(request: AssessmentRequest, rawMap: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val syncedRequests = recoverAssessmentData(request, context)
    syncedRequests.foreach { syncedReq =>
      val calculatedMetadata = assessmentService.getMetadata(syncedReq.courseId, syncedReq.contentId, context)
      validateContent(syncedReq, calculatedMetadata, context)
      if (syncedReq.events.nonEmpty) {
        processAttempt(syncedReq, calculatedMetadata, rawMap, context)
      } else {
        processUserAggregates(syncedReq.userId, syncedReq.courseId, syncedReq.batchId, syncedReq.contentId, context)
      }
    }
  }

  private def recoverAssessmentData(request: AssessmentRequest, context: RequestContext): List[AssessmentRequest] = {
    if (request.events.nonEmpty) return List(request)
    val existing = fetchStoredAssessments(request, context)
    if (existing.isEmpty) {
      logger.warn(context, s"Sync Flow: No stored events found for userId=${request.userId}, contentId=${request.contentId}, attemptId=${request.attemptId}")
      return List(request)
    }
    logger.info(context, s"Sync Flow: Recovered ${existing.size} attempt(s) for userId=${request.userId}, contentId=${request.contentId}")
    existing.map(toSyncRequest(request, _))
  }

  private def fetchStoredAssessments(request: AssessmentRequest, context: RequestContext): List[ExistingAssessment] = {
    if (StringUtils.isNotBlank(request.attemptId)) {
      cassandraService.getAssessment(request.attemptId, request.userId, request.courseId, request.batchId, request.contentId, context).toList
    } else {
      cassandraService.getUserAssessments(request.userId, request.courseId, request.batchId, request.contentId, context)
    }
  }

  private def toSyncRequest(original: AssessmentRequest, stored: ExistingAssessment): AssessmentRequest = {
    original.copy(
      attemptId = stored.attemptId,
      events = stored.questions,
      assessmentTimestamp = stored.questions.headOption.map(_.timestamp).getOrElse(stored.lastAttemptedOn),
      rawJson = None,
      ignoreTimestampValidation = true
    )
  }

  private def validateContent(req: AssessmentRequest, metadata: ContentMetadata, context: RequestContext): Unit = {
    if (!assessmentService.validateContent(req, metadata)) {
      val msg = s"Content validation failed: contentId ${req.contentId} is not valid for courseId ${req.courseId}"
      logger.error(context, msg + s" (userId: ${req.userId})", null)
      throw new RuntimeException(msg)
    }
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
    val existingTs = existing.map(_.lastAttemptedOn).getOrElse(0L)
    logger.info(context, s"AssessmentAggregatorActor: Comparing timestamps for attemptId=${req.attemptId} | Incoming=${req.assessmentTimestamp} | Existing=$existingTs")
    if (!req.ignoreTimestampValidation && existingTs > req.assessmentTimestamp) {
      logger.info(context, s"Skipping stale assessment: ${req.attemptId}")
      return
    }
    val result = AssessmentResult(req.attemptId, req.userId, req.courseId, req.batchId, req.contentId, scoreMetrics.totalScore, scoreMetrics.totalMaxScore, scoreMetrics.grandTotal, scoreMetrics.questions, existing.map(_.createdOn).getOrElse(System.currentTimeMillis()), req.assessmentTimestamp)
    cassandraService.saveAssessment(result, context)
    processUserAggregates(req.userId, req.courseId, req.batchId, req.contentId, context)
  }

  private def processUserAggregates(userId: String, courseId: String, batchId: String, contentId: String, context: RequestContext): Unit = {
    val assessments = cassandraService.getUserAssessments(userId, courseId, batchId, contentId, context)
    if (assessments.nonEmpty) {
      val agg = assessmentService.computeUserAggregates(userId, courseId, batchId, assessments)
      cassandraService.updateUserActivity(userId, courseId, batchId, agg, context)
      val attemptId = assessmentService.getLatestAttemptId(agg)
      if (ProjectUtil.getConfigValue("assessment_aggregator_publish_certificate") == "true") {
        kafkaService.publishCertificateEvent(userId, courseId, batchId, attemptId)
      }
    }
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
    val timestamp = Option(m.get("assessmentTimestamp")).orElse(Option(m.get(JsonKey.ASSESSMENT_TS)))
      .map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis())
    timestamp
  }

  private def extractEvents(m: java.util.Map[String, AnyRef]): List[AssessmentEvent] = {
    val eventsRaw = m.get(JsonKey.EVENTS)
    if (eventsRaw != null) eventsRaw.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(AssessmentParser.mapToEvent).toList else List.empty
  }

  private def getD(m: java.util.Map[String, AnyRef], k: String): Double = Option(m.get(k)).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)

  private def createSuccess(aid: String) = { val r = new org.sunbird.common.models.response.Response(); r.put("response", "SUCCESS"); r.put("attemptId", aid); r }
  private def createErrorResponse(code: String, msg: String, responseCode: Int): ProjectCommonException = new ProjectCommonException(code, msg, responseCode)
}

object AssessmentAggregatorActor {
  def props(): Props = Props(new AssessmentAggregatorActor())
}
