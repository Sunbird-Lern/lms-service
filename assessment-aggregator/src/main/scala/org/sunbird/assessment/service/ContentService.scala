package org.sunbird.assessment.service

import org.sunbird.common.models.util.{HttpUtil, ProjectUtil, JsonKey, LoggerUtil}
import org.sunbird.common.request.RequestContext
import org.apache.commons.lang3.StringUtils
import scala.collection.JavaConverters._

case class ContentMetadata(isValid: Boolean, totalQuestions: Int)

class ContentService {
  private val logger = new LoggerUtil(classOf[ContentService])
  private val baseUrl = Option(ProjectUtil.getConfigValue("sunbird_api_base_url")).filter(StringUtils.isNotBlank).getOrElse("http://localhost:9000")
  private val contentReadPath = Option(ProjectUtil.getConfigValue("sunbird_content_read_api_path")).filter(StringUtils.isNotBlank).getOrElse("/content/v1/read/")

  def fetchMetadata(contentId: String, context: RequestContext): ContentMetadata = {
    val url = s"$baseUrl$contentReadPath$contentId"
    logger.info(context, s"Fetching content metadata from URL: $url")
    try {
      val headers = Map(JsonKey.AUTHORIZATION -> ProjectUtil.getConfigValue(JsonKey.EKSTEP_AUTHORIZATION)).asJava
      val response = HttpUtil.sendGetRequest(url, headers)
      if (StringUtils.isBlank(response)) return ContentMetadata(isValid = false, totalQuestions = 0)
      val respMap = ProjectUtil.convertJsonStringToMap(response).asInstanceOf[java.util.Map[String, AnyRef]].asScala
      val isOk = respMap.get("responseCode").exists(_.toString.equalsIgnoreCase("OK"))
      if (isOk) {
        val result: scala.collection.mutable.Map[String, AnyRef] = respMap.get("result").map(_.asInstanceOf[java.util.Map[String, AnyRef]].asScala).getOrElse(scala.collection.mutable.Map.empty)
        val content: scala.collection.mutable.Map[String, AnyRef] = result.get("content").map(_.asInstanceOf[java.util.Map[String, AnyRef]].asScala).getOrElse(scala.collection.mutable.Map.empty)
        val count = content.get("totalQuestions").map(_.toString.toDouble.toInt).getOrElse(0)
        ContentMetadata(isValid = true, totalQuestions = count)
      } else {
        ContentMetadata(isValid = false, totalQuestions = 0)
      }
    } catch {
      case ex: Exception =>
        logger.error(context, s"Error retrieving content metadata for $contentId: ${ex.getMessage}", ex)
        ContentMetadata(isValid = true, totalQuestions = 0)
    }
  }

  def isValidContent(contentId: String, context: RequestContext): Boolean = fetchMetadata(contentId, context).isValid
  def getQuestionCount(contentId: String, context: RequestContext): Int = fetchMetadata(contentId, context).totalQuestions
}
