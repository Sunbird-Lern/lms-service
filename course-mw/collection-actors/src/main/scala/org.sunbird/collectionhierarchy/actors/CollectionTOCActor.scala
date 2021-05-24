package org.sunbird.collectionhierarchy.actors

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.csv.{CSVFormat, CSVPrinter, CSVRecord, QuoteMode}
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.FileUtils.{deleteQuietly, touch}
import org.apache.commons.io.input.BOMInputStream
import org.sunbird.actor.base.BaseActor
import org.sunbird.collectionhierarchy.util.CollectionTOCUtil._
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.exception.ProjectCommonException.{throwClientErrorException, throwServerErrorException}
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.JsonKey._
import org.sunbird.common.models.util.ProjectLogger.log
import org.sunbird.common.models.util.ProjectUtil.getConfigValue
import org.sunbird.common.models.util.Slug.makeSlug
import org.sunbird.common.models.util._
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.common.responsecode.ResponseCode._
import org.sunbird.content.util.ContentCloudStore.{getUri, upload}
import org.sunbird.learner.util.Util

import java.io._
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.time.Instant
import java.util
import javax.inject.Inject
import scala.collection.JavaConversions.{mapAsJavaMap, _}
import scala.collection.JavaConverters.{asJavaIterableConverter, mapAsJavaMapConverter, mapAsScalaMapConverter}
import scala.collection.immutable.{HashMap, ListMap, Map}
import scala.collection.mutable.ListBuffer

class CollectionTOCActor @Inject() extends BaseActor
{

  @transient val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

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

    val byteArray = request.getRequest.get(JsonKey.DATA).asInstanceOf[Array[Byte]]
    logger.info(request.getRequestContext,"Sized:CollectionTocActor:upload size of request " + byteArray.length)
    val inputStream = new ByteArrayInputStream(byteArray)

    // Reading input CSV File - START
    val csvFileFormat = CSVFormat.DEFAULT.withHeader()
    val bomInputStream = new BOMInputStream(inputStream, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE)
    val character = {
      if (bomInputStream.hasBOM) {
        bomInputStream.getBOMCharsetName
      }
      else
        StandardCharsets.UTF_8.name
    }

