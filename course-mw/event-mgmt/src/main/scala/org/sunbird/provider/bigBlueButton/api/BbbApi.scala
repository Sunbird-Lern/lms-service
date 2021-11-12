package org.sunbird.provider.bigBlueButton.api

import org.sunbird.common.models.util.JsonKey
import org.sunbird.common.request.Request
import org.sunbird.util.ProviderConstants
import org.sunbird.provider.Meet
import java.text.{DateFormat, SimpleDateFormat}
import java.util
import java.util.Date

class BbbApi extends Meet {

  val dateFormatWithTime: DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

  // --- BBB API implementation methods ------------------------------------

  def getAttendanceInfo(request: Request): util.Map[String, Any] = {
    val meetingData = request.get(ProviderConstants.BBB_DATA).asInstanceOf[util.Map[String, Any]]
    val attendanceMap = new java.util.HashMap[String, Any]
    if (null != meetingData) {
      val bbbEventId = meetingData.get(ProviderConstants.BBB_EVENT_ID).asInstanceOf[String]
      val bbbAttributes = meetingData.get(ProviderConstants.BBB_ATTRIBUTES).asInstanceOf[util.Map[String, Any]]
      attendanceMap.put(JsonKey.EVENT_ID, bbbAttributes.get(ProviderConstants.BBB_MEETING).asInstanceOf[util.Map[String, Any]].get(ProviderConstants.BBB_EXTERNAL_MEETING_ID).asInstanceOf[String])
      val attendee = bbbAttributes.get(ProviderConstants.BBB_ATTENDEE).asInstanceOf[util.Map[String, Any]]
      attendanceMap.put(JsonKey.USER_ID, attendee.get(ProviderConstants.BBB_EXTERNAL_USER_ID).asInstanceOf[String])
      val role = attendee.get(ProviderConstants.BBB_ROLE).asInstanceOf[String]
      role toLowerCase match {
        case ProviderConstants.BBB_MODERATOR =>
          attendanceMap.put(JsonKey.ROLE, JsonKey.ORGANIZER)
        case ProviderConstants.BBB_VIEWER =>
          attendanceMap.put(JsonKey.ROLE, JsonKey.VIEWER)
      }
      val isPresenter = attendee.get(ProviderConstants.BBB_PRESENTER).asInstanceOf[String]
      if (ProviderConstants.TRUE == isPresenter) attendanceMap.put(JsonKey.ROLE, JsonKey.PRESENTER)
      attendanceMap.put(ProviderConstants.BBB_PRESENTER, attendee.get(ProviderConstants.BBB_PRESENTER).asInstanceOf[String])
      val eventTimestamp = meetingData.get(ProviderConstants.BBB_EVENT).asInstanceOf[util.Map[String, Any]].get(ProviderConstants.BBB_TIMESTAMP).asInstanceOf[Long]
      bbbEventId match {
        case ProviderConstants.BBB_USER_JOINED =>
          attendanceMap.put(JsonKey.JOINED_DATE_TIME, dateFormatWithTime.format(new Date(eventTimestamp)))
        case ProviderConstants.BBB_USER_LEFT =>
          attendanceMap.put(JsonKey.LEFT_DATE_TIME, dateFormatWithTime.format(new Date(eventTimestamp)))
      }
    }
    attendanceMap
  }
}
