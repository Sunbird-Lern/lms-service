package org.sunbird.assessment.service

import org.sunbird.common.models.util.{HttpUtil, ProjectUtil, JsonKey, LoggerUtil}
import org.apache.commons.lang3.StringUtils
import scala.collection.JavaConverters._

case class ContentMetadata(isValid: Boolean, totalQuestions: Int)

class ContentService {
  private val logger = new LoggerUtil(classOf[ContentService])
  private val baseUrl = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_API_BASE_URL)
  private val contentReadPath = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_CONTENT_READ_API_PATH)

  def fetchMetadata(contentId: String): ContentMetadata = {
    val url = s"$baseUrl$contentReadPath$contentId"
    try {
      val headers = Map(JsonKey.AUTHORIZATION -> ProjectUtil.getConfigValue(JsonKey.EKSTEP_AUTHORIZATION)).asJava
      val response = HttpUtil.sendGetRequest(url, headers)
      if (StringUtils.isBlank(response)) return ContentMetadata(isValid = false, totalQuestions = 0)
      val respMap = ProjectUtil.convertJsonStringToMap(response).asScala
      val isOk = respMap.get("responseCode").exists(_.toString.equalsIgnoreCase("OK"))
      if (isOk) {
        val result = respMap.get("result").collect { case m: java.util.Map[_, _] => m.asScala }.getOrElse(Map.empty)
        val content = result.get("content").collect { case m: java.util.Map[_, _] => m.asScala }.getOrElse(Map.empty)
        val count = content.get("totalQuestions").map(_.toString.toDouble.toInt).getOrElse(0)
        ContentMetadata(isValid = true, totalQuestions = count)
      } else {
        ContentMetadata(isValid = false, totalQuestions = 0)
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Error retrieving content metadata for $contentId: ${ex.getMessage}", ex)
        ContentMetadata(isValid = true, totalQuestions = 0)
    }
  }

  def isValidContent(contentId: String): Boolean = fetchMetadata(contentId).isValid
  def getQuestionCount(contentId: String): Int = fetchMetadata(contentId).totalQuestions
}