    try {
      val reader = new InputStreamReader(bomInputStream, character)
      val csvFileParser = csvFileFormat.parse(reader)
      try {
        val csvHeaders: Map[String, Integer] = {
          if (!csvFileParser.getHeaderMap.isEmpty) {
            csvFileParser.getHeaderMap.asScala.toMap
          }
          else {
            HashMap.empty
          }
        }
        // Reading input CSV File - END

        //Check if CSV Headers are empty
        if (null == csvHeaders || csvHeaders.isEmpty) {
          throwClientErrorException(ResponseCode.blankCsvData)
        }

        //Check if the input CSV is 'CREATE' TOC file format or 'UPDATE' TOC file format
        val csvIdentifierHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_IDENTIFIER_HEADER), new TypeReference[List[String]]() {})
        val mode = if (csvHeaders.containsKey(csvIdentifierHeader.head)) JsonKey.UPDATE else JsonKey.CREATE
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> mode identified: " + mode)
        val collectionId = request.get(COLLECTION_ID).asInstanceOf[String]
        val collectionHierarchy = getHierarchy(collectionId, request)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after fetching collection Hierarchy: " + collectionHierarchy(JsonKey.IDENTIFIER))
        // Validate if the mode is CREATE and children already exist in collection
        val children = collectionHierarchy(CHILDREN).asInstanceOf[List[AnyRef]]
        if(mode.equals(JsonKey.CREATE) && children.nonEmpty)
        {
          throwClientErrorException(collectionChildrenExist, collectionChildrenExist.getErrorMessage)
        }
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after Validating if the mode is CREATE and children already exist in collection")
        //Validate the headers format of the input CSV
        validateCSVHeadersFormat(csvHeaders, mode)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after validating CSV Headers format: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        //Validate the data format of the input CSV records
        val csvRecords = csvFileParser.getRecords
        validateCSVRecordsDataFormat(csvRecords, mode)
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after validating CSV Records data format: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        // Reading collectionId metadata for further csv records data authenticity validation
        val collectionReadResponse = readContent(collectionId, JsonKey.SUNBIRD_CONTENT_READ_API, request)
        val collectionReadResult = collectionReadResponse.getResult.get(JsonKey.CONTENT).asInstanceOf[Map[String, AnyRef]]
        logger.info(request.getRequestContext,"CollectionTOCActor --> uploadTOC --> after content read: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))

        if(mode.equals(JsonKey.UPDATE))
        {
          // validate the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents
          validateCSVRecordsDataAuthenticity(csvRecords, collectionReadResult, request)
          logger.info(request.getRequestContext,
            "CollectionTOCActor --> uploadTOC --> after validating the data authenticity of the input CSV records' - Mapped Topics, QR Codes, Linked Contents: "
              + (Instant.now.toEpochMilli - startTime.toEpochMilli))
        }

        // update the collection hierarchy
        updateCollection(collectionHierarchy, csvRecords, mode, request)

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
        if (reader != null) reader.close()
      }
    }
    catch {
      case e: IllegalArgumentException =>
        ProjectCommonException.throwClientErrorException(ResponseCode.customClientError, e.getMessage)
    }

  }

  private def validateCSVHeadersFormat(csvHeader: Map[String, Integer], mode:String) {

    val configHeaders: Map[String, Integer]  = {
      if(mode.equals(JsonKey.CREATE))
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CREATION_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
      }
      else
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_UPDATE_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
      }
    }

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

  private def validateCSVRecordsDataFormat(csvRecords: util.List[CSVRecord], mode: String) {
    //Check if CSV Records are empty
    if (null == csvRecords || csvRecords.isEmpty) {
      throwClientErrorException(ResponseCode.blankCsvData)
    }

    // check if records are more than allowed csv rows
    val allowedNumberOfRecord = Integer.valueOf(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_MAX_CSV_ROWS))
    if (csvRecords.nonEmpty && csvRecords.size > allowedNumberOfRecord)
      throwClientErrorException(ResponseCode.csvRowsExceeds, ResponseCode.csvRowsExceeds.getErrorMessage + allowedNumberOfRecord)

    // check if record length is greater than max length - START
    val recordLengthErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(colData._1.isEmpty && colData._2.nonEmpty)
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString)
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if(recordLengthErrorMessage.nonEmpty && recordLengthErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvRecordDataExceedsMaxLength, ResponseCode.csvRecordDataExceedsMaxLength.getErrorMessage + recordLengthErrorMessage)
    // check if record length is greater than max length - END

    // Check if data exists in mandatory columns - START
    val mandatoryDataHdrCols = {
      if(mode.equals(JsonKey.CREATE))
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_CREATE_CSV_MANDATORY_FIELDS), new TypeReference[List[String]]() {})
      }
      else
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_UPDATE_CSV_MANDATORY_FIELDS), new TypeReference[List[String]]() {})
      }
    }

    val mandatoryMissingDataList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(mandatoryDataHdrCols.contains(colData._1) && colData._2.isEmpty)
          MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in mandatory columns - END

    // Check if data exists in hierarchy folder columns - START
    val folderHierarchyHdrColumnsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.FOLDER_HIERARCHY_COLUMNS), new TypeReference[List[String]]() {})

    val hierarchyHeaders: Map[String, Integer]  = {
      if(mode.equals(JsonKey.CREATE))
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CREATION_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
      }
      else
      {
        mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_UPDATE_CSV_TOC_HEADERS), new TypeReference[Map[String, Integer]]() {})
      }
    }

    val missingDataList = csvRecords.flatMap(csvRecord => {
      val csvRecordFolderHierarchyData = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })
      csvRecord.toMap.asScala.toMap.map(colData => {
        if(folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.isEmpty && (csvRecordFolderHierarchyData.nonEmpty && hierarchyHeaders(colData._1) < hierarchyHeaders(csvRecordFolderHierarchyData.max._1)))
          MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).mkString(",")
    // Check if data exists in hierarchy folder columns - END

    // Add column data validation messages from mandatory columns and hierarchy folder - START
    val missingDataErrorMessage = {
      if (mandatoryMissingDataList.trim.nonEmpty && missingDataList.trim.nonEmpty)
        mandatoryMissingDataList.trim + "," + missingDataList.trim
      else if (mandatoryMissingDataList.trim.nonEmpty)
        mandatoryMissingDataList.trim
      else if (missingDataList.trim.nonEmpty)
        missingDataList.trim
      else
        ""
    }

    if(missingDataErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.requiredFieldMissing, ResponseCode.requiredFieldMissing.getErrorMessage + missingDataErrorMessage.split(",").distinct.mkString(JsonKey.COMMA_SEPARATOR))
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
      val qrCodeHdrColsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_QR_COLUMNS), new TypeReference[List[String]]() {})

      // Verify if there are any QR Codes data entry issues - START
      val qrDataErrorMessage = csvRecords.map(csvRecord => {
        val csvRecordMap = csvRecord.toMap.asScala.toMap
        if(csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(JsonKey.YES) && csvRecordMap(qrCodeHdrColsList(1)).isEmpty)
          MessageFormat.format(ResponseCode.qrReqdYesQRCodeBlank.getErrorMessage, (csvRecord.getRecordNumber+1).toString)
        else if((csvRecordMap(qrCodeHdrColsList.head).equalsIgnoreCase(JsonKey.NO) || csvRecordMap(qrCodeHdrColsList.head).isEmpty) && csvRecordMap(qrCodeHdrColsList(1)).nonEmpty)
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
          record.get(JsonKey.QR_CODE).nonEmpty && csvRecord.get(JsonKey.QR_CODE).nonEmpty && record.get(JsonKey.QR_CODE).equals(csvRecord.get(JsonKey.QR_CODE)) && !csvRecord.getRecordNumber.equals(record.getRecordNumber)
        })
      }).map(dupQRRecord => {
        MessageFormat.format(ResponseCode.duplicateQRRowNum.getErrorMessage, (dupQRRecord.getRecordNumber+1).toString, dupQRRecord.get(JsonKey.QR_CODE))
      }).mkString(JsonKey.COMMA_SEPARATOR)

      if(dupQRListMsg.trim.nonEmpty)
        throwClientErrorException(ResponseCode.duplicateQRCodeEntry, ResponseCode.duplicateQRCodeEntry.getErrorMessage + dupQRListMsg)
      // Verify if there are any duplicate QR Codes - END
      // QRCode data format validations - END

      // Check if data exists in Linked content columns - START
      val linkedContentHdrColumnsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_FIELDS), new TypeReference[List[String]]() {})
      val linkedContentColumnHeaders  = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_SEQ), new TypeReference[Map[String, Integer]]() {})

      val missingLinkedContentDataList = csvRecords.flatMap(csvRecord => {
        val csvRecordLinkedContentsData = csvRecord.toMap.asScala.toMap.filter(colData => {
          linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
        })

        csvRecord.toMap.asScala.toMap.map(colData => {
          if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.isEmpty && (csvRecordLinkedContentsData.nonEmpty && linkedContentColumnHeaders(colData._1) < linkedContentColumnHeaders(csvRecordLinkedContentsData.max._1)))
            MessageFormat.format(ResponseCode.rowMissingDataColumn.getErrorMessage, (csvRecord.getRecordNumber+1).toString,colData._1)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if(missingLinkedContentDataList.trim.nonEmpty)
        throwClientErrorException(ResponseCode.linkedContentsMissing, ResponseCode.linkedContentsMissing.getErrorMessage + missingLinkedContentDataList)
      // Check if data exists in hierarchy folder columns - END
    }

  }

  private def validateCSVRecordsDataAuthenticity(csvRecords: util.List[CSVRecord], collectionReadResult: Map[String, AnyRef], request: Request) {
    // validate collection name column in CSV - START
    val collectionNameHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.CSV_COLLECTION_NAME_HEADER), new TypeReference[List[String]]() {})

    val invalidCollectionNameErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNameHeader.contains(colData._1) && (colData._2.isEmpty || !colData._2.equalsIgnoreCase(collectionReadResult(JsonKey.NAME).toString)))
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvInvalidCollectionName, ResponseCode.csvInvalidCollectionName.getErrorMessage + invalidCollectionNameErrorMessage)
    // validate collection name column in CSV - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating collection name column in CSV")

    // validate Folder Identifier column in CSV - START
    val collectionNodeIdentifierHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_IDENTIFIER_HEADER), new TypeReference[List[String]]() {})
    val collectionChildNodes = collectionReadResult(JsonKey.CHILD_NODES).asInstanceOf[List[String]]

    val invalidCollectionNodeIDErrorMessage = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (collectionNodeIdentifierHeader.contains(colData._1) && (colData._2.isEmpty || !collectionChildNodes.contains(colData._2)))
          MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

    if (invalidCollectionNameErrorMessage.trim.nonEmpty)
      throwClientErrorException(ResponseCode.csvInvalidCollectionNodeIdentifier, ResponseCode.csvInvalidCollectionNodeIdentifier.getErrorMessage + invalidCollectionNodeIDErrorMessage)
    // validate Folder Identifier column in CSV - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Folder Identifier column in CSV")

    // Validate QR Codes with reserved DIAL codes - START
    val qrCodeHdrColsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_QR_COLUMNS), new TypeReference[List[String]]() {})

    val csvQRCodesList: List[String] = csvRecords.map(csvRecord => {
      csvRecord.toMap.asScala.toMap.get(qrCodeHdrColsList(1)).get.trim
    }).filter(msg => msg.nonEmpty).toList

    if(csvQRCodesList.nonEmpty) {
      val returnDIALCodes = validateDialCodes(collectionReadResult(JsonKey.CHANNEL).toString, csvQRCodesList, request)

      val invalidQRCodeErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (qrCodeHdrColsList.contains(colData._1) && (csvQRCodesList diff returnDIALCodes).contains(colData._2))
            MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidQRCodeErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidDIALCodes, ResponseCode.csvInvalidDIALCodes.getErrorMessage + invalidQRCodeErrorMessage)
    }
    // Validate QR Codes with reserved DIAL codes - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating QR Codes with reserved DIAL codes")

    // Validate Mapped Topics with Collection Framework data - START
    val mappedTopicsHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.MAPPED_TOPICS_HEADER), new TypeReference[List[String]]() {})

    val mappedTopicsList = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty)
          colData._2.trim.split(",").mkString(",")
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if(mappedTopicsList.nonEmpty) {
      val frameworkId = collectionReadResult(JsonKey.FRAMEWORK).toString
      val frameworkGetResponse = getRelatedFrameworkById(frameworkId, request)
      val frameworkGetResult = frameworkGetResponse.getResult.getOrDefault(JsonKey.FRAMEWORK, new util.HashMap[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
      val frameworkCategories = frameworkGetResult.getOrDefault(JsonKey.CATEGORIES, List.empty).asInstanceOf[List[Map[String, AnyRef]]]

      val frameworkTopicList = frameworkCategories.flatMap(categoryData => {
        categoryData.map(colData => {
          if (categoryData(JsonKey.CODE).equals(JsonKey.TOPIC) && colData._1.equalsIgnoreCase(JsonKey.TERMS)) {
            colData._2.asInstanceOf[List[Map[String, AnyRef]]].map(_.getOrElse(JsonKey.NAME, "")).asInstanceOf[List[String]]
          }
          else
            List.empty
        })
      }).filter(topic => topic.nonEmpty).flatten

      val invalidTopicsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty) {
            val topicsDataList: List[String] = colData._2.split(",").toList
            topicsDataList.map(topic => {
              if(!frameworkTopicList.contains(topic.trim))
                MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + topic)
              else
                ""
            }).filter(errmsg => errmsg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)
          }
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidTopicsErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidMappedTopics, ResponseCode.csvInvalidMappedTopics.getErrorMessage + invalidTopicsErrorMessage)
    }
    // Validate Mapped Topics with Collection Framework data - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Mapped Topics with Collection Framework data")

    // Validate Linked Contents authenticity - START
    val linkedContentHdrColsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_FIELDS), new TypeReference[List[String]]() {})

    val csvLinkedContentsList: List[String] = csvRecords.flatMap(csvRecord => {
      csvRecord.toMap.asScala.toMap.map(colData => {
        if (linkedContentHdrColsList.contains(colData._1) && colData._2.nonEmpty)
          colData._2.trim
        else
          ""
      })
    }).filter(msg => msg.nonEmpty).toList

    if (csvLinkedContentsList.nonEmpty) {
      val returnedLinkedContentsResult = searchLinkedContents(csvLinkedContentsList, request)

      val returnedLinkedContentsIdentifierList = returnedLinkedContentsResult.map(_.getOrElse(JsonKey.IDENTIFIER, "")).asInstanceOf[List[String]]

      val invalidLinkedContentsErrorMessage = csvRecords.flatMap(csvRecord => {
        csvRecord.toMap.asScala.toMap.map(colData => {
          if (linkedContentHdrColsList.contains(colData._1) && (csvLinkedContentsList diff returnedLinkedContentsIdentifierList).contains(colData._2))
            MessageFormat.format(ResponseCode.rowNum.getErrorMessage, (csvRecord.getRecordNumber + 1).toString + " - " + colData._2)
          else
            ""
        })
      }).filter(msg => msg.nonEmpty).mkString(JsonKey.COMMA_SEPARATOR)

      if (invalidLinkedContentsErrorMessage.trim.nonEmpty)
        throwClientErrorException(ResponseCode.csvInvalidLinkedContents, ResponseCode.csvInvalidLinkedContents.getErrorMessage + invalidLinkedContentsErrorMessage)

      val allowedContentTypes = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_ALLOWED_CONTENT_TYPES), new TypeReference[List[String]]() {})
      val returnedLinkedContentsContentTypeList = returnedLinkedContentsResult.map(_.getOrElse(JsonKey.CONTENT_TYPE, "")).asInstanceOf[List[String]]

      if(returnedLinkedContentsContentTypeList.exists(contentType => {
        !allowedContentTypes.contains(contentType)
      }))
        {
          val invalidContentTypeLinkedContentsList = returnedLinkedContentsResult.map(content => {
            if(!allowedContentTypes.contains(content(JsonKey.CONTENT_TYPE).toString))
              content(JsonKey.IDENTIFIER).toString
            else
              ""
          }).mkString(JsonKey.COMMA_SEPARATOR)

          if(invalidContentTypeLinkedContentsList.trim.nonEmpty)
            throwClientErrorException(ResponseCode.csvInvalidLinkedContentsContentTypes, ResponseCode.csvInvalidLinkedContentsContentTypes.getErrorMessage + invalidContentTypeLinkedContentsList)
        }
    }
    // Validate Linked Contents authenticity - END
    logger.info(request.getRequestContext,"CollectionTOCActor --> validateCSVRecordsDataAuthenticity --> after validating Linked Contents")
  }

  private def updateCollection(collectionHierarchy: Map[String, AnyRef], csvRecords: util.List[CSVRecord], mode: String, request: Request): Unit = {
    val folderHierarchyHdrColumnsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.FOLDER_HIERARCHY_COLUMNS), new TypeReference[List[String]]() {})
    val mappedTopicsHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.MAPPED_TOPICS_HEADER), new TypeReference[List[String]]() {})
    val linkedContentHdrColsList = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_LINKED_CONTENT_FIELDS), new TypeReference[List[String]]() {})
    val csvIdentifierHeader = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_CSV_IDENTIFIER_HEADER), new TypeReference[List[String]]() {}).head

    val folderInfoMap = scala.collection.mutable.Map.empty[String, AnyRef]

    csvRecords.map(csvRecord => {
      val csvRecordFolderHierarchyMap = csvRecord.toMap.asScala.toMap.filter(colData => {
        folderHierarchyHdrColumnsList.contains(colData._1) && colData._2.nonEmpty
      })

      val sortedFolderHierarchyMap = Map(csvRecordFolderHierarchyMap.toSeq.sortWith(_._1 < _._1):_*)

      val sortedFoldersDataKey = sortedFolderHierarchyMap.keys.toList
      val sortedFoldersDataList = sortedFolderHierarchyMap.values.scan("")(_+_).filter(x => x.nonEmpty).toList

      val finalSortedMap = (sortedFoldersDataKey zip sortedFoldersDataList).toMap
      val csvRecordMap = csvRecord.toMap.asScala.toMap

      sortedFolderHierarchyMap.map(folderData => {
        val folderDataHashCode = getCode(finalSortedMap(folderData._1))

        if(folderInfoMap.contains(folderDataHashCode) && ((sortedFoldersDataKey.indexOf(folderData._1)+1) != sortedFoldersDataList.size)) {
          val nodeInfoMap = folderInfoMap(folderDataHashCode).asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
          if(nodeInfoMap.contains(JsonKey.CHILDREN))
          {
            var childrenSet = nodeInfoMap(JsonKey.CHILDREN).asInstanceOf[Set[String]]
            childrenSet ++= Set(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
            nodeInfoMap(JsonKey.CHILDREN) = childrenSet
          }
          else {
            val childrenList = Set(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
            nodeInfoMap += (JsonKey.CHILDREN -> childrenList)
          }
          folderInfoMap(folderDataHashCode) = nodeInfoMap
        }
        else {
          val nodeInfo = {
            if (folderData._1.equalsIgnoreCase(sortedFolderHierarchyMap.max._1)) {
              if(mode.equals(JsonKey.UPDATE)) {
                val keywordsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if (JsonKey.KEYWORDS.equalsIgnoreCase(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.map(x => x.trim)
                  else
                    List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val mappedTopicsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if (mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.map(x => x.trim)
                  else
                    List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val dialCodeRequired = {
                  if (csvRecordMap(JsonKey.QR_CODE_REQUIRED).nonEmpty && csvRecordMap(JsonKey.QR_CODE_REQUIRED).equalsIgnoreCase(JsonKey.YES)) {
                    JsonKey.YES
                  }
                  else
                    JsonKey.NO
                }

                val dialCode = {
                  if (csvRecordMap(JsonKey.QR_CODE).nonEmpty) {
                    csvRecordMap(JsonKey.QR_CODE).trim
                  }
                  else
                    ""
                }

                val csvLinkedContentsList: Set[String] = csvRecord.toMap.asScala.toMap.map(colData => {
                  if (linkedContentHdrColsList.contains(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.toLowerCase()
                  else
                    ""
                }).filter(msg => msg.nonEmpty).toSet[String]

                scala.collection.mutable.Map(JsonKey.IDENTIFIER -> csvRecordMap(csvIdentifierHeader), JsonKey.NAME -> folderData._2,
                  JsonKey.DESCRIPTION -> csvRecordMap("Description"), JsonKey.KEYWORDS -> keywordsList, JsonKey.TOPIC -> mappedTopicsList,
                  JsonKey.DIAL_CODE_REQUIRED -> dialCodeRequired, JsonKey.DIALCODES -> dialCode, JsonKey.LINKED_CONTENT -> csvLinkedContentsList, JsonKey.LEVEL -> folderData._1)
              }
              else{
                scala.collection.mutable.Map(JsonKey.NAME -> folderData._2, JsonKey.DESCRIPTION -> csvRecordMap("Description"), JsonKey.LEVEL -> folderData._1)
              }
            }
            else {
              val childrenList = {
                if((sortedFoldersDataKey.indexOf(folderData._1)+1) != sortedFoldersDataList.size)
                  Set(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
                else
                  Set.empty[String]
              }
              scala.collection.mutable.Map(JsonKey.NAME -> folderData._2, JsonKey.CHILDREN -> childrenList, JsonKey.LEVEL -> folderData._1)
            }
          }

          folderInfoMap += (folderDataHashCode -> nodeInfo)
        }
      })
    })

    val collectionID = collectionHierarchy(JsonKey.IDENTIFIER).toString
    val collectionName = collectionHierarchy(JsonKey.NAME).toString
    val channelID = collectionHierarchy(JsonKey.CHANNEL).toString
    val frameworkID = collectionHierarchy(JsonKey.FRAMEWORK).toString
    val collectionType = collectionHierarchy(JsonKey.CONTENT_TYPE).toString
    val contentTypeToUnitTypeMapping = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TYPE_TO_UNIT_TYPE), new TypeReference[Map[String, String]]() {})
    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)

    val nodeMetadataObj = if(mode.equals(JsonKey.CREATE)) {
      """"nodeID": {"isNew": true,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection","contentType": "unitType","name": "nodeName","description": "nodeDesc","dialcodeRequired": "No","code": "nodeID","framework": "frameworkID" }}"""
    }
    else {
      """"nodeID": {"isNew": false,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection","contentType": "unitType","name": "nodeName","description": "nodeDesc","dialcodeRequired": "dialCodeRequiredVal","dialcodes": "dialCodesVal", "code": "nodeID","framework": "frameworkID", "keywords": keywordsArray, "topic": topicArray }}"""
    }

    val nodesMetadata = if(mode.equals(JsonKey.CREATE)) {
      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        nodeMetadataObj.replaceAll("nodeID",record._1).replaceAll("unitType", collectionUnitType)
          .replaceAll("nodeName", nodeInfo("name").toString).replaceAll("frameworkID", frameworkID)
          .replaceAll("nodeDesc", if(nodeInfo.contains(JsonKey.DESCRIPTION)) nodeInfo(JsonKey.DESCRIPTION).toString else "")
      }).mkString(",")
    }
    else {
      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        nodeMetadataObj.replaceAll("nodeID",nodeInfo(JsonKey.IDENTIFIER).toString).replaceAll("unitType", collectionUnitType)
          .replaceAll("nodeName", nodeInfo("name").toString).replaceAll("frameworkID", frameworkID)
          .replaceAll("nodeDesc", if(nodeInfo.contains(JsonKey.DESCRIPTION)) nodeInfo(JsonKey.DESCRIPTION).toString else "")
          .replaceAll("dialCodeRequiredVal", nodeInfo(JsonKey.DIAL_CODE_REQUIRED).toString)
          .replaceAll("dialCodesVal", nodeInfo(JsonKey.DIALCODES).toString)
          .replaceAll("keywordsArray", if(nodeInfo.contains(JsonKey.KEYWORDS) && nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].nonEmpty) nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]")
          .replaceAll("topicArray", if(nodeInfo.contains(JsonKey.TOPIC) && nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].nonEmpty) nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]")
      }).mkString(",")
    }

    val collectionL1NodeList = {
      folderInfoMap.map(nodeData => {
        if(nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.LEVEL)!=null &&
          nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.LEVEL).toString.equalsIgnoreCase
          (mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_CREATE_CSV_MANDATORY_FIELDS),
            new TypeReference[List[String]]() {}).head)) {
          if (mode.equals(JsonKey.UPDATE))
            nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.IDENTIFIER).toString
          else
            nodeData._1
        }
        else
          ""
      }).filter(node => node.nonEmpty).mkString("[\"","\",\"","\"]")
    }

    val hierarchyRootNode = s""""$collectionID": {"name":"$collectionName","collectionType":"$collectionType","root":true,"children":$collectionL1NodeList}"""

    val hierarchyChildNode = """"nodeID": {"name": "nodeName","root": false,"contentType": "unitType", "children": childrenArray}"""
    val hierarchyChildNodesMetadata = if(mode.equals(JsonKey.CREATE)) {
      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        hierarchyChildNode.replaceAll("nodeID",record._1).replaceAll("unitType", collectionUnitType).replaceAll("nodeName", nodeInfo("name").toString).replaceAll("childrenArray", if(nodeInfo.contains(JsonKey.CHILDREN)) nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].mkString("[\"","\",\"","\"]") else "[]")
      }).mkString(",")
    }
    else {
      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        val childrenFolders = {
          if(nodeInfo.contains(JsonKey.CHILDREN) &&  nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].nonEmpty
            && nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty) {
           val allChildrenSet = nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]] ++ nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]]
            allChildrenSet.map(childFolder => {
              if(folderInfoMap.contains(childFolder))
                folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](JsonKey.IDENTIFIER).toString
              else
                childFolder
            }).mkString("[\"","\",\"","\"]")
          }
          else if(nodeInfo.contains(JsonKey.CHILDREN) &&  nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].nonEmpty)
          {
            nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].map(childFolder => {
              folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](JsonKey.IDENTIFIER).toString
            }).mkString("[\"","\",\"","\"]")
          }
          else if(nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty)
          {
            nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].mkString("[\"","\",\"","\"]")
          }
          else "[]"
        }

        val folderNodeHierarchy = hierarchyChildNode.replaceAll("nodeID",nodeInfo(JsonKey.IDENTIFIER).toString).replaceAll("unitType", collectionUnitType).replaceAll("nodeName", nodeInfo("name").toString).replaceAll("childrenArray", childrenFolders)

        val contentsNode = {
          if(nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty)
          {
            val returnedLinkedContentsResult = searchLinkedContents(nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].toList, request)
            val LinkedContentInfo = returnedLinkedContentsResult.map(content => {
              hierarchyChildNode.replaceAll("nodeID",content(JsonKey.IDENTIFIER).toString).replaceAll("unitType", content(JsonKey.CONTENT_TYPE).toString).replaceAll("nodeName", content(JsonKey.NAME).toString).replaceAll("childrenArray", "[]")
            }).mkString(",")
            LinkedContentInfo
          }
          else
            ""
        }

        if(contentsNode.isEmpty)
          folderNodeHierarchy
        else
          folderNodeHierarchy + "," + contentsNode
      }).mkString(",")
    }
    val hierarchyMetadata = hierarchyRootNode + "," + hierarchyChildNodesMetadata

    val createHierarchyRequestObjString = s"""{"request": {"data": {"nodesModified": { $nodesMetadata }, "hierarchy": { $hierarchyMetadata }}}}"""
    val updateHierarchyResponse = updateHierarchy(createHierarchyRequestObjString, request)

    if(mode.equals(JsonKey.UPDATE)) {
      //invoke DIAL code Linking
     val linkDIALCodeReqMap = folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        if(nodeInfo(JsonKey.DIALCODES) != null && nodeInfo(JsonKey.DIALCODES).toString.nonEmpty)
          {
              Map(JsonKey.IDENTIFIER -> nodeInfo(JsonKey.IDENTIFIER).toString, JsonKey.DIALCODE -> nodeInfo(JsonKey.DIALCODES).toString)
          }
        else
          Map.empty
      }).filter(record => record.nonEmpty).toList.asInstanceOf[List[Map[String,String]]]

      if(linkDIALCodeReqMap.nonEmpty)
          linkDIALCode(channelID, collectionID, linkDIALCodeReqMap, request)


    }

    val identifierData = updateHierarchyResponse.getResult.getOrElse("identifiers", Map[String,String]()).asInstanceOf[Map[String,String]].asJava
    updateHierarchyResponse.getResult.put("identifiers", identifierData)

    sender.tell(updateHierarchyResponse, self)

  }

  private def getCode(code: String): String = {DigestUtils.md5Hex(code)}

  private def getTOCUrl(request: Request): Unit = {
    val startTime = Instant.now

    val collectionId = request.get(COLLECTION_ID).asInstanceOf[String]
    if (collectionId.isBlank) {
      logger.error(request.getRequestContext, "Invalid Collection Id Provided", null)
      throwClientErrorException(invalidCollection, invalidCollection.getErrorMessage)
    }

    logger.debug(request.getRequestContext, "Reading Content for Collection | Id: " + collectionId)
    val contentHierarchy = getHierarchy(collectionId, request)
    logger.info(request.getRequestContext, "Timed:CollectionTocActor:getTocUrl duration for get collection: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))

    validateCollection(contentHierarchy)
    val fileExtension = JsonKey.COLLECTION_CSV_FILE_EXTENSION
    val contentVersionKey = contentHierarchy(VERSION_KEY).asInstanceOf[String]
    val collectionNameSlug = makeSlug(contentHierarchy(NAME).asInstanceOf[String], true)
    val collectionTocFileName = collectionId + "_" + collectionNameSlug + "_" + contentVersionKey
    val prefix = File.separator + contentHierarchy(CONTENT_TYPE).toString.toLowerCase + File.separator + "toc" + File.separator + collectionTocFileName + fileExtension

    val cloudPath = {
      val path = getUri(prefix, false)
      logger.info(request.getRequestContext, "Timed:CollectionTocActor:getTocUrl duration for get cloud path url: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))
      if (path == null || path.isEmpty || path.isBlank) {
        logger.info(request.getRequestContext, "Reading Hierarchy for Collection | Id: " + collectionId)
        logger.info(request.getRequestContext, "Timed:CollectionTocActor:getTocUrl duration for get hierarchy: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))
        val finalCloudPath = createCSVAndStore(contentHierarchy, collectionTocFileName+JsonKey.COLLECTION_CSV_FILE_EXTENSION)
        logger.info(request.getRequestContext, "Timed:CollectionTocActor:getTocUrl duration for processing preparing and uploading: " + (Instant.now.toEpochMilli - startTime.toEpochMilli))
        finalCloudPath
      }
      else
        path
    }

     logger.info(request.getRequestContext, "Sending Response for Toc Download API for Collection | Id: " + collectionId)
     val collectionCSV = HashMap[String, AnyRef] (TOC_URL-> cloudPath, TTL -> getConfigValue(COLLECTION_TOC_CSV_TTL))

     val response = new Response
     response.put(COLLECTION, collectionCSV.asJava)
     sender.tell(response, self)
  }

  private def getHierarchy(collectionId: String, request: Request):Map[String, AnyRef] = {
    val response = readContent(collectionId, SUNBIRD_CONTENT_GET_HIERARCHY_API, request)
    try {
      val contentHierarchy = response.get(CONTENT).asInstanceOf[Map[String, AnyRef]]
      if (contentHierarchy.isEmpty) {
        log("Empty Hierarchy fetched | Collection Id: " + collectionId)
        throwServerErrorException(SERVER_ERROR, "Empty Hierarchy fetched for Collection Id: " + collectionId)
      }
      contentHierarchy
    } catch {
      case e: Exception =>
        logger.error(request.getRequestContext, "Error while fetching collection : " + collectionId + " with response " + serialize(response), e)
        throw e
    }
  }

  private def validateCollection(collection: Map[String, AnyRef]) {
    val allowedContentTypes = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TOC_ALLOWED_CONTENT_TYPES), new TypeReference[List[String]]() {})
    if (!COLLECTION_TOC_ALLOWED_MIMETYPE.equalsIgnoreCase(collection(MIME_TYPE).toString) || !allowedContentTypes.contains(collection(CONTENT_TYPE).toString)) throwClientErrorException(invalidCollection, invalidCollection.getErrorMessage)
    val children = collection(CHILDREN).asInstanceOf[List[AnyRef]]
    if (children.isEmpty) throwClientErrorException(noChildrenExists, noChildrenExists.getErrorMessage)
  }

  private def createCSVAndStore(collectionHierarchy: Map[String, AnyRef], collectionTocFileName: String): String = {
    val collectionName = collectionHierarchy(JsonKey.NAME).toString
    val collectionType = collectionHierarchy(JsonKey.CONTENT_TYPE).toString
    val contentTypeToUnitTypeMapping = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_TYPE_TO_UNIT_TYPE), new TypeReference[Map[String, String]]() {})
    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)

    val nodesInfoList = prepareNodeInfo(collectionUnitType, collectionHierarchy(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], Map.empty[String, AnyRef], "")
    val nodesMap = ListMap(nodesInfoList.flatten.toMap[String, AnyRef].toSeq.sortBy(_._1):_*)
    val collectionOutputTocHeaders = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.COLLECTION_OUTPUT_TOC_HEADERS), new TypeReference[List[String]]() {})
    val maxAllowedContentSize = Integer.parseInt(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TOC_MAX_FIRST_LEVEL_UNITS))
    val maxFolderLevels = mapper.readValue(ProjectUtil.getConfigValue(JsonKey.FOLDER_HIERARCHY_COLUMNS), new TypeReference[List[String]]() {}).size
    val csvFile: File = new File(collectionTocFileName)
    var out: OutputStreamWriter = null
    var csvPrinter: CSVPrinter = null
    try{
      deleteQuietly(csvFile)
      logger.info(null, "Creating file for CSV at Location: " + csvFile.getAbsolutePath)
      touch(csvFile)

      out = new OutputStreamWriter(new FileOutputStream(csvFile), StandardCharsets.UTF_8)
      out.write(ByteOrderMark.UTF_BOM)

      val csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader().withRecordSeparator(System.lineSeparator()).withQuoteMode(QuoteMode.NON_NUMERIC)
      logger.info(null, "Writing Headers to Output Stream for Collection | Id " + collectionHierarchy.get(IDENTIFIER))
      csvPrinter = new CSVPrinter(out, csvFormat)
      csvPrinter.printRecord(collectionOutputTocHeaders.asJava)
      nodesMap.foreach(record => {
        val nodeDepthIndex = record._1
        val nodeInfo = record._2.asInstanceOf[Map[String, AnyRef]]
        if(nodeInfo(JsonKey.CONTENT_TYPE).toString.equalsIgnoreCase(collectionUnitType)) {
          val nodeID = nodeInfo(JsonKey.IDENTIFIER).toString
          val recordToWrite = ListBuffer.empty[String]
          recordToWrite.append(collectionName)
          recordToWrite.append(nodeID)

          val foldersLevel = nodeDepthIndex.split(":")
          val foldersLevelId = StringBuilder.newBuilder
          for (iCounter <- 0 until maxFolderLevels) {
            if (iCounter < foldersLevel.size) {
              if (iCounter == 0)
                foldersLevelId ++= foldersLevel(iCounter)
              else {
                foldersLevelId ++= ":"
                foldersLevelId ++= foldersLevel(iCounter)
              }
              val parentNode = nodesMap(foldersLevelId.toString).asInstanceOf[Map[String, AnyRef]]
              recordToWrite.append(parentNode(JsonKey.NAME).toString)
            }
            else {
              recordToWrite.append(null)
            }
          }

          val mappedTopics = if (nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].nonEmpty) nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].mkString(",") else null
          val keywords = if (nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].nonEmpty) nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].mkString(",") else null
          val linkedContentsList = nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Seq[String]]

          recordToWrite.append(if (nodeInfo(JsonKey.DESCRIPTION).toString.nonEmpty) nodeInfo(JsonKey.DESCRIPTION).toString else null)
          recordToWrite.append(mappedTopics)
          recordToWrite.append(keywords)
          recordToWrite.append(nodeInfo(JsonKey.QR_CODE_REQUIRED).toString)
          recordToWrite.append(if (nodeInfo(JsonKey.QR_CODE).toString.nonEmpty) nodeInfo(JsonKey.QR_CODE).toString else null)

          for (idx <- 0 until maxAllowedContentSize) {
            if (idx < linkedContentsList.size) {
              recordToWrite.append(linkedContentsList(idx))
            }
            else {
              recordToWrite.append(null)
            }
          }

          csvPrinter.printRecord(recordToWrite.toList.asJava)
        }
      })

      csvPrinter.flush()

      val csvURL = upload(File.separator+collectionType.toLowerCase+File.separator+"toc", csvFile)

      try if (null != csvFile && csvFile.exists) deleteQuietly(csvFile)
      catch {
        case e: SecurityException =>
          logger.info(null, "Error! While deleting the local csv file: " + csvFile.getAbsolutePath + e)
        case e: Exception =>
          logger.info(null, "Error! Something Went wrong while deleting csv file: " + csvFile.getAbsolutePath + e)
      }

      csvURL

    }
    catch {
      case e: Exception =>
        logger.info(null, "Error writing data to file | Collection Id:" + collectionHierarchy(IDENTIFIER).toString + " - Version Key: " + collectionHierarchy(VERSION_KEY).toString + e)
        throw new ProjectCommonException(errorProcessingRequest.getErrorCode, errorProcessingRequest.getErrorMessage, SERVER_ERROR.getResponseCode)
    } finally {
      logger.info(null, "Flushing Data to File | Location:" + csvFile.getAbsolutePath + " | for Collection  | Id: " + collectionHierarchy(IDENTIFIER))
      try {
        if (csvPrinter != null) {csvPrinter.close()}
        if (out != null) out.close()
      } catch {
        case e: IOException =>
          logger.info(null, "Error writing data to file | Collection Id:" + collectionHierarchy(IDENTIFIER) + " - Version Key: " + collectionHierarchy(VERSION_KEY) + e)
      }
    }
  }

  private def prepareNodeInfo(collectionUnitType: String, childrenHierarchy: List[Map[String, AnyRef]], nodesInfoMap: Map[String, AnyRef],
                              parentDepthIndex: String): List[Map[String, AnyRef]] = {
    val nodesInfoListMet: List[Map[String, AnyRef]] = {
      childrenHierarchy.flatMap(record => {
        val linkedContents = {
          if (record.contains(JsonKey.CHILDREN)) {
            record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]].map(childNode => {
              if (!childNode(JsonKey.CONTENT_TYPE).toString.equalsIgnoreCase(collectionUnitType))
                childNode(JsonKey.IDENTIFIER).toString
              else
                ""
            }).filter(nodeId => nodeId.nonEmpty).asInstanceOf[Seq[String]]
          }
          else Seq.empty[String]
        }

        val nodeId = record(JsonKey.IDENTIFIER).toString
        val nodeName = record(JsonKey.NAME).toString
        val nodeDescription = if (record.contains(JsonKey.DESCRIPTION)) record(JsonKey.DESCRIPTION).toString else ""
        val nodeKeywords = if (record.contains(JsonKey.KEYWORDS)) record(JsonKey.KEYWORDS).asInstanceOf[List[String]] else List.empty[String]
        val nodeTopics = if (record.contains(JsonKey.TOPIC)) record(JsonKey.TOPIC).asInstanceOf[List[String]] else List.empty[String]
        val nodeDialCodeReqd = if (record.contains(JsonKey.DIAL_CODE_REQUIRED)) record(JsonKey.DIAL_CODE_REQUIRED).toString else "No"
        val nodeDIALCode = if (record.contains(JsonKey.DIALCODES)) record(JsonKey.DIALCODES).asInstanceOf[List[String]].head else ""
        val nodeDepth = if (record.contains(JsonKey.DEPTH)) record(JsonKey.DEPTH).toString.toInt else 0
        val nodeIndex = if (record.contains(JsonKey.INDEX)) record(JsonKey.INDEX).toString.toInt else 0

        val nodeInfo = Map(JsonKey.IDENTIFIER -> nodeId, JsonKey.NAME -> nodeName, JsonKey.DESCRIPTION -> nodeDescription,
          JsonKey.KEYWORDS -> nodeKeywords, JsonKey.TOPIC -> nodeTopics, JsonKey.QR_CODE_REQUIRED -> nodeDialCodeReqd, JsonKey.CONTENT_TYPE -> record(JsonKey.CONTENT_TYPE).toString,
          JsonKey.QR_CODE -> nodeDIALCode, JsonKey.DEPTH -> nodeDepth, JsonKey.INDEX -> nodeIndex, JsonKey.LINKED_CONTENT -> linkedContents)

        val appendedMap = {
          if(nodeDepth == 1)
            nodesInfoMap ++ Map(nodeDepth + "."+ nodeIndex -> nodeInfo)
          else
            nodesInfoMap ++ Map(parentDepthIndex + ":" + nodeDepth + "."+ nodeIndex -> nodeInfo)
        }

        val fetchedList = {
          if (record.contains(JsonKey.CHILDREN))
            if(nodeDepth == 1)
              prepareNodeInfo(collectionUnitType, record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, nodeDepth + "."+ nodeIndex)
            else
              prepareNodeInfo(collectionUnitType, record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, parentDepthIndex + ":" + nodeDepth + "."+ nodeIndex)
          else
            List(appendedMap)
        }
        fetchedList
      })
    }
    nodesInfoListMet
  }

}