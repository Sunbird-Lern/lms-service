package org.sunbird.collectionhierarchy.validator

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.csv.CSVRecord

import org.sunbird.collectionhierarchy.util.CollectionTOCUtil.{getRelatedFrameworkById, searchLinkedContents, validateDialCodes}
import org.sunbird.common.exception.ProjectCommonException.throwClientErrorException
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode

import java.text.MessageFormat
import java.util
import scala.collection.JavaConversions.{mapAsJavaMap, _}
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.immutable.{ListMap, Map}

object CollectionCSVValidator {

  val logger: LoggerUtil = new LoggerUtil(CollectionCSVValidator.getClass)

  @transient val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  val allowedContentTypes: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_ALLOWED_CONTENT_TYPES), new TypeReference[List[String]]() {})
  val allowedNumberOfRecord: Integer = Integer.valueOf(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_MAX_CSV_ROWS))
  val createCSVHeaders: Map[String, Integer] =  mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CREATION_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
  val updateCSVHeaders: Map[String, Integer] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_UPDATE_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
  val createCSVMandatoryHeaderCols: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_CREATE_CSV_MANDATORY_FIELDS), new TypeReference[List[String]]() {})
  val updateCSVMandatoryHeaderCols: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_UPDATE_CSV_MANDATORY_FIELDS), new TypeReference[List[String]]() {})
  val qrCodeHdrColsList: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_QR_COLUMNS), new TypeReference[List[String]]() {})
  val folderHierarchyHdrColumnsList: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.FOLDER_HIERARCHY_COLUMNS), new TypeReference[List[String]]() {})
  val linkedContentHdrColumnsList: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_FIELDS), new TypeReference[List[String]]() {})
  val linkedContentColumnHeadersSeq: Map[String, Integer] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_SEQ), new TypeReference[Map[String, Integer]]() {})
  val collectionNameHeader: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.CSV_COLLECTION_NAME_HEADER), new TypeReference[List[String]]() {})
  val mappedTopicsHeader: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.MAPPED_TOPICS_HEADER), new TypeReference[List[String]]() {})
  val collectionNodeIdentifierHeader: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_IDENTIFIER_HEADER), new TypeReference[List[String]]() {})
  val contentTypeToUnitTypeMapping: Map[String, String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TYPE_TO_UNIT_TYPE), new TypeReference[Map[String, String]]() {})
  val collectionOutputTocHeaders: List[String] = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_OUTPUT_TOC_HEADERS), new TypeReference[List[String]]() {})
  val maxFolderLevels: Int = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.FOLDER_HIERARCHY_COLUMNS), new TypeReference[List[String]]() {}).size

  def validateCSVHeadersFormat(csvHeader: Map[String, Integer], mode:String) {

    val configHeaders: Map[String, Integer]  =  if(mode.equals(JsonKey.CREATE)) createCSVHeaders else updateCSVHeaders

    if(!csvHeader.equals(configHeaders))
    {
      //Check if Column Order is different
      if((csvHeader.keySet -- configHeaders.keySet).isEmpty)
      {
        val colSeqString = ListMap(configHeaders.toSeq.sortBy(_._2):_*).keySet mkString ","
        val errorMessage = MessageFormat.format(ResponseCode.invalidHeaderOrder.getErrorMessage+colSeqString)
        throwClientErrorException(ResponseCode.invalidHeaderOrder, errorMessage)
      }

      //Check if Some columns are missing and any additional columns found
      if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty &&
        (((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty) ||
          (csvHeader.keySet -- configHeaders.keySet).toList.head.nonEmpty))
      {
        val additionalCols = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
        val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format(ResponseCode.requiredHeaderMissing.getErrorMessage+missingCols+" AND "
          +ResponseCode.additionalHeaderFound.getErrorMessage+additionalCols)
        throwClientErrorException(ResponseCode.invalidHeadersFound, errorMessage)
      }
      //Check if Some columns are missing
      else if((configHeaders.toSet diff csvHeader.toSet).toMap.keySet.nonEmpty && (configHeaders.toSet diff csvHeader.toSet).toMap.keySet.toList.head.nonEmpty)
      {
        val missingCols = (configHeaders.toSet diff csvHeader.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format(ResponseCode.requiredHeaderMissing.getErrorMessage+missingCols)
        throwClientErrorException(ResponseCode.requiredHeaderMissing, errorMessage)
      }
      //Check if any additional columns found
      else if((csvHeader.toSet diff configHeaders.toSet).toMap.keySet.nonEmpty && (csvHeader.toSet diff configHeaders.toSet).toMap.keySet.toList.head.nonEmpty)
      {
        val additionalCols:String = (csvHeader.toSet diff configHeaders.toSet).toMap.keySet mkString ","
        val errorMessage = MessageFormat.format(ResponseCode.additionalHeaderFound.getErrorMessage+additionalCols)
        throwClientErrorException(ResponseCode.additionalHeaderFound, errorMessage)
      }
    }

  }

  def validateCSVRecordsDataFormat(csvRecords: util.List[CSVRecord], mode: String) {
    //Check if CSV Records are empty
    if (null == csvRecords || csvRecords.isEmpty)
      throwClientErrorException(ResponseCode.blankCsvData)

    // check if records are more than allowed csv rows
    if (csvRecords.nonEmpty && csvRecords.size > allowedNumberOfRecord)
      throwClientErrorException(ResponseCode.csvRowsExceeds, ResponseCode.csvRowsExceeds.getErrorMessage + allowedNumberOfRecord)

    // check if record length is greater than max length - START
    val recordLengthErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(colData._1.isEmpty && colData._2.nonEmpty)
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if(recordLengthErrorMessage.nonEmpty && recordLengthErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvRecordDataExceedsMaxLength, ResponseCode.csvRecordDataExceedsMaxLength.getErrorMessage + recordLengthErrorMessage)
    // check if record length is greater than max length - END

    // Check if data exists in mandatory columns - START
    val mandatoryDataHdrCols =  if(mode.equals(JsonKey.CREATE)) createCSVMandatoryHeaderCols else updateCSVMandatoryHeaderCols

    val mandatoryMissingDataList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(mandatoryDataHdrCols.contains(colData._1) && colData._2.isEmpty)
          MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in mandatory columns - END

    // Check if data exists in hierarchy folder columns - START
    val hierarchyHeaders: Map[String, Integer]  = if(mode.equals(JsonKey.CREATE)) createCSVHeaders else updateCSVHeaders

    val missingDataList = csvRecords.flatMap(csvRecord => {
      val csvRecordFolderHierarchyData = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.isEmpty &&
          (csvRecordFolderHierarchyData.nonEmpty && hierarchyHeaders(colData._1) < hierarchyHeaders(csvRecordFolderHierarchyData.max._1)))
          MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in hierarchy folder columns - END

    // Add column data validation messages from mandatory columns and hierarchy folder - START
    val missingDataErrorMessage = {
      if (mandatoryMissingDataList.trim.nonEmpty && missingDataList.trim.nonEmpty)
        mandatoryMissingDataList.trim + "," + missingDataList.trim
      else if (mandatoryMissingDataList.trim.nonEmpty) mandatoryMissingDataList.trim
      else if (missingDataList.trim.nonEmpty) missingDataList.trim
      else ""
    }

    if(missingDataErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.requiredFieldMissing, ResponseCode.requiredFieldMissing.getErrorMessage
        + missingDataErrorMessage.split(",").distinct.mkString(JsonKey.COMMA_SEPARATOR))
    // Add column data validation messages from mandatory columns and hierarchy folder - END

    // Verify if there are any duplicate hierarchy folder structure - START
    val dupRecordsList = csvRecords.filter(csvRecord => {
      csvRecords.exists(record => {
        val csvRecordFolderHierarchy = csvRecord.toMap.asScala.toMap.map(colData => {
          if(folderHierarchyHdrColumnsList.contains(colData._1))
            colData
        })
        val recordFolderHierarchy = record.toMap.asScala.toMap.map(colData => {
          if(folderHierarchyHdrColumnsList.contains(colData._1))
            colData
        })
        recordFolderHierarchy.equals(csvRecordFolderHierarchy) && !csvRecord.getRecordNumber.equals(record.getRecordNumber)
      })
    }).map(dupRecord => {
      MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (dupRecord.getRecordNumber+1).toString)
    }).mkString(JsonKey.COMMA_SEPARATOR)

    if(dupRecordsList.trim.nonEmpty)
      throwClientErrorException(ResponseCode.duplicateRows, ResponseCode.duplicateRows.getErrorMessage + dupRecordsList)
    // Verify if there are any duplicate hierarchy folder structure - END


    if(mode.equals(JsonKey.UPDATE)) {
      // QRCode data format validations - START
      // Verify if there are any QR Codes data entry issues - START
      val qrDataErrorMessage = csvRecords.map(csvRecord => {
        val csvRecordMap = csvRecord.toMap.asScala.toMap
        if(csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(JsonKey.YES) && csvRecordMap(qrCodeHdrColsList(1)).isEmpty)
          MessageFormat.format(ResponseCode.qrReqdYesQRCodeBlank.getErrorMessage, (csvRecord.getRecordNumber+1).toString)
        else if((csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(JsonKey.NO) || csvRecordMap(qrCodeHdrColsList.head).isEmpty) &&
          csvRecordMap(qrCodeHdrColsList(1)).nonEmpty)
          MessageFormat.format(ResponseCode.qrReqdNoQRCodeFilled.getErrorMessage, (csvRecord.getRecordNumber+1).toString)
        else
          ""
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if(qrDataErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.errorQRCodeEntry, ResponseCode.errorQRCodeEntry.getErrorMessage + qrDataErrorMessage)
      // Verify if there are any QR Codes data entry issues - END

      // Verify if there are any duplicate QR Codes - START
      val dupQRListMsg = csvRecords.filter(csvRecord => {
        csvRecords.exists(record => {
          record.get(JsonKey.QR_CODE).nonEmpty && csvRecord.get(JsonKey.QR_CODE).nonEmpty && record.get(JsonKey.QR_CODE).equals(csvRecord.get(JsonKey.QR_CODE)) &&
            !csvRecord.getRecordNumber.equals(record.getRecordNumber)
        })
      }).map(dupQRRecord => {
        MessageFormat.format(ResponseCode.duplicateQRRowNum.getErrorMessage, (dupQRRecord.getRecordNumber+1).toString, dupQRRecord.get(JsonKey.QR_CODE))
      }).mkString(JsonKey.COMMA_SEPARATOR)

      if(dupQRListMsg.trim.nonEmpty)
        throwClientErrorException(ResponseCode.duplicateQRCodeEntry, ResponseCode.duplicateQRCodeEntry.getErrorMessage + dupQRListMsg)
      // Verify if there are any duplicate QR Codes - END
      // QRCode data format validations - END

      // Check if data exists in Linked content columns - START
      val missingLinkedContentDataList = csvRecords.flatMap(csvRecord => {
        val csvRecordLinkedContentsData = csvRecord.toMap.asScala.toMap.filter(colData => {
          linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
        })

        csvRecord.toMap.asScala.toMap.map(colData => {
          if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.isEmpty &&
            (csvRecordLinkedContentsData.nonEmpty && linkedContentColumnHeadersSeq(colData._1) < linkedContentColumnHeadersSeq(csvRecordLinkedContentsData.max._1)))
            MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if(missingLinkedContentDataList.trim.nonEmpty)
        throwClientErrorException(ResponseCode.linkedContentsMissing, ResponseCode.linkedContentsMissing.getErrorMessage + missingLinkedContentDataList)
      // Check if data exists in hierarchy folder columns - END
    }

  }

  def validateCSVRecordsDataAuthenticity(csvRecords: util.List[CSVRecord], collectionHierarchy: Map[String, AnyRef], request: Request): List[Map[String, AnyRef]] = {
    // validate collection name column in CSV - START
    val invalidCollectionNameErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNameHeader.contains(colData._1) && (colData._2.isEmpty || !colData._2.equalsIgnoreCase(collectionHierarchy(JsonKey.NAME).toString)))
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvInvalidCollectionName, ResponseCode.csvInvalidCollectionName.getErrorMessage + invalidCollectionNameErrorMessage)
    // validate collection name column in CSV - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating collection name column in CSV")

    // validate Folder Identifier column in CSV - START
    val collectionChildNodes = collectionHierarchy(JsonKey.CHILD_NODES).asInstanceOf[List[String]]

    val invalidCollectionNodeIDErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && (colData._2.isEmpty || !collectionChildNodes.contains(colData._2)))
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvInvalidCollectionNodeIdentifier, ResponseCode.csvInvalidCollectionNodeIdentifier.getErrorMessage
        + invalidCollectionNodeIDErrorMessage)
    // validate Folder Identifier column in CSV - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Folder Identifier column in CSV")

    // Validate QR Codes with reserved DIAL codes - START
    val csvQRCodesList: List[String] = csvRecords.map(csvRecord => {
      csvRecord.toMap.asScala.toMap.get(qrCodeHdrColsList(1)).get.trim
    }).filter(msg => msg.nonEmpty).toList

    if(csvQRCodesList.nonEmpty) {
      val returnDIALCodes = validateDialCodes(collectionHierarchy(JsonKey.CHANNEL).toString, csvQRCodesList, request)

      val invalidQRCodeErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (qrCodeHdrColsList.contains(colData._1) && (csvQRCodesList diff returnDIALCodes).contains(colData._2))
            MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidQRCodeErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidDIALCodes, ResponseCode.csvInvalidDIALCodes.getErrorMessage + invalidQRCodeErrorMessage)
    }
    // Validate QR Codes with reserved DIAL codes - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating QR Codes with reserved DIAL codes")

    // Validate Mapped Topics with Collection Framework data - START
    val mappedTopicsList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) colData._2.trim.split(",").mkString(",") else ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if(mappedTopicsList.nonEmpty) {
      val frameworkId = collectionHierarchy(JsonKey.FRAMEWORK).toString
      val frameworkGetResponse = getRelatedFrameworkById(frameworkId, request)
      val frameworkGetResult = frameworkGetResponse.getResult.getOrDefault(JsonKey.FRAMEWORK, new util.HashMap[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
      val frameworkCategories = frameworkGetResult.getOrDefault(JsonKey.CATEGORIES, List.empty).asInstanceOf[List[Map[String, AnyRef]]]

      val frameworkTopicList = frameworkCategories.flatMap(categoryData => {
        categoryData.map(colData => {
          if (categoryData(JsonKey.CODE).equals(JsonKey.TOPIC) && colData._1.equalsIgnoreCase(JsonKey.TERMS))
            colData._2.asInstanceOf[List[Map[String, AnyRef]]].map(_.getOrElse(JsonKey.NAME, "")).asInstanceOf[List[String]]
          else  List.empty
        })
      }).filter(topic => topic.nonEmpty).flatten

      val invalidTopicsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) {
            val topicsDataList: List[String] = colData._2.split(",").toList
            topicsDataList.map(topic => {
              if(!frameworkTopicList.contains(topic.trim))
                MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + topic)
              else ""
            }).filter(errmsg => errmsg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)
          } else ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidTopicsErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidMappedTopics, ResponseCode.csvInvalidMappedTopics.getErrorMessage + invalidTopicsErrorMessage)
    }
    // Validate Mapped Topics with Collection Framework data - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Mapped Topics with Collection Framework data")

    // Validate Linked Contents authenticity - START

    val csvLinkedContentsList: List[String] = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty) colData._2.trim  else ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if (csvLinkedContentsList.nonEmpty) {
      val returnedLinkedContentsResult: List[Map[String, AnyRef]] = searchLinkedContents(csvLinkedContentsList, request)
      val returnedLinkedContentsIdentifierList = returnedLinkedContentsResult.map(_.getOrElse(JsonKey.IDENTIFIER, "")).asInstanceOf[List[String]]

      val invalidLinkedContentsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (linkedContentHdrColumnsList.contains(colData._1) && (csvLinkedContentsList diff returnedLinkedContentsIdentifierList).contains(colData._2))
            MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidLinkedContentsErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidLinkedContents, ResponseCode.csvInvalidLinkedContents.getErrorMessage + invalidLinkedContentsErrorMessage)

      val returnedLinkedContentsContentTypeList = returnedLinkedContentsResult.map(_.getOrElse(JsonKey.CONTENT_TYPE, "")).asInstanceOf[List[String]]

      if(returnedLinkedContentsContentTypeList.exists(contentType => !allowedContentTypes.contains(contentType)))
      {
        val invalidContentTypeLinkedContentsList = returnedLinkedContentsResult.map(content => {
          if(!allowedContentTypes.contains(content(JsonKey.CONTENT_TYPE).toString)) content(JsonKey.IDENTIFIER).toString  else ""
        }).mkString(JsonKey.COMMA_SEPARATOR)

        if(invalidContentTypeLinkedContentsList.trim.nonEmpty)
          throwClientErrorException(ResponseCode.csvInvalidLinkedContentsContentTypes, ResponseCode.csvInvalidLinkedContentsContentTypes.getErrorMessage
            + invalidContentTypeLinkedContentsList)
      }
      logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Linked Contents")
      returnedLinkedContentsResult
    }
    else List.empty[Map[String, AnyRef]]
    // Validate Linked Contents authenticity - END

  }

}
