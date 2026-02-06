package org.sunbird.assessment.service

import org.sunbird.kafka.KafkaClient

trait KafkaClientWrapper {
  def send(event: String, topic: String): Unit
}

object DefaultKafkaClientWrapper extends KafkaClientWrapper {
  override def send(event: String, topic: String): Unit = KafkaClient.send(event, topic)
}
