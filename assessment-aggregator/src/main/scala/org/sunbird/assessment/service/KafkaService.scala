package org.sunbird.assessment.service

import org.sunbird.kafka.client.KafkaClient
import org.sunbird.common.models.util.ProjectUtil
import org.sunbird.assessment.models.AssessmentRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Kafka service using Sunbird's shared Kafka client
 */
class KafkaService {
  
  private val logger = LoggerFactory.getLogger(classOf[KafkaService])
  private val mapper = new ObjectMapper()
  
  // Get topic names from configuration
  private val certificateTopic = ProjectUtil.getConfigValue("kafka_topics_certificate_instruction")
  private val failedEventsTopic = ProjectUtil.getConfigValue("kafka_topics_contentstate_invalid")
  
  /**
   * Publish certificate issue event
   */
  def publishCertificateEvent(userId: String, courseId: String, batchId: String, attemptId: String): Unit = {
    try {
      val event = new java.util.HashMap[String, AnyRef]()
      event.put("userId", userId)
      event.put("courseId", courseId)
      event.put("batchId", batchId)
      event.put("attemptId", attemptId)
      event.put("timestamp", System.currentTimeMillis().asInstanceOf[AnyRef])
      val eventJson = mapper.writeValueAsString(event)
      KafkaClient.send(eventJson, certificateTopic)
      logger.info(s"Certificate event published: userId=$userId, courseId=$courseId")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to publish certificate event: ${ex.getMessage}", ex)
    }
  }
  
  /**
   * Publish failed assessment event
   */
  def publishFailedEvent(request: AssessmentRequest, reason: String): Unit = {
    try {
      val event = new java.util.HashMap[String, AnyRef]()
      event.put("attemptId", request.attemptId)
      event.put("userId", request.userId)
      event.put("courseId", request.courseId)
      event.put("batchId", request.batchId)
      event.put("contentId", request.contentId)
      event.put("reason", reason)
      event.put("timestamp", System.currentTimeMillis().asInstanceOf[AnyRef])
      val eventJson = mapper.writeValueAsString(event)
      KafkaClient.send(eventJson, failedEventsTopic)
      logger.info(s"Failed event published: attemptId=${request.attemptId}, reason=$reason")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to publish failed event: ${ex.getMessage}", ex)
    }
  }
}
