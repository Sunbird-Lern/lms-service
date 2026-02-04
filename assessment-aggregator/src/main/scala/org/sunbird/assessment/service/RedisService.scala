package org.sunbird.assessment.service

import org.redisson.api.RedissonClient
import org.sunbird.redis.RedisConnectionManager
import org.slf4j.LoggerFactory

/**
 * Redis service using Sunbird's shared Redis connection
 */
class RedisService(client: Option[RedissonClient] = None) {
  
  private val logger = LoggerFactory.getLogger(classOf[RedisService])
  private val redisClient: RedissonClient = client.getOrElse(RedisConnectionManager.getClient())
  
  /**
   * Check if content is valid by verifying it exists in the course hierarchy
   */
  def isValidContent(courseId: String, contentId: String): Boolean = {
    try {
      val key = s"$courseId:$courseId:leafnodes"
      val set = redisClient.getSet[String](key)
      if (set.isEmpty) {
        true // If no hierarchy in Redis, allow processing
      } else {
        val isValid = set.contains(contentId)
        if (!isValid) {
          logger.warn(s"Content validation failed: contentId=$contentId not in courseId=$courseId leafnodes")
        }
        isValid
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to check content validity for courseId=$courseId: ${ex.getMessage}", ex)
        true // Return true to not block processing on Redis errors
    }
  }
  
  /**
   * Get total questions count for content from metadata
   */
  def getTotalQuestionsCount(contentId: String): Option[Int] = {
    try {
      val key = s"content:$contentId"
      val map = redisClient.getMap[String, AnyRef](key)
      if (map.isExists && map.containsKey("totalquestions")) {
        extractCount(map.get("totalquestions"))
      } else {
        None
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to get questions count for contentId=$contentId: ${ex.getMessage}", ex)
        None
    }
  }

  private def extractCount(value: AnyRef): Option[Int] = {
    value match {
      case i: java.lang.Integer => Some(i.toInt)
      case d: java.lang.Double => Some(d.toInt)
      case n: java.lang.Number => Some(n.intValue())
      case s: String => try { Some(s.toInt) } catch { case _: NumberFormatException => None }
      case _ => None
    }
  }
}
