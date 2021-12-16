package org.sunbird.provider.bigBlueButton.api

import org.sunbird.common.models.util.{JsonKey, LoggerUtil}
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.collections4.{CollectionUtils, MapUtils}
import org.sunbird.common.request.Request
import org.sunbird.util.ProviderConstants
import org.sunbird.provider.Meet
import org.sunbird.provider.bigBlueButton.entity.BBBException
import org.w3c.dom.{Document, Node}
import org.xml.sax.{InputSource, SAXException}

import util.control.Breaks._
import scala.util.control._
import scala.collection.JavaConversions._
import java.io._
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.text.{DateFormat, SimpleDateFormat}
import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory, ParserConfigurationException}
import java.util
import java.util.Date
import java.util.concurrent.TimeUnit

class BbbApi extends Meet {

  var logger = new LoggerUtil(this.getClass)
  // BBB server url
  private val bbbUrl = System.getenv(ProviderConstants.PROVIDER_BBB_SERVER_URL)
  //BBB security salt
  private val bbbSalt = System.getenv(ProviderConstants.PROVIDER_BBB_SECURE_SALT)
  val dateFormatWithTime: DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

  @throws[UnsupportedEncodingException]
  private def encode(msg: String) = URLEncoder.encode(msg, getParametersEncoding)

  // --- BBB API implementation methods ------------------------------------

  def getAttendanceInfo(request: Request): util.Map[String, Any] = {
    getWebhookInfo(request, ProviderConstants.CALLBACK_EVENT_ATTENDANCE)
  }

  def getRecordingInfo(request: Request): util.Map[String, Any] = {
    val response: util.Map[String, Any] = new util.HashMap[String, Any]()
    val bbbResponse: util.Map[String, Any] = getWebhookInfo(request, ProviderConstants.CALLBACK_EVENT_RECORDING)
    if (MapUtils.isNotEmpty(bbbResponse)) {
      val recordingList = bbbResponse.get(ProviderConstants.BBB_RESPONSE_RECORDINGS).asInstanceOf[util.List[util.Map[String, AnyRef]]]
      if (CollectionUtils.isNotEmpty(recordingList)) {
        val recording = recordingList.get(0)
        val formatList = recording.get(ProviderConstants.BBB_RESPONSE_PLAYBACK).asInstanceOf[util.List[util.Map[String, AnyRef]]]
        if (CollectionUtils.isNotEmpty(formatList)) {
          if (formatList.exists(format => ProviderConstants.BBB_RESPONSE_PRESENTATION.equalsIgnoreCase(format.get(ProviderConstants.BBB_RESPONSE_TYPE).asInstanceOf[String]))) {
            val format = formatList.filter(format => ProviderConstants.BBB_RESPONSE_PRESENTATION.equalsIgnoreCase(format.get(ProviderConstants.BBB_RESPONSE_TYPE).asInstanceOf[String])).get(0)
            response.put(JsonKey.EVENT_ID, bbbResponse.get(JsonKey.EVENT_ID))
            response.put(JsonKey.RECORD_ID, recording.get(ProviderConstants.BBB_RESPONSE_RECORD_ID).asInstanceOf[String])
            val recordingResponse: util.Map[String, Any] = new util.HashMap[String, Any]()
            recordingResponse.put(JsonKey.RECORDING_URL, format.get(ProviderConstants.BBB_RESPONSE_URL))
            val startTimeStr = recording.get(ProviderConstants.BBB_RESPONSE_START_TIME).asInstanceOf[String]
            val startDateTime: Date = new Date(startTimeStr.toLong)
            recordingResponse.put(JsonKey.RECORDING_START_TIME, dateFormatWithTime.format(startDateTime))
            val endTimeStr = recording.get(ProviderConstants.BBB_RESPONSE_END_TIME).asInstanceOf[String]
            val endDateTime: Date = new Date(endTimeStr.toLong)
            recordingResponse.put(JsonKey.RECORDING_END_TIME, dateFormatWithTime.format(endDateTime))
            val duration = endDateTime.getTime - startDateTime.getTime
            recordingResponse.put(JsonKey.DURATION, TimeUnit.MILLISECONDS.toSeconds(duration))
            response.put(JsonKey.RECORDING, recordingResponse)
          }
        }
      }
    }
    response
  }

