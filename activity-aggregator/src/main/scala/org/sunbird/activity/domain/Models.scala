package org.sunbird.activity.domain

import java.util.Date
import scala.collection.JavaConverters._


case class ContentStatus(
                          contentId: String,
                          status: Int,
                          completedCount: Int = 0,
                          viewCount: Int = 1,
                          progress: Int = 0,
                          lastAccessTime: Date = null,
                          lastCompletedTime: Date = null,
                          lastUpdatedTime: Date = null,
                          fromInput: Boolean = true,
                          eventsFor: List[String] = List()
                        )

case class UserContentConsumption(userId: String, batchId: String, courseId: String, contents: Map[String, ContentStatus])

case class UserActivityAgg(
                            activity_type: String,
                            user_id: String,
                            activity_id: String,
                            context_id: String,
                            aggregates: Map[String, Double],
                            agg_last_updated: Map[String, Long]
                          )

case class CollectionProgress(
                               userId: String,
                               batchId: String,
                               courseId: String,
                               progress: Int,
                               completedOn: Date,
                               contentStatus: Map[String, Int],
                               inputContents: List[String] = List(),
                               completed: Boolean = false
                             )

case class UserEnrolmentAgg(activityAgg: UserActivityAgg, collectionProgress: Option[CollectionProgress] = None)

/**
 * Telemetry event models for audit events
 */
case class ActorObject(id: String, `type`: String = "User")

case class EventData(props: Array[String], `type`: String)

case class EventContext(
                         channel: String = "in.sunbird",
                         env: String = "Course",
                         sid: String = java.util.UUID.randomUUID().toString,
                         did: String = java.util.UUID.randomUUID().toString,
                         pdata: java.util.Map[String, String] = Map("ver" -> "3.0", "id" -> "org.sunbird.learning.platform", "pid" -> "activity-aggregator-actor").asJava,
                         cdata: Array[java.util.Map[String, String]]
                       )

case class EventObject(id: String, `type`: String, rollup: java.util.Map[String, String])

case class TelemetryEvent(
                           actor: ActorObject,
                           eid: String = "AUDIT",
                           edata: EventData,
                           ver: String = "3.0",
                           syncts: Long = System.currentTimeMillis(),
                           ets: Long = System.currentTimeMillis(),
                           context: EventContext,
                           mid: String = s"LP.AUDIT.${java.util.UUID.randomUUID().toString}",
                           `object`: EventObject,
                           tags: java.util.List[AnyRef] = new java.util.ArrayList[AnyRef]()
                         )

