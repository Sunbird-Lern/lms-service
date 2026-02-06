package org.sunbird.assessment.service

import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.request.RequestContext

class ContentServiceSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  "ContentService" should "fetch metadata successfully" in {
    val mHttp = mock[HttpUtilWrapper]
    val response = """{"responseCode":"OK","result":{"content":{"totalQuestions":10}}}"""
    when(mHttp.sendGetRequest(anyString, any)).thenReturn(response)
    
    val service = new ContentService(Some(mHttp))
    val metadata = service.fetchMetadata("cont1", mock[RequestContext])
    metadata.isValid should be (true)
    metadata.totalQuestions should be (10)
  }

  it should "handle blank response" in {
    val mHttp = mock[HttpUtilWrapper]
    when(mHttp.sendGetRequest(anyString, any)).thenReturn("")
    
    val service = new ContentService(Some(mHttp))
    val metadata = service.fetchMetadata("cont1", mock[RequestContext])
    metadata.isValid should be (false)
  }

  it should "handle non-OK responseCode" in {
    val mHttp = mock[HttpUtilWrapper]
    val response = """{"responseCode":"CLIENT_ERROR"}"""
    when(mHttp.sendGetRequest(anyString, any)).thenReturn(response)
    
    val service = new ContentService(Some(mHttp))
    val metadata = service.fetchMetadata("cont1", mock[RequestContext])
    metadata.isValid should be (false)
  }

  it should "handle exception and return valid = true with 0 questions" in {
    val mHttp = mock[HttpUtilWrapper]
    when(mHttp.sendGetRequest(anyString, any)).thenThrow(new RuntimeException("API error"))
    
    val service = new ContentService(Some(mHttp))
    val metadata = service.fetchMetadata("cont1", mock[RequestContext])
    metadata.isValid should be (true)
    metadata.totalQuestions should be (0)
  }
}