  private def getWebhookInfo(request: Request, callbackEvent: String): util.Map[String, Any] = {
    val meetingData = request.get(ProviderConstants.BBB_EVENT_DATA).asInstanceOf[util.Map[String, Any]]
    logger.info(request.getRequestContext, "BBBApi::getWebhookInfo::meetingData : " + meetingData)
    val attendanceMap = new java.util.HashMap[String, Any]
    if (null != meetingData) {
      val bbbEventId = meetingData.get(ProviderConstants.BBB_EVENT_DATA_ID).asInstanceOf[String]
      val eventTimestamp = meetingData.get(ProviderConstants.BBB_EVENT_DATA_EVENT).asInstanceOf[util.Map[String, Any]].get(ProviderConstants.BBB_EVENT_TIMESTAMP).asInstanceOf[Number].longValue() // Timestamp can be Integer/Long
      val bbbAttributes = meetingData.get(ProviderConstants.BBB_EVENT_DATA_ATTRIBUTES).asInstanceOf[util.Map[String, Any]]
      attendanceMap.put(JsonKey.EVENT_ID, bbbAttributes.get(ProviderConstants.BBB_EVENT_ATTRIBUTE_MEETING).asInstanceOf[util.Map[String, Any]].get(ProviderConstants.BBB_MEETING_EXTERNAL_MEETING_ID).asInstanceOf[String])
      callbackEvent match {
        case ProviderConstants.CALLBACK_EVENT_ATTENDANCE => bbbEventId match {
          case ProviderConstants.BBB_EVENT_USER_JOINED =>
            attendanceMap.put(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT, JsonKey.ONLINE_PROVIDER_EVENT_USER_JOINED)
            attendanceMap.put(JsonKey.JOINED_DATE_TIME, dateFormatWithTime.format(new Date(eventTimestamp)))
          case ProviderConstants.BBB_EVENT_USER_LEFT =>
            attendanceMap.put(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT, JsonKey.ONLINE_PROVIDER_EVENT_USER_LEFT)
            attendanceMap.put(JsonKey.LEFT_DATE_TIME, dateFormatWithTime.format(new Date(eventTimestamp)))
          case ProviderConstants.BBB_EVENT_MEETING_ENDED =>
            attendanceMap.put(JsonKey.ONLINE_PROVIDER_CALLBACK_EVENT, JsonKey.ONLINE_PROVIDER_EVENT_MEETING_ENDED)
            attendanceMap.put(JsonKey.LEFT_DATE_TIME, dateFormatWithTime.format(new Date(eventTimestamp)))
          case _ =>
            logger.info(request.getRequestContext, s"Event $bbbEventId is other than ${ProviderConstants.BBB_EVENT_USER_JOINED} and ${ProviderConstants.BBB_EVENT_USER_LEFT}")
            return null
        }
        case ProviderConstants.CALLBACK_EVENT_RECORDING => bbbEventId match {
          case ProviderConstants.BBB_EVENT_RECORDING =>
            val recordId = bbbAttributes.get(ProviderConstants.BBB_EVENT_ATTRIBUTE_RECORD_ID).asInstanceOf[String]
            attendanceMap.putAll(getRecordings(recordId))
          case _ =>
            logger.info(request.getRequestContext, s"Event $bbbEventId is other than ${ProviderConstants.BBB_EVENT_RECORDING}")
            return null
        }
        case _ =>
          logger.info(request.getRequestContext, s"Event $bbbEventId is other than ${ProviderConstants.BBB_EVENT_USER_JOINED}, ${ProviderConstants.BBB_EVENT_USER_LEFT} and ${ProviderConstants.BBB_EVENT_RECORDING}")
          return null
      }
      // User attribute
      val attendee = bbbAttributes.get(ProviderConstants.BBB_EVENT_ATTRIBUTE_USER).asInstanceOf[util.Map[String, Any]]
      if (MapUtils.isNotEmpty(attendee)) {
        getAttendeeAttributes(attendanceMap, attendee, bbbEventId)
      }
    }
    logger.info(request.getRequestContext, "BBBApi::getWebhookInfo::attendanceMap : " + attendanceMap)
    attendanceMap
  }

  private def getAttendeeAttributes(attendanceMap: util.Map[String, Any], attendee: util.Map[String, Any], bbbEventId: String) = {
    attendanceMap.put(JsonKey.USER_ID, attendee.get(ProviderConstants.BBB_USER_EXTERNAL_USER_ID).asInstanceOf[String])
    if (ProviderConstants.BBB_EVENT_USER_JOINED.equalsIgnoreCase(bbbEventId)) {
      val role = attendee.get(ProviderConstants.BBB_USER_ROLE).asInstanceOf[String]
      role toLowerCase match {
        case ProviderConstants.BBB_USER_ROLE_MODERATOR =>
          attendanceMap.put(JsonKey.ROLE, JsonKey.ORGANIZER)
        case ProviderConstants.BBB_USER_ROLE_VIEWER =>
          attendanceMap.put(JsonKey.ROLE, JsonKey.VIEWER)
      }
      val isPresenter = attendee.get(ProviderConstants.BBB_USER_PRESENTER).asInstanceOf[Boolean]
      if (isPresenter) attendanceMap.put(JsonKey.ROLE, JsonKey.PRESENTER)
    }
    attendanceMap
  }

