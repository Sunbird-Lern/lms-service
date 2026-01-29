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
    if (body.containsKey("assessments")) {
      handleBatchRequest(body, context)
      replyTo ! createSuccess(body.asScala.getOrElse("attemptId", "N/A").toString)
    } else {
      handleSingleRequest(body, context, replyTo)
    }
  }

  private def handleBatchRequest(body: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val assessments = body.getOrDefault("assessments", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
    assessments.foreach { item =>
      try {
        val assessment = extractFromAggregatorApi(item, body)
        processIndividual(assessment, item, context)
      } catch {
        case ex: Exception =>
          logger.error(context, s"Individual assessment failed in batch: ${ex.getMessage}", ex)
          kafkaService.publishFailedEvent(item, s"Processing error: ${ex.getMessage}")
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
        logger.error(context, "Assessment request failed", ex)
        kafkaService.publishFailedEvent(body, s"Processing error: ${ex.getMessage}")
        replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage)
    }
  }

  private def processIndividual(req: AssessmentRequest, rawMap: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    validateAssessment(req, rawMap)
    if (req.events.isEmpty) processUserAggregates(req.userId, req.courseId, req.batchId, context)
    else processAttempt(req, rawMap, context)
  }

  private def processAttempt(req: AssessmentRequest, rawMap: java.util.Map[String, AnyRef], context: RequestContext): Unit = {
    val uniqueEvents = assessmentService.getUniqueQuestions(req.events)
    val skipMissing = Option(ProjectUtil.getConfigValue("assessment_skip_missing_records")).getOrElse("true").toBoolean
    if (skipMissing) {
      val totalQuestions = redisService.getTotalQuestionsCount(req.contentId).getOrElse(contentService.getQuestionCount(req.contentId))
      if (totalQuestions > 0 && uniqueEvents.size > totalQuestions) {
        logger.warn(context, s"Skipping assessment ${req.attemptId}: unique events (${uniqueEvents.size}) exceed total questions ($totalQuestions)")
        kafkaService.publishFailedEvent(rawMap, s"Question count mismatch: unique=${uniqueEvents.size}, total=$totalQuestions")
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
      kafkaService.publishCertificateEvent(userId, courseId, batchId, attemptId)    }
  }

  /**
   * Extraction logic for Content Consumption Actor Flow (Single Request)
   */
  private def extractFromContentConsumptionActor(data: java.util.Map[String, AnyRef]): AssessmentRequest = {
    val userId = getInternalVal(data, "userId", "requestedBy")
    val courseId = getInternalVal(data, "courseId", "")
    val batchId = getInternalVal(data, "batchId", "")
    val contentId = getInternalVal(data, "contentId", "")
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) {
      throw new RuntimeException(s"Missing userId/courseId in Request Body")
    }
    val timestamp = extractTs(data)
    val events = extractEvents(data)
    val attemptId = Option(data.get("attemptId")).map(_.toString).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
    AssessmentRequest(attemptId, userId, courseId, batchId, contentId, timestamp, events)
  }

  /**
   * Extraction logic for Aggregator API Flow (Batch Processing)
   */
  private def extractFromAggregatorApi(item: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef]): AssessmentRequest = {
    // 1. Get IDs from item first, then from root body
    val userId = getApiVal(item, root, "userId", "requestedBy")
    val courseId = getApiVal(item, root, "courseId", "courseId")
    val batchId = getApiVal(item, root, "batchId", "batchId")
    val contentId = getApiVal(item, root, "contentId", "contentId")
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) {
      throw new RuntimeException(s"Missing userId/courseId in batch item or root body")
    }
    // 2. Get metric data specifically from the item
    val timestamp = extractTs(item)
    val events = extractEvents(item)
    val attemptId = Option(item.get("attemptId")).map(_.toString).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
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
    val eventsRaw = m.get("events")
    if (eventsRaw != null) eventsRaw.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(mapToEvent).toList else List.empty
  }

  private def mapToEvent(m: java.util.Map[String, AnyRef]): AssessmentEvent = {
    if (m.containsKey("edata")) {
      val edata = m.get("edata").asInstanceOf[java.util.Map[String, AnyRef]]
      val item = edata.getOrDefault("item", new java.util.HashMap()).asInstanceOf[java.util.Map[String, AnyRef]]
      val resvalues = getListValues(edata.getOrDefault("resvalues", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      val params = getListValues(item.getOrDefault("params", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      AssessmentEvent(item.getOrDefault("id","").toString, getD(edata, "score"), getD(item, "maxscore"), getD(edata, "duration"), Option(m.get("ets")).getOrElse(0L).asInstanceOf[Number].longValue(), item.getOrDefault("type","").toString, item.getOrDefault("title","").toString, item.getOrDefault("desc","").toString, resvalues.asJava, params.asJava)
    } else {
      val resvalues = getListValues(m.getOrDefault("resvalues", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      val params = getListValues(m.getOrDefault("params", new java.util.ArrayList()).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]])
      AssessmentEvent(m.getOrDefault("questionId","").toString, getD(m, "score"), getD(m, "maxScore"), getD(m, "duration"), Option(m.get("timestamp")).getOrElse(0L).asInstanceOf[Number].longValue(), m.getOrDefault("questionType","").toString, m.getOrDefault("title","").toString, m.getOrDefault("description","").toString, resvalues.asJava, params.asJava)
    }
  }

  private def validateAssessment(req: AssessmentRequest, rawMap: java.util.Map[String, AnyRef]): Unit = {
    val enableVal = ProjectUtil.getConfigValue("assessment_enable_content_validation") == "true"
    if (enableVal) {
      val isValidInRedis = redisService.isValidContent(req.courseId, req.contentId)
      if (!isValidInRedis && !contentService.isValidContent(req.contentId)) {
        kafkaService.publishFailedEvent(rawMap, "Invalid Content")
        throw new RuntimeException("Invalid Content")
      }
    }
  }

  private def getVal(m: java.util.Map[String, AnyRef], k1: String, k2: String): String = {
    val v = m.get(k1); if (v != null) v.toString else { val v2 = m.get(k2); if (v2 != null) v2.toString else "" }
  }

  private def getD(m: java.util.Map[String, AnyRef], k: String): Double = Option(m.get(k)).map(_.asInstanceOf[Number].doubleValue()).getOrElse(0.0)

  private def getListValues(values: java.util.List[java.util.Map[String, AnyRef]]): List[java.util.Map[String, AnyRef]] = {
    import com.google.gson.Gson
    val gson = new Gson()
    values.asScala.map { res =>
      res.asScala.map {
        case (key, value) => 
          val finalValue = if (null != value && !value.isInstanceOf[String]) gson.toJson(value) else value
          key -> finalValue.asInstanceOf[AnyRef]
      }.asJava
    }.toList
  }

  private def createSuccess(aid: String) = { val r = new org.sunbird.common.models.response.Response(); r.put("response", "SUCCESS"); r.put("attemptId", aid); r }
  private def createErrorResponse(code: String, msg: String): ProjectCommonException = new ProjectCommonException(code, msg, ResponseCode.SERVER_ERROR.getResponseCode)
}

object AssessmentAggregatorActor {
  def props(): Props = Props(new AssessmentAggregatorActor())
}
