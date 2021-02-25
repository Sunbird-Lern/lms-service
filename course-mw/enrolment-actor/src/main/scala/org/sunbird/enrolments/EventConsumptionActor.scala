package org.sunbird.enrolments

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util._
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.helper.ServiceFactory
import org.sunbird.learner.util.Util

import javax.inject.Inject
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class EventConsumptionActor @Inject() extends BaseEnrolmentActor {
  private val cassandraOperation = ServiceFactory.getInstance
  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  private val dateFormatter = ProjectUtil.getDateFormatter

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "updateConsumption" => updateConsumption(request)
      case "getConsumption" => getConsumption(request)
      case _ => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  def updateConsumption(request: Request): Unit = {
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val batchId = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val courseId = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
    val existingConsumptionResult = getContentsConsumption(userId, courseId, batchId, request.getRequestContext)
    val existingCompletedTime = if (existingConsumptionResult.isEmpty) null else parseDate(existingConsumptionResult.get(0).getOrDefault(JsonKey.LAST_COMPLETED_TIME, "").asInstanceOf[String])
    var status: Integer = request.getRequest.getOrDefault(JsonKey.STATUS, Integer.valueOf(1)).asInstanceOf[Integer]
    var progress: Integer = request.getRequest.getOrDefault(JsonKey.PROGRESS, Integer.valueOf(1)).asInstanceOf[Integer]
    var completedCount: Integer = 0;
    if (status >= 2) {
      status = 2
      completedCount = 1
    }
    if (completedCount >= 1) {
      completedCount = 1
      request.getRequest.put(JsonKey.LAST_COMPLETED_TIME, compareTime(existingCompletedTime, null))
      //note progress should still denote the actual progress, so not making it 100 percent explicitly
    }
    if (progress > 100) progress = 100
    request.getRequest.put(JsonKey.STATUS, status)
    request.getRequest.put(JsonKey.PROGRESS, progress)
    request.getRequest.put(JsonKey.COMPLETED_COUNT, completedCount)
    request.getRequest.put(JsonKey.LAST_UPDATED_TIME, ProjectUtil.getFormattedDate)
    cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, request.getRequest, request.getRequestContext)
    val response = new Response()
    response.setResponseCode(ResponseCode.success)
    sender().tell(response, self)

  }

  def parseDate(dateString: String) = {
    if (StringUtils.isNotBlank(dateString) && !StringUtils.equalsIgnoreCase(JsonKey.NULL, dateString)) {
      dateFormatter.parse(dateString)
    } else null
  }

  def compareTime(existingTime: java.util.Date, inputTime: java.util.Date): String = {
    if (null == existingTime && null == inputTime) {
      ProjectUtil.getFormattedDate
    } else if (null == existingTime) dateFormatter.format(inputTime)
    else if (null == inputTime) dateFormatter.format(existingTime)
    else {
      if (inputTime.after(existingTime)) dateFormatter.format(inputTime)
      else dateFormatter.format(existingTime)
    }
  }

  def getConsumption(request: Request): Unit = {
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val batchId = request.get(JsonKey.BATCH_ID).asInstanceOf[String]
    val courseId = request.get(JsonKey.COURSE_ID).asInstanceOf[String]
    val contentsConsumed = getContentsConsumption(userId, courseId, batchId, request.getRequestContext)
    val response = new Response
    if (CollectionUtils.isNotEmpty(contentsConsumed)) {
      val filteredContents = contentsConsumed.map(m => {
        ProjectUtil.removeUnwantedFields(m, JsonKey.DATE_TIME, JsonKey.USER_ID, JsonKey.ADDED_BY, JsonKey.LAST_UPDATED_TIME)
        m
      }).asJava
      response.put(JsonKey.RESPONSE, filteredContents)
    } else {
      response.put(JsonKey.RESPONSE, new java.util.ArrayList[AnyRef]())
    }
    sender().tell(response, self)
  }

  private def getContentsConsumption(userId: String, courseId: String, batchId: String, requestContext: RequestContext): java.util.List[java.util.Map[String, AnyRef]] = {
    val filters = new java.util.HashMap[String, AnyRef]() {
      {
        put("userid", userId)
        put("courseid", courseId)
        put("batchid", batchId)
      }
    }
    val response = cassandraOperation.getRecords(requestContext, consumptionDBInfo.getKeySpace, consumptionDBInfo.getTableName, filters, null)
    response.getResult.getOrDefault(JsonKey.RESPONSE, new java.util.ArrayList[java.util.Map[String, AnyRef]]).asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
  }

}