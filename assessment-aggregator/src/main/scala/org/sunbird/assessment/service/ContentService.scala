package org.sunbird.assessment.service

import org.sunbird.common.models.util.{HttpUtil, ProjectUtil, JsonKey}
import org.slf4j.LoggerFactory
import org.apache.commons.lang3.StringUtils
import scala.collection.JavaConverters._

/**
 * Service to interact with the Content Service APIs
 */
class ContentService {

  private val logger = LoggerFactory.getLogger(classOf[ContentService])
  private val baseUrl = ProjectUtil.getConfigValue("content_service_base_url")
  private val contentReadPath = "/content/v3/read/"

  def isValidContent(contentId: String): Boolean = {
    try {
      getQuestionCount(contentId) >= 0
    } catch {
      case _: Exception => true // Fallback to true to avoid blocking on API errors
    }
  }

  def getQuestionCount(contentId: String): Int = {
    val url = s"$baseUrl$contentReadPath$contentId"
    try {
      val headers = Map(
        JsonKey.AUTHORIZATION -> ProjectUtil.getConfigValue(JsonKey.EKSTEP_AUTHORIZATION)
      ).asJava
      
      val response = HttpUtil.sendGetRequest(url, headers)
      if (StringUtils.isNotBlank(response)) {
        val respMap = ProjectUtil.convertJsonStringToMap(response).asInstanceOf[java.util.Map[String, AnyRef]]
        if (respMap.getOrDefault("responseCode", "").toString.equalsIgnoreCase("OK")) {
          val result = respMap.getOrDefault("result", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
          val content = result.getOrDefault("content", new java.util.HashMap[String, AnyRef]()).asInstanceOf[java.util.Map[String, AnyRef]]
          val totalQuestions = content.get("totalQuestions")
          val count = if (totalQuestions != null) totalQuestions.asInstanceOf[Number].intValue() else 0
          logger.info(s"Fetched the totalQuestion Value from the Content Read API - ContentId:$contentId, TotalQuestionCount:$count")
          count
        } else {
          logger.error(s"API Failed to Fetch the TotalQuestion Count - ContentId:$contentId, ResponseCode - ${respMap.get("responseCode")}")
          throw new RuntimeException(s"Failed to fetch content metadata for $contentId. ResponseCode: ${respMap.get("responseCode")}")
        }
      } else {
        throw new RuntimeException(s"Empty response from Content Read API for contentId: $contentId")
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Error retrieving question count for contentId=$contentId: ${ex.getMessage}", ex)
        throw ex
    }
  }
}
