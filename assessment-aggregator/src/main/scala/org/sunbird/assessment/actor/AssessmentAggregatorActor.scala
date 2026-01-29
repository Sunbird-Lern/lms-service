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
    val context = request.getRequestContext
    val body = request.getRequest
    if (body.containsKey("assessments") && body.get("assessments").isInstanceOf[java.util.List[_]]) {
      val assessments = body.get("assessments").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
      assessments.foreach { item => 
        try {
          processIndividual(extractAssessment(item, body), item, context)
        } catch {
          case ex: Exception => 
            logger.error(context, s"Individual assessment failed in batch: ${ex.getMessage}", ex)
            kafkaService.publishFailedEvent(item, s"Processing error: ${ex.getMessage}")
        }
      }
      replyTo ! createSuccess(body.asScala.getOrElse("attemptId", "N/A").toString)
    } else {
      try {
        val assessment = extractAssessment(body, body)
        processIndividual(assessment, body, context)
        replyTo ! createSuccess(assessment.attemptId)
      } catch {
        case ex: Exception => 
          logger.error(context, "Assessment request failed", ex)
          kafkaService.publishFailedEvent(body, s"Processing error: ${ex.getMessage}")
          replyTo ! createErrorResponse("SERVER_ERROR", ex.getMessage)
      }
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

  private def extractAssessment(data: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef]): AssessmentRequest = {
    val userId = getValWithFallback(data, root, "userId", "requestedBy")
    val courseId = getValWithFallback(data, root, "courseId", "courseId")
    val batchId = getValWithFallback(data, root, "batchId", "batchId")
    val contentId = getValWithFallback(data, root, "contentId", "contentId")
    
    val tsValue = Option(data.get("assessmentTimestamp")).orElse(Option(data.get("assessmentTs")))
      .orElse(Option(root.get("assessmentTimestamp"))).orElse(Option(root.get("assessmentTs")))
    
    val timestamp = tsValue.map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis())
    val eventsRaw = data.get("events")
    val events = if (eventsRaw != null) eventsRaw.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(mapToEvent).toList else List.empty
    val attemptId = Option(data.get("attemptId")).map(_.asInstanceOf[String]).getOrElse(s"${userId}_${contentId}_$timestamp".hashCode.abs.toString)
    
    if (StringUtils.isBlank(userId) || StringUtils.isBlank(courseId)) {
      val dataKeys = data.keySet().asScala.mkString(", ")
      val rootKeys = root.keySet().asScala.mkString(", ")
      throw new RuntimeException(s"Missing userId/courseId: userId=$userId, courseId=$courseId. Available keys in item: [$dataKeys], in root: [$rootKeys]")
    }
    AssessmentRequest(attemptId, userId, courseId, batchId, contentId, timestamp, events)
  }

  private def getValWithFallback(data: java.util.Map[String, AnyRef], root: java.util.Map[String, AnyRef], key: String, fallbackKey: String): String = {
    // Check item data first (multiple variations)
    val variations = List(key, key.toLowerCase, fallbackKey, fallbackKey.toLowerCase)
    val v = variations.flatMap(k => Option(data.get(k))).find(x => StringUtils.isNotBlank(x.toString))
    
    if (v.isDefined) v.get.toString
    else {
      // Check root body
      val rv = variations.flatMap(k => Option(root.get(k))).find(x => StringUtils.isNotBlank(x.toString))
      if (rv.isDefined) rv.get.toString
      else {
        // Ultimate fallback: check if it's inside the 'contents' list if available
        if (root.containsKey("contents") && root.get("contents").isInstanceOf[java.util.List[_]]) {
          val contents = root.get("contents").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala
          val cv = contents.flatMap(c => variations.flatMap(k => Option(c.get(k)))).find(x => StringUtils.isNotBlank(x.toString))
          if (cv.isDefined) cv.get.toString else ""
        } else ""
      }
    }
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
