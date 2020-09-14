package org.sunbird.aggregate

import akka.actor.ActorRef
import javax.inject.{Inject, Named}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.models.util.TelemetryEnvKey
import org.sunbird.common.request.Request
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.learner.util.Util

class CollectionSummaryAggregate @Inject()(@Named("collection-summary-aggregate-actor") courseBatchNotificationActorRef: ActorRef
                                          )(implicit val cacheUtil: RedisCacheUtil) extends BaseEnrolmentActor {
  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.BATCH)
    println("RequestObj" + request)
  }
}
