package filters

import akka.stream.Materializer
import akka.util.ByteString
import org.sunbird.common.models.util.JsonKey
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
          val y = x.utf8String.replaceAll(getConfigValue(JsonKey.CLOUD_STORE_BASE_PATH_PLACEHOLDER),getConfigValue(JsonKey.CLOUD_STORE_BASE_PATH))
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
}