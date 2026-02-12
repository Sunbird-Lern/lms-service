package org.sunbird.activity.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext
import org.sunbird.kafka.KafkaClient

import java.util.UUID

class CertificateUtil {

  private val mapper = new ObjectMapper()

  def publishCertificateIssueEvent(userId: String, courseId: String, batchId: String, requestContext: RequestContext): Unit = {
    val topic = ProjectUtil.getConfigValue("kafka_topics_certificate_instruction")
    val ets = System.currentTimeMillis
    val mid = s"LP.$ets.${UUID.randomUUID}"
    val event = s"""{"eid": "BE_JOB_REQUEST","ets": $ets,"mid": "$mid","actor": {"id": "Course Certificate Generator","type": "System"},"context": {"pdata": {"ver": "1.0","id": "org.sunbird.platform"}},"object": {"id": "${batchId}_$courseId","type": "CourseCertificateGeneration"},"edata": {"userIds": ["$userId"],"action": "issue-certificate","iteration": 1, "trigger": "auto-issue","batchId": "$batchId","reIssue": false,"courseId": "$courseId"}}"""
    KafkaClient.send(event, topic)
  }
}

object CertificateUtil {
  def apply(): CertificateUtil = new CertificateUtil()
}
