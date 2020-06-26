package org.sunbird.enrolments

import javax.inject.Inject
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.models.util.{JsonKey, TelemetryEnvKey}
import org.sunbird.common.request.Request
import org.sunbird.learner.util.Util

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class ProgressActor @Inject() extends BaseEnrolmentActor {

    override def onReceive(request: Request): Unit = {
        Util.initializeContext(request, TelemetryEnvKey.BATCH)
        request.getOperation match {
            case "addContent" => addContent(request)
            case _ => onReceiveUnsupportedOperation(request.getOperation)
        }
    }
    
    def addContent(request: Request): Unit = {
        val requestBy = request.get(JsonKey.REQUESTED_BY).asInstanceOf[String]
        val requestedFor = request.get(JsonKey.REQUESTED_FOR).asInstanceOf[String]
        val validUserIds = List(requestBy, requestedFor).filter(p => StringUtils.isNotBlank(p))
        
        processAssessments(request, validUserIds)
        processContents(request, validUserIds)
    }

    def processAssessments(request: Request, validUserIds: List[String]) = {
        val assementEvents = request.getRequest.getOrDefault(JsonKey.ASSESSMENT_EVENTS, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        if(CollectionUtils.isNotEmpty(assementEvents)) {
            val batchAssessmentList: Map[String, List[java.util.Map[String, AnyRef]]] = assementEvents.filter(event => StringUtils.isNotBlank(event.getOrDefault(JsonKey.BATCH_ID, "").asInstanceOf[String])).toList.groupBy(event => event.get(JsonKey.BATCH_ID).asInstanceOf[String])
            val batchIds = batchAssessmentList.keySet.toList.asJava
            val batches:Map[String, List[java.util.Map[String, AnyRef]]] = getBatches(batchIds, null).toList.groupBy(batch => batch.get(JsonKey.IDENTIFIER).asInstanceOf[String])
            val invalidBatchIds = batchAssessmentList.keySet.diff(batches.keySet).toList.asJava
            val validBatches:Map[String, List[java.util.Map[String, AnyRef]]]  = batches.filterKeys(key => batchIds.contains(key))
            val completedBatchIds = validBatches.filter(batch => 1 != batch._2.head.get(JsonKey.STATUS).asInstanceOf[Integer]).keys.toList.asJava
            batchAssessmentList.foreach(input => {
                val batchId = input._1
                if(!invalidBatchIds.contains(batchId) && !completedBatchIds.contains(batchId)) {
                    
                }
                
            })
        }
    }

    def processContents(request: Request, validUserIds: List[String]): Unit = {
        
    }

}
