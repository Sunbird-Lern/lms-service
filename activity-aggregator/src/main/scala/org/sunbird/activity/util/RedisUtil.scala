package org.sunbird.activity.util

import org.apache.commons.collections.CollectionUtils
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.logging.LoggerUtil
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext

import scala.collection.JavaConverters._

class RedisUtil(implicit val cacheUtil: RedisCacheUtil) {

  private val logger = new LoggerUtil(classOf[RedisUtil])

  private val relationCacheDb = ProjectUtil.getConfigValue("redis_relation_cache_index") match {
    case value if value != null => value.toInt
    case _ => 10
  }

  def readFromCache(key: String, requestContext: RequestContext): List[String] = {
    logger.info(requestContext, s"RedisUtil.readFromCache: key: $key, dbIndex: $relationCacheDb")
    try {
      val list = cacheUtil.getList(key, relationCacheDb)
      if (CollectionUtils.isEmpty(list.asJava)) {
        logger.info(requestContext, s"RedisUtil: No data found in Redis for key: $key, dbIndex: $relationCacheDb")
        List.empty
      } else {
        logger.info(requestContext, s"RedisUtil: Found ${list.size} items for key: $key")
        list
      }
    } catch {
      case ex: Exception =>
        logger.error(requestContext, s"RedisUtil: Error reading from Redis for key: $key, dbIndex: $relationCacheDb", ex)
        List.empty
    }
  }

  def getLeafNodes(courseId: String, collectionId: String, requestContext: RequestContext): List[String] = {
    val key = s"$courseId:$collectionId:leafnodes"
    logger.info(requestContext, s"RedisUtil: Getting leaf nodes for courseId: $courseId, collectionId: $collectionId")
    val nodes = readFromCache(key, requestContext).distinct
    logger.info(requestContext, s"RedisUtil: Retrieved ${nodes.size} distinct leaf nodes")
    nodes
  }

  def getOptionalNodes(courseId: String, collectionId: String, requestContext: RequestContext): List[String] = {
    val key = s"$courseId:$collectionId:optionalnodes"
    logger.info(requestContext, s"RedisUtil: Getting optional nodes for courseId: $courseId, collectionId: $collectionId")
    val nodes = readFromCache(key, requestContext).distinct
    logger.info(requestContext, s"RedisUtil: Retrieved ${nodes.size} distinct optional nodes")
    nodes
  }

  def getAncestors(courseId: String, contentId: String, requestContext: RequestContext): List[String] = {
    val key = s"$courseId:$contentId:ancestors"
    logger.info(requestContext, s"RedisUtil: Getting ancestors for courseId: $courseId, contentId: $contentId")
    val ancestors = readFromCache(key, requestContext)
    logger.info(requestContext, s"RedisUtil: Retrieved ${ancestors.size} ancestors")
    ancestors
  }

  def getRequiredLeafNodes(courseId: String, collectionId: String, requestContext: RequestContext): List[String] = {
    logger.info(requestContext, s"RedisUtil: Getting required leaf nodes (excluding optional) for courseId: $courseId, collectionId: $collectionId")
    val leafNodes = getLeafNodes(courseId, collectionId, requestContext)
    val optionalNodes = getOptionalNodes(courseId, collectionId, requestContext)
    val required = leafNodes.diff(optionalNodes)
    logger.info(requestContext, s"RedisUtil: Required leaf nodes: ${required.size} (total: ${leafNodes.size}, optional: ${optionalNodes.size})")
    required
  }
}

object RedisUtil {
  def apply()(implicit cacheUtil: RedisCacheUtil): RedisUtil = new RedisUtil()
}
