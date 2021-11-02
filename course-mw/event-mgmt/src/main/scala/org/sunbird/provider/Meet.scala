package org.sunbird.provider

trait Meet {

  /* Gets the meeting details */
  def getMeetingInfo(meetingID: String): Meeting
}
