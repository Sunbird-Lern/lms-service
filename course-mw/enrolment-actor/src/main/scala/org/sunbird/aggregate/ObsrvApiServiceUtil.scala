package org.sunbird.aggregate

import com.mashape.unirest.http.Unirest
import org.apache.http.HttpHeaders
import org.sunbird.common.models.util.{JsonKey, ProjectLogger, ProjectUtil}
import org.sunbird.common.util.JsonUtil

import java.util
import javax.ws.rs.core.MediaType

class ObsrvApiServiceUtil {

  def callObsrvService(druidQuery: String): String = {
    try{
      println("Druid Query" + JsonUtil.serialize(druidQuery))
      val baseUrl: String = ProjectUtil.getConfigValue(JsonKey.OBSERV_API_SERVICE_BASE_URL)
      val endPoint: String = ProjectUtil.getConfigValue(JsonKey.OBSERV_API_SERVICE_ENDPOINT)
      val request = Unirest.post(s"$baseUrl$endPoint")
        .headers(getUpdatedHeaders(new util.HashMap[String, String]())).body(druidQuery)
      val response = request.asString().getBody
      println("=====Druid Response======" + response)
      response
    } catch {
      case ex: Exception =>
        ProjectLogger.log("callObsrvService: Exception thrown = " + ex)
        throw ex
    }
  }

  private def getUpdatedHeaders(headers: util.Map[String, String]): util.Map[String, String] = {
    headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
    headers.put("Connection", "Keep-Alive")
    headers
  }

}
