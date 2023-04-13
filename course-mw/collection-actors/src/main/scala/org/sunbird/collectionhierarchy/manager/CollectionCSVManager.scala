package org.sunbird.collectionhierarchy.manager

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.csv.{CSVFormat, CSVParser, CSVPrinter, CSVRecord, QuoteMode}
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.FileUtils.{deleteQuietly, touch}
import org.apache.commons.io.input.BOMInputStream
import org.sunbird.collectionhierarchy.util.CollectionTOCUtil.{linkDIALCode, readContent, searchLinkedContents, serialize, updateHierarchy}
import org.sunbird.collectionhierarchy.validator.CollectionCSVValidator.{allowedContentTypes, collectionNodeIdentifierHeader, collectionOutputTocHeaders, contentTypeToUnitTypeMapping, createCSVMandatoryHeaderCols, folderHierarchyHdrColumnsList, linkedContentHdrColumnsList, mappedTopicsHeader, maxFolderLevels}
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.exception.ProjectCommonException.{throwClientErrorException, throwServerErrorException}
import org.sunbird.common.models.util.JsonKey.{CHILDREN, COLLECTION_ID, COLLECTION_TOC_ALLOWED_MIMETYPE, CONTENT, CONTENT_TYPE, IDENTIFIER, MIME_TYPE, NAME, SUNBIRD_CONTENT_GET_HIERARCHY_API, VERSION_KEY}
import org.sunbird.common.models.util.{JsonKey, LoggerUtil, ProjectUtil}
import org.sunbird.common.models.util.ProjectLogger.log
import org.sunbird.common.request.Request
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.Slug.makeSlug
import org.sunbird.common.responsecode.ResponseCode.{SERVER_ERROR, errorProcessingRequest, invalidCollection, noChildrenExists}
import org.sunbird.content.util.ContentCloudStore.{getUri, upload}

