package org.sunbird.provider

import org.sunbird.common.request.Request

import java.util

trait Meet {

  /* Gets the meeting details */
  def getAttendanceInfo(request: Request): util.Map[String, Any]
}
