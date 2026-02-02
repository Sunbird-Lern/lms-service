package filters

import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.apache.commons.lang.StringUtils
import org.sunbird.keys.JsonKey
import org.sunbird.keys.JsonKey.{CLOUD_STORAGE_CNAME_URL, CLOUD_STORE_BASE_PATH, CONTENT_CLOUD_STORAGE_CONTAINER}
import org.sunbird.common.ProjectUtil.getConfigValue
import play.api.Logging
import play.api.http.HttpEntity.Strict
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ResponseFilter  @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter with Logging {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader) =
    nextFilter(rh) flatMap { result =>
      if (null != result.body && !result.body.isKnownEmpty){
        val contentType = result.body.contentType
        val updatedBody = result.body.consumeData.map { x =>
          val y = x.utf8String.replaceAll(getConfigValue(JsonKey.CLOUD_STORE_BASE_PATH_PLACEHOLDER), getBaseUrl + "/" + getConfigValue(CONTENT_CLOUD_STORAGE_CONTAINER))
          logger.info("updated body: " + y)
          y
        }
        updatedBody map { x =>
          result.copy(body = Strict(ByteString(x), contentType))
        }
      } else {
        Future(result)
      }
    }

  def getBaseUrl: String = {
    var baseUrl = getConfigValue(CLOUD_STORAGE_CNAME_URL)
    if (StringUtils.isEmpty(baseUrl)) baseUrl = getConfigValue(CLOUD_STORE_BASE_PATH)
    baseUrl
  }
}