import java.io.{ByteArrayInputStream, File, FileOutputStream, IOException, InputStreamReader, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.immutable.{ListMap, Map}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters.{asJavaIterableConverter, mapAsScalaMapConverter}
import scala.collection.mutable.ListBuffer

object CollectionCSVManager {

  val logger: LoggerUtil = new LoggerUtil(CollectionCSVManager.getClass)

  def readInputCSV(request: Request): CSVParser = {
    val byteArray = request.getRequest.get(JsonKey.DATA).asInstanceOf[Array[Byte]]
    logger.info(request.getRequestContext,"Sized:CollectionTocActor:upload size of request " + byteArray.length)
    val inputStream = new ByteArrayInputStream(byteArray)

    // Reading input CSV File - START
    val csvFileFormat = CSVFormat.DEFAULT.withHeader()
    val bomInputStream = new BOMInputStream(inputStream, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE)
    val character =  if (bomInputStream.hasBOM)  bomInputStream.getBOMCharsetName else StandardCharsets.UTF_8.name
    try {
     csvFileFormat.parse(new InputStreamReader(bomInputStream, character))
    }
    catch {
      case e: Exception =>
       throw e
    }
  }

  def getCode(code: String): String = {DigestUtils.md5Hex(code)}

  def validateCollection(collection: Map[String, AnyRef]) {
    if (!COLLECTION_TOC_ALLOWED_MIMETYPE.equalsIgnoreCase(collection(MIME_TYPE).toString) || !allowedContentTypes.contains(collection(CONTENT_TYPE).toString))
      throwClientErrorException(invalidCollection, invalidCollection.getErrorMessage)
    val children = collection(CHILDREN).asInstanceOf[List[AnyRef]]
    if (children.isEmpty) throwClientErrorException(noChildrenExists, noChildrenExists.getErrorMessage)
  }

  def getHierarchy(collectionId: String, request: Request):Map[String, AnyRef] = {
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

  def updateCollection(collectionHierarchy: Map[String, AnyRef], csvRecords: util.List[CSVRecord], mode: String, linkedContentsDetails: List[Map[String, AnyRef]], request: Request): Response = {

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
            if(folderData._1.equalsIgnoreCase(sortedFolderHierarchyMap.max._1)) {
              if(mode.equals(JsonKey.UPDATE)) {
                val keywordsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(JsonKey.KEYWORDS.equalsIgnoreCase(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.map(x => x.trim)
                  else List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val mappedTopicsList = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(mappedTopicsHeader.contains(colData._1) && colData._2.nonEmpty)
                    colData._2.trim.split(",").toList.map(x => x.trim)
                  else List.empty
                }).filter(msg => msg.nonEmpty).flatten.toList

                val dialCodeRequired = if(csvRecordMap(JsonKey.QR_CODE_REQUIRED).nonEmpty && csvRecordMap(JsonKey.QR_CODE_REQUIRED)
                    .equalsIgnoreCase(JsonKey.YES)) JsonKey.YES else JsonKey.NO

                val dialCode = if(csvRecordMap(JsonKey.QR_CODE).nonEmpty) csvRecordMap(JsonKey.QR_CODE).trim else ""

                val csvLinkedContentsList: Set[String] = csvRecord.toMap.asScala.toMap.map(colData => {
                  if(linkedContentHdrColumnsList.contains(colData._1) && colData._2.nonEmpty) colData._2.trim.toLowerCase() else ""
                }).filter(msg => msg.nonEmpty).toSet[String]

                scala.collection.mutable.Map(JsonKey.IDENTIFIER -> csvRecordMap(collectionNodeIdentifierHeader.head), JsonKey.NAME -> folderData._2,
                  JsonKey.DESCRIPTION -> csvRecordMap("Description"), JsonKey.KEYWORDS -> keywordsList, JsonKey.TOPIC -> mappedTopicsList,
                  JsonKey.DIAL_CODE_REQUIRED -> dialCodeRequired, JsonKey.DIALCODES -> dialCode, JsonKey.LINKED_CONTENT -> csvLinkedContentsList,
                  JsonKey.LEVEL -> folderData._1)
              }
              else{
                scala.collection.mutable.Map(JsonKey.NAME -> folderData._2, JsonKey.DESCRIPTION -> csvRecordMap("Description"), JsonKey.LEVEL -> folderData._1)
              }
            }
            else {
              val childrenList = {
                if((sortedFoldersDataKey.indexOf(folderData._1)+1) != sortedFoldersDataList.size)
                  Set(getCode(sortedFoldersDataList.get(sortedFoldersDataKey.indexOf(folderData._1)+1)))
                else Set.empty[String]
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

    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)

    val nodeMetadataObj = if(mode.equals(JsonKey.CREATE)) {
      """"nodeID": {"isNew": true,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection","contentType": "unitType","name": "nodeName",
        |"description": "nodeDesc","dialcodeRequired": "No","code": "nodeID","framework": "frameworkID" }}""".stripMargin
    }
    else {
      """"nodeID": {"isNew": false,"root": false, "metadata": {"mimeType": "application/vnd.ekstep.content-collection","contentType": "unitType","name": "nodeName",
        |"description": "nodeDesc","dialcodeRequired": "dialCodeRequiredVal","dialcodes": "dialCodesVal", "code": "nodeID","framework": "frameworkID",
        |"keywords": keywordsArray, "topic": topicArray }}""".stripMargin
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
          .replaceAll("keywordsArray", if(nodeInfo.contains(JsonKey.KEYWORDS) && nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].nonEmpty)
            nodeInfo(JsonKey.KEYWORDS).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]")
          .replaceAll("topicArray", if(nodeInfo.contains(JsonKey.TOPIC) && nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].nonEmpty)
            nodeInfo(JsonKey.TOPIC).asInstanceOf[List[String]].mkString("[\"","\",\"","\"]") else "[]")
      }).mkString(",")
    }

    val collectionL1NodeList = {
      folderInfoMap.map(nodeData => {
        if(nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.LEVEL)!=null &&
          nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.LEVEL).toString.equalsIgnoreCase
          (createCSVMandatoryHeaderCols.head)) {
          if (mode.equals(JsonKey.UPDATE))
            nodeData._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]](JsonKey.IDENTIFIER).toString
          else nodeData._1
        }
        else ""
      }).filter(node => node.nonEmpty).mkString("[\"","\",\"","\"]")
    }

    val hierarchyRootNode = s""""$collectionID": {"name":"$collectionName","collectionType":"$collectionType","root":true,"children":$collectionL1NodeList}"""

    val hierarchyChildNode = """"nodeID": {"name": "nodeName","root": false,"contentType": "unitType", "children": childrenArray}"""
    val hierarchyChildNodesMetadata = if(mode.equals(JsonKey.CREATE)) {
      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        hierarchyChildNode.replaceAll("nodeID",record._1).replaceAll("unitType", collectionUnitType)
          .replaceAll("nodeName", nodeInfo("name").toString)
          .replaceAll("childrenArray", if(nodeInfo.contains(JsonKey.CHILDREN)) nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].mkString("[\"","\",\"","\"]") else "[]")
      }).mkString(",")
    }
    else {
      val linkedContentsInfoMap: Map[String, Map[String, String]] = if(linkedContentsDetails.nonEmpty) {
        linkedContentsDetails.flatMap(linkedContentRecord => {
          Map(linkedContentRecord(JsonKey.IDENTIFIER).toString ->
            Map(JsonKey.IDENTIFIER -> linkedContentRecord(JsonKey.IDENTIFIER).toString,
              JsonKey.NAME -> linkedContentRecord(JsonKey.NAME).toString,
              JsonKey.CONTENT_TYPE -> linkedContentRecord(JsonKey.CONTENT_TYPE).toString))
        }).toMap
      } else Map.empty[String, Map[String, String]]

      folderInfoMap.map(record => {
        val nodeInfo = record._2.asInstanceOf[scala.collection.mutable.Map[String, AnyRef]]
        val childrenFolders = {
          if(nodeInfo.contains(JsonKey.CHILDREN) &&  nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].nonEmpty
            && nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty) {
            val allChildrenSet = nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]] ++ nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]]
            allChildrenSet.map(childFolder => {
              if(folderInfoMap.contains(childFolder))
                folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](JsonKey.IDENTIFIER).toString
              else childFolder
            }).mkString("[\"","\",\"","\"]")
          }
          else if(nodeInfo.contains(JsonKey.CHILDREN) &&  nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].nonEmpty)
            nodeInfo(JsonKey.CHILDREN).asInstanceOf[Set[String]].map(childFolder => {
              folderInfoMap(childFolder).asInstanceOf[scala.collection.mutable.Map[String,AnyRef]](JsonKey.IDENTIFIER).toString
            }).mkString("[\"","\",\"","\"]")
          else if(nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty)
            nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].mkString("[\"","\",\"","\"]")
          else "[]"
        }

        val folderNodeHierarchy = hierarchyChildNode.replaceAll("nodeID",nodeInfo(JsonKey.IDENTIFIER).toString)
          .replaceAll("unitType", collectionUnitType).replaceAll("nodeName", nodeInfo("name").toString).replaceAll("childrenArray", childrenFolders)

        val contentsNode = {
          if(nodeInfo.contains(JsonKey.LINKED_CONTENT) && nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].nonEmpty && linkedContentsInfoMap.nonEmpty)
          {
            val LinkedContentInfo = nodeInfo(JsonKey.LINKED_CONTENT).asInstanceOf[Set[String]].map(contentId => {
              val linkedContentDtls: Map[String, String] = linkedContentsInfoMap(contentId)
              hierarchyChildNode.replaceAll("nodeID",linkedContentDtls(JsonKey.IDENTIFIER))
                .replaceAll("unitType", linkedContentDtls(JsonKey.CONTENT_TYPE))
                .replaceAll("nodeName", linkedContentDtls(JsonKey.NAME))
                .replaceAll("childrenArray", "[]")
            }).mkString(",")
            LinkedContentInfo
          } else ""
        }

        if(contentsNode.isEmpty) folderNodeHierarchy else folderNodeHierarchy + "," + contentsNode
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
          Map(JsonKey.IDENTIFIER -> nodeInfo(JsonKey.IDENTIFIER).toString, JsonKey.DIALCODE -> nodeInfo(JsonKey.DIALCODES).toString)
        else  Map.empty
      }).filter(record => record.nonEmpty).toList.asInstanceOf[List[Map[String,String]]]

      if(linkDIALCodeReqMap.nonEmpty) linkDIALCode(channelID, collectionID, linkDIALCodeReqMap, request)
    }

    updateHierarchyResponse
  }

  def getCloudPath(request: Request, collectionHierarchy: Map[String, AnyRef]): String = {
    val collectionId = request.get(COLLECTION_ID).asInstanceOf[String]
    val fileExtension = JsonKey.COLLECTION_CSV_FILE_EXTENSION
    val contentVersionKey = collectionHierarchy(VERSION_KEY).asInstanceOf[String]
    val collectionNameSlug = makeSlug(collectionHierarchy(NAME).asInstanceOf[String], true)
    val collectionTocFileName = collectionId + "_" + collectionNameSlug + "_" + contentVersionKey
    val prefix = File.separator + collectionHierarchy(CONTENT_TYPE).toString.toLowerCase + File.separator + "toc" + File.separator + collectionTocFileName + fileExtension

    val path = getUri(prefix, false)

    if (path == null || path.isEmpty || path.isBlank) {
      logger.info(request.getRequestContext, "Reading TOC URL for Collection | Id: " + collectionId)
      createCSVAndStore(collectionHierarchy, collectionTocFileName+JsonKey.COLLECTION_CSV_FILE_EXTENSION)
    }
    else
      path

  }

  def createCSVAndStore(collectionHierarchy: Map[String, AnyRef], collectionTocFileName: String): String = {
    val collectionName = collectionHierarchy(JsonKey.NAME).toString
    val collectionType = collectionHierarchy(JsonKey.CONTENT_TYPE).toString
    val collectionUnitType = contentTypeToUnitTypeMapping(collectionType)

    val nodesInfoList = prepareNodeInfo(collectionUnitType, collectionHierarchy(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], Map.empty[String, AnyRef], "")
    val nodesMap = ListMap(nodesInfoList.flatten.toMap[String, AnyRef].toSeq.sortBy(_._1):_*)

    val maxAllowedContentSize = Integer.parseInt(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TOC_MAX_FIRST_LEVEL_UNITS))

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
        logger.info(null, "Error writing data to file | Collection Id:" + collectionHierarchy(IDENTIFIER).toString + " - Version Key: "
          + collectionHierarchy(VERSION_KEY).toString + e)
        throw new ProjectCommonException(errorProcessingRequest.getErrorCode, errorProcessingRequest.getErrorMessage, SERVER_ERROR.getResponseCode)
    } finally {
      logger.info(null, "Flushing Data to File | Location:" + csvFile.getAbsolutePath + " | for Collection  | Id: " + collectionHierarchy(IDENTIFIER))
      try {
        if (csvPrinter != null) {csvPrinter.close()}
        if (out != null) out.close()
      } catch {
        case e: IOException =>
          logger.info(null, "Error writing data to file | Collection Id:" + collectionHierarchy(IDENTIFIER) + " - Version Key: "
            + collectionHierarchy(VERSION_KEY) + e)
      }
    }
  }

  def prepareNodeInfo(collectionUnitType: String, childrenHierarchy: List[Map[String, AnyRef]], nodesInfoMap: Map[String, AnyRef],
                      parentDepthIndex: String): List[Map[String, AnyRef]] = {
    val nodesInfoListMet: List[Map[String, AnyRef]] = {
      childrenHierarchy.flatMap(record => {
        val linkedContents = {
          if (record.contains(JsonKey.CHILDREN)) {
            record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]].map(childNode => {
              if (!childNode(JsonKey.CONTENT_TYPE).toString.equalsIgnoreCase(collectionUnitType)) childNode(JsonKey.IDENTIFIER).toString
              else ""
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
          if(nodeDepth == 1) nodesInfoMap ++ Map(nodeDepth + "."+ nodeIndex -> nodeInfo)
          else nodesInfoMap ++ Map(parentDepthIndex + ":" + nodeDepth + "."+ nodeIndex -> nodeInfo)
        }

        val fetchedList = {
          if (record.contains(JsonKey.CHILDREN))
            if(nodeDepth == 1)
              prepareNodeInfo(collectionUnitType, record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, nodeDepth + "."+ nodeIndex)
            else
              prepareNodeInfo(collectionUnitType, record(JsonKey.CHILDREN).asInstanceOf[List[Map[String, AnyRef]]], appendedMap, parentDepthIndex + ":"
                + nodeDepth + "."+ nodeIndex)
          else List(appendedMap)
        }
        fetchedList
      })
    }
    nodesInfoListMet
  }

}
