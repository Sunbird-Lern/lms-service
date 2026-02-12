package org.sunbird.activity.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.sunbird.http.HttpUtil
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

class ContentSearchUtil {

  private val mapper = new ObjectMapper()

  def getDBStatus(collectionId: String): String = {
    val requestBody = s"""{
                       |    "request": {
                       |        "filters": {
                       |            "objectType": "Collection",
                       |            "identifier": "$collectionId",
                       |            "status": ["Live", "Unlisted", "Retired"]
                       |        },
                       |        "fields": ["status"]
                       |    }
                       |}""".stripMargin

    val searchBasePath = ProjectUtil.getConfigValue("service_search_base_path")
    val searchAPIURL = searchBasePath + "/v3/search"
    val response = HttpUtil.doPostRequest(searchAPIURL, requestBody, new util.HashMap[String, String]())
    
    if (response != null && response.getStatusCode == 200) {
      val responseBody = mapper.readValue(response.getBody, classOf[util.Map[String, AnyRef]])
      val result = responseBody.getOrDefault("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.Map[String, AnyRef]]
      val count = result.getOrDefault("count", 0.asInstanceOf[AnyRef]).asInstanceOf[Number].intValue()
      if (count > 0) {
        val list = result.getOrDefault("content", new util.ArrayList[util.Map[String, AnyRef]]()).asInstanceOf[util.List[util.Map[String, AnyRef]]]
        list.asScala.head.get("status").asInstanceOf[String]
      } else throw new Exception(s"There are no published or retired collection with id: $collectionId")
    } else {
      throw new Exception("search-service not returning error:" + (if (response != null) response.getStatusCode else "null"))
    }
  }

  def getCollectionStatus(collectionId: String, requestContext: RequestContext): String = {
    getDBStatus(collectionId)
  }
}

object ContentSearchUtil {
  def apply(): ContentSearchUtil = new ContentSearchUtil()
}