  @throws[BBBException]
  private def getRecordings(recordId: String): util.Map[String, AnyRef] = try {
    val query = new StringBuilder
    if (recordId != null) query.append(ProviderConstants.QUERY_PARAM_RECORD_ID + recordId)
    query.append(getCheckSumParameterForQuery(ProviderConstants.API_CALL_GET_RECORDS, query.toString))
    doAPICall(ProviderConstants.API_CALL_GET_RECORDS, query.toString)
  } catch {
    case e: BBBException =>
      throw new BBBException(e.getMessageKey, e.getMessage, e)
    case e: IOException =>
      throw new BBBException(BBBException.MESSAGE_KEY_INTERNAL_ERROR, e.getMessage, e)
  }

  // --- BBB API utility methods -------------------------------------------

  /** Compute the query string checksum based on the security salt */
  private def getCheckSumParameterForQuery(apiCall: String, queryString: String): String = if (bbbSalt != null) ProviderConstants.QUERY_PARAM_CHECKSUM + DigestUtils.shaHex(apiCall + queryString + bbbSalt)
  else ""

  /** Encoding used when encoding url parameters */
  private def getParametersEncoding = ProviderConstants.ENCODE

  /* Make an API call */
  @throws[BBBException]
  private def doAPICall(apiCall: String, query: String): util.Map[String, AnyRef] = {
    val urlStr = new StringBuilder(bbbUrl)
    if (urlStr.toString.endsWith(ProviderConstants.API_SERVER_PATH_TO_CHECK)) urlStr.append(ProviderConstants.URL_PATH_SLASH)
    else urlStr.append(ProviderConstants.API_SERVER_PATH)
    urlStr.append(apiCall)
    if (query != null) {
      urlStr.append(ProviderConstants.URL_PATH_QUESTION_MARK)
      urlStr.append(query)
    }
    try { // open connection
      val url = new URL(urlStr.toString)
      val httpConnection = url.openConnection.asInstanceOf[HttpURLConnection]
      apiCall match {
        case ProviderConstants.API_CALL_CREATE => httpConnection.setRequestMethod(ProviderConstants.API_POST)
          httpConnection.setRequestProperty("Content-Length", "" + String.valueOf(0))
          httpConnection.setRequestProperty("Accept", "" + "*/*")
          httpConnection.setRequestProperty("Accept-Encoding", "" + "gzip, deflate, br")
          httpConnection.setRequestProperty("Connection", "" + "keep-alive")
        case _ => httpConnection.setRequestMethod(ProviderConstants.API_GET)
      }
      httpConnection.connect()
      val responseCode = httpConnection.getResponseCode
      if (responseCode == HttpURLConnection.HTTP_OK) { // read response
        var isr: InputStreamReader = null
        val reader: BufferedReader = null
        val xml = new StringBuilder
        try {
          isr = new InputStreamReader(httpConnection.getInputStream, ProviderConstants.ENCODE)
          val reader = new BufferedReader(isr)
          var line = reader.readLine()
          while ( {
            line != null
          }) {
            if (!line.startsWith("<?xml version=\"1.0\"?>")) xml.append(line.trim)
            line = reader.readLine
          }
        } finally {
          if (reader != null) reader.close()
          if (isr != null) isr.close()
        }
        httpConnection.disconnect()
        // parse response
        var stringXml = xml.toString

        stringXml = stringXml.replaceAll(">.\\s+?<", "><")
        if (apiCall == ProviderConstants.API_CALL_GET_CONFIG_XML) {
          val map = new util.HashMap[String, AnyRef]
          map.put("xml", stringXml)
          return map
        }
        var dom: Document = null
        // Initialize XML libraries
        var docBuilderFactory: DocumentBuilderFactory = null
        var docBuilder: DocumentBuilder = null
        docBuilderFactory = DocumentBuilderFactory.newInstance
        try {
          docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
          docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
          docBuilder = docBuilderFactory.newDocumentBuilder
          dom = docBuilder.parse(new InputSource(new StringReader(stringXml)))
        } catch {
          case e: ParserConfigurationException =>

        }
        val response = getNodesAsMap(dom, ProviderConstants.BBB_RESPONSE)
        val returnCode = response.get(ProviderConstants.BBB_RESPONSE_RETURN_CODE).asInstanceOf[String]
        if (ProviderConstants.API_RESPONSE_FAILED == returnCode) throw new BBBException(response.get(ProviderConstants.BBB_RESPONSE_MESSAGE_KEY).asInstanceOf[String], response.get(ProviderConstants.BBB_RESPONSE_MESSAGE).asInstanceOf[String], null)
        response
      }
      else throw new BBBException(BBBException.MESSAGE_KEY_HTTP_ERROR, "BBB server responded with HTTP status code " + responseCode, null)
    } catch {
      case e: BBBException =>
        throw new BBBException(e.getMessageKey, e.getMessage, e)
      case e: IOException =>
        throw new BBBException(BBBException.MESSAGE_KEY_UNREACHABLE, e.getMessage, e)
      case e: SAXException =>
        throw new BBBException(BBBException.MESSAGE_KEY_INVALID_RESPONSE, e.getMessage, e)
      case e: IllegalArgumentException =>
        throw new BBBException(BBBException.MESSAGE_KEY_INVALID_RESPONSE, e.getMessage, e)
      case e: Exception =>
        throw new BBBException(BBBException.MESSAGE_KEY_UNREACHABLE, e.getMessage, e)
    }
  }

