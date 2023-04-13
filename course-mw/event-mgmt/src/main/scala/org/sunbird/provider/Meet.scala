package org.sunbird.provider

import org.sunbird.common.request.Request

import java.util

trait Meet {

  /* Gets the attendance details */
  def getAttendanceInfo(request: Request): util.Map[String, Any]

  /* Gets the recording details */
  def getRecordingInfo(request: Request): util.Map[String, Any]
}
