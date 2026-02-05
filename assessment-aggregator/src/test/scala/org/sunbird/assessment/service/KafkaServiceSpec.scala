package org.sunbird.assessment.service

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.util.HashMap

class KafkaServiceSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "KafkaService" should "publish certificate event" in {
    val mClient = mock[KafkaClientWrapper]
    val service = new KafkaService(Some(mClient))
    service.publishCertificateEvent("u1", "c1", "b1", "a1")
    verify(mClient).send(anyString, any)
  }

  it should "handle exception in publishCertificateEvent" in {
    val mClient = mock[KafkaClientWrapper]
    when(mClient.send(anyString, any)).thenThrow(new RuntimeException("Kafka error"))
    val service = new KafkaService(Some(mClient))
    // Should not throw exception
    service.publishCertificateEvent("u1", "c1", "b1", "a1")
  }

  it should "publish failed event" in {
    val mClient = mock[KafkaClientWrapper]
    val service = new KafkaService(Some(mClient))
    val eventMap = new HashMap[String, AnyRef]()
    eventMap.put("attemptId", "a1")
    service.publishFailedEvent(eventMap, "Validation failed")
    verify(mClient).send(anyString, any)
  }

  it should "handle exception in publishFailedEvent" in {
    val mClient = mock[KafkaClientWrapper]
    when(mClient.send(anyString, any)).thenThrow(new RuntimeException("Kafka error"))
    val service = new KafkaService(Some(mClient))
    val eventMap = new HashMap[String, AnyRef]()
    service.publishFailedEvent(eventMap, "Validation failed")
  }
}
