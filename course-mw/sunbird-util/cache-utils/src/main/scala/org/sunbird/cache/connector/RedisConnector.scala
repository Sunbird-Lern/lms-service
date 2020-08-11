package org.sunbird.cache.connector

import java.time.Duration

import org.sunbird.cache.platform.Platform
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

class RedisConnector {

    implicit val className = "org.sunbird.cache.connector.RedisConnector"

    private val redis_host = Platform.getString("redis.host", "localhost")
    private val redis_port = Platform.getInteger("redis.port", 6379)
    private val index = Platform.getInteger("redis.dbIndex", 0)


    private def buildPoolConfig = {
        val poolConfig = new JedisPoolConfig
        poolConfig.setMaxTotal(Platform.getInteger("redis.connection.max", 2))
        poolConfig.setMaxIdle(Platform.getInteger("redis.connection.idle.max", 2))
        poolConfig.setMinIdle(Platform.getInteger("redis.connection.idle.min", 1))
        poolConfig.setTestWhileIdle(true)
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(Platform.getLong("redis.connection.minEvictableIdleTimeSeconds", 120)).toMillis)
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(Platform.getLong("redis.connection.timeBetweenEvictionRunsSeconds", 300)).toMillis)
        poolConfig.setBlockWhenExhausted(true)
        poolConfig
    }

    protected var jedisPool: JedisPool = new JedisPool(buildPoolConfig, redis_host, redis_port)

    def getConnection(database: Int): Jedis = {
        val conn = jedisPool.getResource
        conn.select(database)
        conn
    }

    def getConnection: Jedis = try {
        val jedis = jedisPool.getResource
        if (index > 0) jedis.select(index)
        jedis
    } catch {
        case e: Exception => throw e
    }

    /**
      * This Method takes a connection object and put it back to pool.
      *
      * @param jedis
      */
    protected def returnConnection(jedis: Jedis): Unit = {
        try if (null != jedis) jedisPool.returnResource(jedis)
        catch {
            case e: Exception => throw e
        }
    }


    def resetConnection(): Unit = {
        jedisPool.close()
        jedisPool = new JedisPool(buildPoolConfig, redis_host, redis_port)
    }

    def closePool() = {
        jedisPool.close()
    }

    def checkConnection = {
        try {
            val conn = getConnection(2)
            conn.close()
            true;
        } catch {
            case ex: Exception => false
        }
    }

}
