package filters

import java.util.UUID

import org.apache.commons.lang3.StringUtils
import org.sunbird.common.models.util.JsonKey
import org.sunbird.common.request.ExecutionContext
import org.sunbird.telemetry.util.PerformanceLogger
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialAction, EssentialFilter, RequestHeader, Result}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * @author Mahesh Kumar Gangula
  */


class LoggingFilter extends EssentialFilter {
  override def apply(next: EssentialAction): EssentialAction = new EssentialAction {

    override def apply(request: RequestHeader): Iteratee[Array[Byte], Result] = {
      val startTime = System.currentTimeMillis();
      val requestId = request.headers.get(JsonKey.MESSAGE_ID).getOrElse(UUID.randomUUID().toString);
      val scenarioId = request.headers.get(JsonKey.SCENARIO_ID).getOrElse("NOSCENARIO");
      val newRequest = request.copy(headers = request.headers.add((JsonKey.MESSAGE_ID, requestId), (JsonKey.SCENARIO_ID, scenarioId)));
      next(newRequest).map {
        result =>
          val scenario = request.headers.get(JsonKey.SCENARIO_ID).getOrElse("NA");
          val duration = (System.currentTimeMillis() - startTime);
          PerformanceLogger.log(duration, request.path, LoggingFilter.this.getClass.getCanonicalName, requestId, result.header.status.toString, scenario);
          result;
      }.recover {
        case e: Throwable =>
          val scenario = request.headers.get(JsonKey.SCENARIO_ID).getOrElse("NA");
          val duration = (System.currentTimeMillis() - startTime);
          PerformanceLogger.log(duration, request.path, LoggingFilter.this.getClass.getCanonicalName, requestId, "500", scenario);
          throw e;
      }
    }
  }
}
