package org.sunbird.assessment.service

import org.sunbird.kafka.KafkaClient
import org.sunbird.common.ProjectUtil
import org.sunbird.assessment.models.AssessmentRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Kafka service using Sunbird's shared Kafka client
 */
class KafkaService(client: Option[KafkaClientWrapper] = None) {
  
  private val logger = LoggerFactory.getLogger(classOf[KafkaService])
  private val mapper = new ObjectMapper()
  private val kafkaClient = client.getOrElse(DefaultKafkaClientWrapper)
  
  // Get topic names from configuration
  private val certificateTopic = ProjectUtil.getConfigValue("kafka_topics_certificate_instruction")
  private val failedEventsTopic = ProjectUtil.getConfigValue("kafka_topics_contentstate_invalid")
  
  /**
   * Publish certificate issue event
   */
  def publishCertificateEvent(userId: String, courseId: String, batchId: String, attemptId: String): Unit = {
    try {
      val ets = System.currentTimeMillis()
      val mid = s"LP.$ets.${java.util.UUID.randomUUID()}"
      val event = s"""{"eid":"BE_JOB_REQUEST","ets":$ets,"mid":"$mid","actor":{"id":"Course Certificate Generator","type":"System"},"context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}},"object":{"id":"${batchId}_$courseId","type":"CourseCertificateGeneration"},"edata":{"userIds":["$userId"],"action":"issue-certificate","iteration":1,"trigger":"auto-issue","batchId":"$batchId","reIssue":false,"courseId":"$courseId","attemptId":"$attemptId"}}"""
      kafkaClient.send(event, certificateTopic)
      logger.info(s"Certificate event published: userId=$userId, courseId=$courseId")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to publish certificate event: ${ex.getMessage}", ex)
    }
  }
  
  /**
   * Publish failed assessment event
   */
  def publishFailedEvent(eventMap: java.util.Map[String, AnyRef], reason: String): Unit = {
    try {
      val metadata = new java.util.HashMap[String, AnyRef]()
      metadata.put("validation_error", reason)
      metadata.put("src", "AssessmentAggregator")
      
      val flags = new java.util.HashMap[String, AnyRef]()
      flags.put("error_processed", true.asInstanceOf[AnyRef])

      eventMap.put("metadata", metadata)
      eventMap.put("flags", flags)
      
      val eventJson = mapper.writeValueAsString(eventMap)
      kafkaClient.send(eventJson, failedEventsTopic)
      logger.info(s"Failed event published: attemptId=${eventMap.get("attemptId")}, reason=$reason")
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to publish failed event: ${ex.getMessage}", ex)
    }
  }
}
