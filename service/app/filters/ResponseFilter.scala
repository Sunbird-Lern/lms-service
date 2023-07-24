package filters

import akka.stream.Materializer
import akka.util.ByteString
import org.apache.commons.lang.StringUtils
import org.sunbird.common.models.util.JsonKey
import org.sunbird.common.models.util.JsonKey.{CLOUD_STORE_CNAME_URL, CLOUD_STORE_BASE_PATH, CONTENT_CLOUD_STORE_CONTAINER}
import play.api.http.HttpEntity.Strict
import play.api.mvc.{Filter, RequestHeader, Result}
import org.sunbird.common.models.util.ProjectUtil.getConfigValue
import play.api.Logger.logger

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ResponseFilter  @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader) =
    nextFilter(rh) flatMap { result =>
      if (null != result.body && !result.body.isKnownEmpty){
        val contentType = result.body.contentType
        val updatedBody = result.body.consumeData.map { x =>
          val y = x.utf8String.replaceAll(getConfigValue(JsonKey.CLOUD_STORE_BASE_PATH_PLACEHOLDER), getBaseUrl + "/" + getConfigValue(CONTENT_CLOUD_STORE_CONTAINER))
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
    var baseUrl = getConfigValue(CLOUD_STORE_CNAME_URL)
    if (StringUtils.isEmpty(baseUrl)) baseUrl = getConfigValue(CLOUD_STORE_BASE_PATH)
    baseUrl
  }
}