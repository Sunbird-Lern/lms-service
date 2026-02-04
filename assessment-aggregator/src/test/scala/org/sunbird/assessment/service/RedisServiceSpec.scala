package org.sunbird.assessment.service

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.redisson.api.{RMap, RSet, RedissonClient}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisServiceSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "RedisService" should "return true for isValidContent if set is empty" in {
    val mClient = mock[RedissonClient]
    val mSet = mock[RSet[String]]
    when(mClient.getSet[String](anyString)).thenReturn(mSet)
    when(mSet.isEmpty).thenReturn(true)
    
    val service = new RedisService(Some(mClient))
    service.isValidContent("c1", "cont1") should be (true)
  }

  it should "return true for isValidContent if content exists in set" in {
    val mClient = mock[RedissonClient]
    val mSet = mock[RSet[String]]
    when(mClient.getSet[String](anyString)).thenReturn(mSet)
    when(mSet.isEmpty).thenReturn(false)
    when(mSet.contains("cont1")).thenReturn(true)
    
    val service = new RedisService(Some(mClient))
    service.isValidContent("c1", "cont1") should be (true)
  }

  it should "return false for isValidContent if content does not exist in set" in {
    val mClient = mock[RedissonClient]
    val mSet = mock[RSet[String]]
    when(mClient.getSet[String](anyString)).thenReturn(mSet)
    when(mSet.isEmpty).thenReturn(false)
    when(mSet.contains("cont1")).thenReturn(false)
    
    val service = new RedisService(Some(mClient))
    service.isValidContent("c1", "cont1") should be (false)
  }

  it should "return true for isValidContent on exception" in {
    val mClient = mock[RedissonClient]
    when(mClient.getSet[String](anyString)).thenThrow(new RuntimeException("Redis down"))
    
    val service = new RedisService(Some(mClient))
    service.isValidContent("c1", "cont1") should be (true)
  }

  it should "return total questions count from map" in {
    val mClient = mock[RedissonClient]
    val mMap = mock[RMap[String, AnyRef]]
    when(mClient.getMap[String, AnyRef](anyString)).thenReturn(mMap)
    when(mMap.isExists).thenReturn(true)
    when(mMap.containsKey("totalquestions")).thenReturn(true)
    when(mMap.get("totalquestions")).thenReturn(15.asInstanceOf[AnyRef])
    
    val service = new RedisService(Some(mClient))
    service.getTotalQuestionsCount("cont1") should be (Some(15))
  }

  it should "return None if map does not exist" in {
    val mClient = mock[RedissonClient]
    val mMap = mock[RMap[String, AnyRef]]
    when(mClient.getMap[String, AnyRef](anyString)).thenReturn(mMap)
    when(mMap.isExists).thenReturn(false)
    
    val service = new RedisService(Some(mClient))
    service.getTotalQuestionsCount("cont1") should be (None)
  }

  it should "handle different types in extractCount" in {
    val service = new RedisService(Some(mock[RedissonClient]))
    val extractCount = service.getClass.getDeclaredMethod("extractCount", classOf[AnyRef])
    extractCount.setAccessible(true)
    
    extractCount.invoke(service, 10.asInstanceOf[AnyRef]) should be (Some(10))
    extractCount.invoke(service, 10.0.asInstanceOf[AnyRef]) should be (Some(10))
    extractCount.invoke(service, 10L.asInstanceOf[AnyRef]) should be (Some(10))
    extractCount.invoke(service, "10") should be (Some(10))
    extractCount.invoke(service, "invalid") should be (None)
    extractCount.invoke(service, null) should be (None)
    extractCount.invoke(service, new Object()) should be (None)
  }
}
