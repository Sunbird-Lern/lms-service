package org.sunbird.provider

import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.provider.bigBlueButton.api.BbbApi
import org.sunbird.util.ProviderConstants
import scala.collection.JavaConverters._


import java.util
import scala.language.postfixOps

object Provider {

  def getEventMeetingInfo(onlineProvider: String, meetingId: String): util.Map[String, Any] = {
    val providerApiObject = onlineProvider toLowerCase match {
      case ProviderConstants.BIG_BLUE_BUTTON =>
        new BbbApi()
      case _ =>
        // Set response of Meeting URL for other onlineProviders
        throw new ProjectCommonException(ResponseCode.onlineProviderMissing.getErrorCode, ResponseCode.onlineProviderMissing.getErrorMessage, ResponseCode.CLIENT_ERROR.getResponseCode)
    }
    val meetingInfo = providerApiObject.getMeetingInfo(meetingId)
    // Converting object to map
    if (null != meetingInfo) {
      val mapMeetingResponse: Map[String, Any] = meetingInfo.getClass.getDeclaredFields.foldLeft(Map.empty[String, Any]) { (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> f.get(meetingInfo))
      }
      mapMeetingResponse.asJava
    } else new java.util.HashMap[String, Any]()
  }
}