package org.sunbird.collectionhierarchy.actors

import org.sunbird.actor.base.BaseActor
import org.sunbird.collectionhierarchy.manager.CollectionCSVManager.{getCloudPath, getHierarchy, readInputCSV, updateCollection, validateCollection}
import org.sunbird.collectionhierarchy.validator.CollectionCSVValidator.{collectionNodeIdentifierHeader, validateCSVHeadersFormat, validateCSVRecordsDataAuthenticity, validateCSVRecordsDataFormat}
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.exception.ProjectCommonException.{throwClientErrorException, throwServerErrorException}
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.JsonKey._
import org.sunbird.common.models.util.ProjectUtil.getConfigValue
import org.sunbird.common.models.util._
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.common.responsecode.ResponseCode._
import org.sunbird.learner.util.Util

import java.io._
import java.time.Instant
import javax.inject.Inject
import scala.collection.JavaConversions.{mapAsJavaMap, _}
import scala.collection.JavaConverters.{mapAsJavaMapConverter, mapAsScalaMapConverter}
import scala.collection.immutable.{HashMap, Map}

class CollectionTOCActor @Inject() extends BaseActor
{

  override def onReceive(request: Request): Unit = {
    Util.initializeContext(request, TelemetryEnvKey.COLLECTION_CSV_HIERARCHY, this.getClass.getName)

    request.getOperation match {
      case JsonKey.COLLECTION_CSV_TOC_UPLOAD => uploadTOC(request)
      case JsonKey.COLLECTION_CSV_TOC_DOWNLOAD => getTOCUrl(request)
      case _ => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  private def uploadTOC(request:Request) {
    val startTime = Instant.now
    try {
      val csvFileParser = readInputCSV(request)
      try {
        val csvHeaders: Map[String, Integer] =  if (!csvFileParser.getHeaderMap.isEmpty) csvFileParser.getHeaderMap.asScala.toMap else HashMap.empty
        // Reading input CSV File - END

        //Check if CSV Headers are empty
        if (null == csvHeaders || csvHeaders.isEmpty) throwClientErrorException(ResponseCode.blankCsvData)

        //Check if the input CSV is 'CREATE' TOC file format or 'UPDATE' TOC file format
        val mode = if (csvHeaders.containsKey(collectionNodeIdentifierHeader.head)) JsonKey.UPDATE else JsonKey.CREATE
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> mode identified: " + mode)
        val collectionId = request.get(COLLECTION_ID).asInstanceOf[String]
        val collectionHierarchy = getHierarchy(collectionId, request)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after fetching collection Hierarchy: " + collectionHierarchy(JsonKey.IDENTIFIER))

        // Validate if the mode is CREATE and children already exist in collection
        val children = collectionHierarchy(CHILDREN).asInstanceOf[List[AnyRef]]
        if(mode.equals(JsonKey.CREATE) && children.nonEmpty)
          throwClientErrorException(collectionChildrenExist, collectionChildrenExist.getErrorMessage)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after Validating if the mode is CREATE and children already exist in collection")

        //Validate the headers format of the input CSV
        validateCSVHeadersFormat(csvHeaders, mode)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after validating CSV Headers format: "
          + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        //Validate the data format of the input CSV records
        val csvRecords = csvFileParser.getRecords
        validateCSVRecordsDataFormat(csvRecords, mode)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after validating CSV Records data format: "
          + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        val linkedContentsDetails: List[Map[String, AnyRef]] = {
          if(mode.equals(JsonKey.UPDATE)) {
          // validate the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents
          validateCSVRecordsDataAuthenticity(csvRecords, collectionHierarchy, request)
          }
          else List.empty[Map[String, AnyRef]]
        }
        logger.info(request.getRequestContext,
          "CollectionTOCActor --> uploadTOC --> after validating the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents: "
            + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        // update the collection hierarchy
        val updateHierarchyResponse: Response = updateCollection(collectionHierarchy, csvRecords, mode, linkedContentsDetails, request)
        val identifierData = updateHierarchyResponse.getResult.getOrElse("identifiers", Map[String,String]()).asInstanceOf[Map[String,String]].asJava
        updateHierarchyResponse.getResult.put("identifiers", identifierData)
        sender.tell(updateHierarchyResponse, self)
        logger.info(request.getRequestContext,"CollectionTOCActor -> uploadTOC -> END::::::::"+ (Instant.now.toEpochMilli - startTime.toEpochMilli))

      } catch {
        case e: IllegalArgumentException =>
          ProjectCommonException.throwClientErrorException(ResponseCode.customClientError, e.getMessage)
        case e: ProjectCommonException =>
          throw e
        case e: Exception =>
          logger.info(request.getRequestContext, "Exception" + e.getMessage)
          throwServerErrorException(ResponseCode.errorProcessingFile)
      } finally {
        try if (null != csvFileParser) csvFileParser.close()
        catch {
          case e: IOException =>
            logger.error(request.getRequestContext,"CollectionTocActor:readAndValidateCSV : Exception occurred while closing stream", e)
        }

      }
    }
    catch {
      case e: Exception =>
        ProjectCommonException.throwClientErrorException(ResponseCode.customClientError, e.getMessage)
    }

  }

  private def getTOCUrl(request: Request) {
    val startTime = Instant.now

    val collectionId = request.get(COLLECTION_ID).asInstanceOf[String]
    if (collectionId.isBlank) {
      logger.error(request.getRequestContext, "Invalid Collection Id Provided", null)
      throwClientErrorException(invalidCollection, invalidCollection.getErrorMessage)
    }

    logger.debug(request.getRequestContext, "Reading Content for Collection | Id: " + collectionId)
    val collectionHierarchy = getHierarchy(collectionId, request)
    logger.info(request.getRequestContext, "Timed:CollectionTocActor:getTocUrl duration for get collection: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))

    validateCollection(collectionHierarchy)

    val cloudPath = getCloudPath(request, collectionHierarchy)

    logger.info(request.getRequestContext, "Sending Response for Toc Download API for Collection | Id: " + collectionId)
    val collectionCSV = HashMap[String, AnyRef] (TOC_URL-> cloudPath, TTL -> getConfigValue(COLLECTION_TOC_CSV_TTL))

    val response = new Response
    response.put(COLLECTION, collectionCSV.asJava)
    sender.tell(response, self)
  }

}