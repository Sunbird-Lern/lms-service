package org.sunbird.activity.util

import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext

import java.security.MessageDigest

class DeDupUtil(implicit cacheUtil: RedisCacheUtil) {

  private val deDupRedisIndex = ProjectUtil.getConfigValue("dedup_redis_index") match {
    case value if value != null => value.toInt
    case _ => 3
  }
  
  private val deDupExpirySec = ProjectUtil.getConfigValue("dedup_redis_expiry") match {
    case value if value != null => value.toInt
    case _ => 604800
  }
  
  private val dedupEnabled = ProjectUtil.getConfigValue("activity_input_dedup_enabled") match {
    case value if value != null => value.toBoolean
    case _ => false
  }

  def getMessageId(courseId: String, batchId: String, userId: String, contentId: String, status: Int): String = {
    val key = Array(courseId, batchId, userId, contentId, status).mkString("|")
    MessageDigest.getInstance("MD5").digest(key.getBytes).map("%02X".format(_)).mkString
  }

  def isUniqueEvent(checksum: String, requestContext: RequestContext): Boolean = {
    if (!dedupEnabled) {
      true
    } else {
      val jedis = cacheUtil.getConnection(deDupRedisIndex)
      try {
        !jedis.exists(checksum)
      } finally {
        jedis.close()
      }
    }
  }

  def storeChecksum(checksum: String, requestContext: RequestContext): Unit = {
    if (dedupEnabled) {
      val jedis = cacheUtil.getConnection(deDupRedisIndex)
      try {
        jedis.setex(checksum, deDupExpirySec, "1")
      } finally {
        jedis.close()
      }
    }
  }
}

object DeDupUtil {
  def apply()(implicit cacheUtil: RedisCacheUtil): DeDupUtil = new DeDupUtil()
}