  // --- BBB Other utility methods -----------------------------------------

  /** Get all nodes under the specified element tag name as a map */
  private def getNodesAsMap(dom: Document, elementTagName: String): util.Map[String, AnyRef] = {
    val firstNode = dom.getElementsByTagName(elementTagName).item(0)
    processNode(firstNode)
  }

  private def processNode(_node: Node): util.Map[String, AnyRef] = {
    val map = new util.HashMap[String, AnyRef]
    val responseNodes = _node.getChildNodes
    var images = 1 //counter for images (i.e image1, image2, image3)
    for (i <- 0 until responseNodes.getLength) {
      val node = responseNodes.item(i)
      val nodeName = node.getNodeName.trim
      if (node.getChildNodes.getLength == 1 && (node.getChildNodes.item(0).getNodeType == org.w3c.dom.Node.TEXT_NODE || node.getChildNodes.item(0).getNodeType == org.w3c.dom.Node.CDATA_SECTION_NODE)) {
        val nodeValue = node.getTextContent
        if ((nodeName eq "image") && node.getAttributes != null) {
          val imageMap = new util.HashMap[String, String]
          val heightAttr = node.getAttributes.getNamedItem("height")
          val widthAttr = node.getAttributes.getNamedItem("width")
          val altAttr = node.getAttributes.getNamedItem("alt")
          imageMap.put("height", heightAttr.getNodeValue)
          imageMap.put("width", widthAttr.getNodeValue)
          imageMap.put("title", altAttr.getNodeValue)
          imageMap.put("url", nodeValue)
          map.put(nodeName + images, imageMap)
          images += 1
        }
        else map.put(nodeName, if (nodeValue != null) nodeValue.trim
        else null)
      }
      else if (node.getChildNodes.getLength == 0 && node.getNodeType != org.w3c.dom.Node.TEXT_NODE && node.getNodeType != org.w3c.dom.Node.CDATA_SECTION_NODE) map.put(nodeName, "")
      // Below code could be needed when deep xml response read
      else if (node.getChildNodes.getLength >= 1) {
        var isList = false
        val outLoop = new Breaks
        val inLoop = new Breaks
        outLoop.breakable {
          for (c <- 0 until node.getChildNodes.getLength) {
            breakable {
              try {
                val n = node.getChildNodes.item(c)
                if (n.getChildNodes.item(0).getNodeType != org.w3c.dom.Node.TEXT_NODE && n.getChildNodes.item(0).getNodeType != org.w3c.dom.Node.CDATA_SECTION_NODE) {
                  isList = true
                  outLoop.break
                }
              } catch {
                case e: Exception =>
                  inLoop.break
              }
            }
          }
        }
        val list = new util.ArrayList[AnyRef]
        if (isList) {
          for (c <- 0 until node.getChildNodes.getLength) {
            val n = node.getChildNodes.item(c)
            list.add(processNode(n))
          }
          if (nodeName eq "preview") {
            val n = node.getChildNodes.item(0)
            map.put(nodeName, new util.ArrayList[AnyRef](processNode(n).values))
          }
          else map.put(nodeName, list)
        }
        else map.put(nodeName, processNode(node))
      }
      else map.put(nodeName, processNode(node))
    }
    map
  }
}
