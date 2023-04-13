package org.sunbird.provider

import org.sunbird.common.models.util.JsonKey
import org.sunbird.common.request.Request
import org.sunbird.provider.bigBlueButton.api.BbbApi
import org.sunbird.util.ProviderConstants

import java.util
import scala.language.postfixOps

object Provider {

  def getAttendanceInfo(request: Request): util.Map[String, Any] = {
    val onlineProvider = request.get(JsonKey.ONLINE_PROVIDER).asInstanceOf[String]
    val providerApiObject = onlineProvider toLowerCase match {
      case ProviderConstants.BIG_BLUE_BUTTON =>
        new BbbApi()
      // Set response of other onlineProviders
    }
    providerApiObject.getAttendanceInfo(request)
  }

  def getRecordingInfo(request: Request): util.Map[String, Any] = {
    val onlineProvider = request.get(JsonKey.ONLINE_PROVIDER).asInstanceOf[String]
    val providerApiObject = onlineProvider toLowerCase match {
      case ProviderConstants.BIG_BLUE_BUTTON =>
        new BbbApi()
      // Set response of other onlineProviders
    }
    providerApiObject.getRecordingInfo(request)
  }
}