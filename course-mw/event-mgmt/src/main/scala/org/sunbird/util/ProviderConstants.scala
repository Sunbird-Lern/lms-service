package org.sunbird.util

object ProviderConstants {

  // BigBlueButton
  val BIG_BLUE_BUTTON = "bigbluebutton"
  val PROVIDER_BBB_SERVER_URL = "provider_bbb_server_url"
  val PROVIDER_BBB_SECURE_SALT = "provider_bbb_secure_salt"

  // API method
  val API_POST = "POST"
  val API_GET = "GET"

  // API Server Path
  val API_SERVER_PATH = "/api/"
  val API_SERVER_PATH_TO_CHECK = "/api"
  val URL_PATH_SLASH = "/"
  val URL_PATH_QUESTION_MARK = "?"

  // API Calls
  val API_CALL_CREATE = "create"
  val API_CALL_GET_MEETING_INFO = "getMeetingInfo"
  val API_CALL_JOIN = "join"
  val API_CALL_GET_CONFIG_XML = "getDefaultConfigXML"
  val API_CALL_GET_RECORDS = "getRecordings"

  // API Response Codes
  val API_RESPONSE_SUCCESS = "SUCCESS"
  val API_RESPONSE_FAILED = "FAILED"

  // Query parameters
  val QUERY_PARAM_MEETING_ID = "meetingID="
  val QUERY_PARAM_NAME = "&name="
  val QUERY_PARAM_USER_ID = "&userID="
  val QUERY_PARAM_FULL_NAME = "&fullName="
  val QUERY_PARAM_PASSWORD = "&password="
  val QUERY_PARAM_CHECKSUM = "&checksum="
  val QUERY_PARAM_RECORD_ID = "&recordID="

  // Response parameters
  val BBB_RESPONSE = "response"
  val BBB_RESPONSE_MEETING_ID = "meetingID"
  val BBB_RESPONSE_MEETING_NAME = "meetingName"
  val BBB_RESPONSE_MODERATOR_PW = "moderatorPW"
  val BBB_RESPONSE_ATTENDEE_PW = "attendeePW"
  val BBB_RESPONSE_RETURN_CODE = "returncode"
  val BBB_RESPONSE_MESSAGE_KEY = "messageKey"
  val BBB_RESPONSE_MESSAGE = "message"

  val ENCODE = "UTF-8"
  val MODERATOR_USER = "Moderator"
  val ATTENDEE_USER = "Attendee"

  // BBB web hook events
  val BBB_EVENT_USER_JOINED = "user-joined"
  val BBB_EVENT_USER_LEFT = "user-left"
  val BBB_EVENT_MEETING_ENDED = "meeting-ended"
  val BBB_EVENT_RECORDING = "rap-post-publish-ended"
  // BBB web hook data
  val BBB_EVENT_DATA = "data"
  val BBB_EVENT_DATA_ID = "id"
  val BBB_EVENT_DATA_EVENT = "event"
  val BBB_EVENT_DATA_ATTRIBUTES = "attributes"
  val BBB_EVENT_TIMESTAMP = "ts"
  // BBB web hook user attributes
  val BBB_EVENT_ATTRIBUTE_USER = "user"
  val BBB_USER_ROLE = "role"
  val BBB_USER_PRESENTER = "presenter"
  val BBB_USER_EXTERNAL_USER_ID = "external-user-id"
  val BBB_USER_ROLE_MODERATOR = "moderator"
  val BBB_USER_ROLE_VIEWER = "viewer"
  // BBB web hook meeting attributes
  val BBB_EVENT_ATTRIBUTE_MEETING = "meeting"
  val BBB_MEETING_EXTERNAL_MEETING_ID = "external-meeting-id"
  // BBB web hook record attributes
  val BBB_EVENT_ATTRIBUTE_RECORD_ID = "record-id"
  val BBB_RESPONSE_RECORD_ID = "recordID"
  val BBB_RESPONSE_RECORDINGS = "recordings"
  val BBB_RESPONSE_START_TIME = "startTime"
  val BBB_RESPONSE_END_TIME = "endTime"
  val BBB_RESPONSE_PLAYBACK = "playback"
  val BBB_RESPONSE_PRESENTATION = "presentation"
  val BBB_RESPONSE_TYPE = "type"
  val BBB_RESPONSE_URL = "url"

  val TRUE = "true"

  val CALLBACK_EVENT_ATTENDANCE = "attendance-callback"
  val CALLBACK_EVENT_RECORDING = "recording-callback"
}
