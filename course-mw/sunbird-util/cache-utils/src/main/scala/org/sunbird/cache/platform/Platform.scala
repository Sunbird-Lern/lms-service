package org.sunbird.cache.platform

import com.typesafe.config.{Config, ConfigFactory}

object Platform {
    val defaultConf = ConfigFactory.load()
    val envConf = ConfigFactory.systemEnvironment()
    val config = envConf.withFallback(defaultConf)

    def getString(key: String, default: String): String = if (config.hasPath(key)) config.getString(key)
    else default

    def getInteger(key: String, default: Integer): Integer = if (config.hasPath(key)) config.getInt(key)
    else default

    def getBoolean(key: String, default: Boolean): Boolean = if (config.hasPath(key)) config.getBoolean(key)
    else default

    def getStringList(key: String, default: java.util.List[String]): java.util.List[String] = if (config.hasPath(key)) config.getStringList(key)
    else default

    def getLong(key: String, default: Long): Long = if (config.hasPath(key)) config.getLong(key)
    else default

    def getDouble(key: String, default: Double): Double = if (config.hasPath(key)) config.getDouble(key)
    else default
    
}
