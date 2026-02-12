package org.sunbird.activity.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.domain.{ContentStatus, UserContentConsumption}
import org.sunbird.request.RequestContext

import java.util
import java.util.Date

class ActivityAggregateUtilTest extends AnyFlatSpec with Matchers {

  val aggUtil = new ActivityAggregateUtil()
  // Correct RequestContext instantiation
  implicit val requestContext: RequestContext = new RequestContext("channel", "pdataId", "env", "did", "sid", "pid", "pver", null)

  "getContentStatusFromContents" should "convert Java list to ContentStatus map" in {
    val contentsList = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val content1 = new util.HashMap[String, AnyRef]()
    content1.put("contentId", "content1")
    content1.put("status", Int.box(2))
    content1.put("progress", Int.box(100))
    contentsList.add(content1)

    val result = aggUtil.getContentStatusFromContents(contentsList)
    
    result.size shouldBe 1
    result.contains("content1") shouldBe true
    result("content1").status shouldBe 2
    result("content1").completedCount shouldBe 1
  }

  it should "filter out contents with status 0" in {
    val contentsList = new util.ArrayList[util.Map[String, AnyRef]]()
    
    val content1 = new util.HashMap[String, AnyRef]()
    content1.put("contentId", "content1")
    content1.put("status", Int.box(0))
    contentsList.add(content1)

    val content2 = new util.HashMap[String, AnyRef]()
    content2.put("contentId", "content2")
    content2.put("status", Int.box(2))
    contentsList.add(content2)

    val result = aggUtil.getContentStatusFromContents(contentsList)
    
    result.size shouldBe 1
    result.contains("content2") shouldBe true
    result.contains("content1") shouldBe false
  }

  it should "merge multiple entries for same content" in {
    val contentsList = new util.ArrayList[util.Map[String, AnyRef]]()
    
    // Same content ID, different entries
    val content1a = new util.HashMap[String, AnyRef]()
    content1a.put("contentId", "content1")
    content1a.put("status", Int.box(1))
    contentsList.add(content1a)

    val content1b = new util.HashMap[String, AnyRef]()
    content1b.put("contentId", "content1")
    content1b.put("status", Int.box(2))
    contentsList.add(content1b)

    val result = aggUtil.getContentStatusFromContents(contentsList)
    
    result.size shouldBe 1
    result("content1").status shouldBe 2 // Max status
    result("content1").viewCount shouldBe 2 // Sum of views
  }

  "mergeConsumptionData" should "merge input and DB data correctly" in {
    val inputMap = Map(
      "content1" -> ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true)
    )
    val inputData = UserContentConsumption("user1", "batch1", "course1", inputMap)

    val dbMap = Map(
      "content1" -> ContentStatus("content1", 1, 0, 2, 50, null, null, null, fromInput = false)
    )
    val dbData = UserContentConsumption("user1", "batch1", "course1", dbMap)

    val result = aggUtil.mergeConsumptionData(inputData, dbData)

    result.contents("content1").status shouldBe 2 // Max
    result.contents("content1").viewCount shouldBe 3 // 1 + 2
    result.contents("content1").completedCount shouldBe 1 // 1 + 0
    result.contents("content1").progress shouldBe 100 // Status is 2, so 100
  }

  it should "preserve DB contents not in input" in {
    val inputMap = Map(
      "content1" -> ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true)
    )
    val inputData = UserContentConsumption("user1", "batch1", "course1", inputMap)

    val dbMap = Map(
      "content1" -> ContentStatus("content1", 1, 0, 2, 50, null, null, null, fromInput = false),
      "content2" -> ContentStatus("content2", 2, 1, 3, 100, null, null, null, fromInput = false)
    )
    val dbData = UserContentConsumption("user1", "batch1", "course1", dbMap)

    val result = aggUtil.mergeConsumptionData(inputData, dbData)

    result.contents.size shouldBe 2
    result.contents.contains("content1") shouldBe true
    result.contents.contains("content2") shouldBe true
    result.contents("content2").viewCount shouldBe 3 // Unchanged from DB
  }

  "getEventActions" should "generate start event for first view" in {
    val dbCC = ContentStatus("content1", 0, 0, 0, 0, null, null, null, fromInput = false)
    val inputCC = ContentStatus("content1", 1, 0, 1, 10, null, null, null, fromInput = true)

    val events = aggUtil.getEventActions(dbCC, inputCC)

    events should contain("start")
  }

  it should "generate complete event for first completion" in {
    val dbCC = ContentStatus("content1", 1, 0, 2, 50, null, null, null, fromInput = false)
    val inputCC = ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true)

    val events = aggUtil.getEventActions(dbCC, inputCC)

    events should contain("complete")
  }

  it should "not generate start event for subsequent views" in {
    val dbCC = ContentStatus("content1", 1, 0, 3, 50, null, null, null, fromInput = false)
    val inputCC = ContentStatus("content1", 1, 0, 1, 50, null, null, null, fromInput = true)

    val events = aggUtil.getEventActions(dbCC, inputCC)

    events should not contain "start"
  }

  "computeCourseActivityAgg" should "calculate progress correctly" in {
    val consumptionMap = Map(
      "content1" -> ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true),
      "content2" -> ContentStatus("content2", 1, 0, 1, 50, null, null, null, fromInput = true),
      "content3" -> ContentStatus("content3", 2, 1, 1, 100, null, null, null, fromInput = true)
    )
    val consumption = UserContentConsumption("user1", "batch1", "course1", consumptionMap)

    val leafNodes = List("content1", "content2", "content3", "content4")
    val optionalNodes = List.empty[String]

    val result = aggUtil.computeCourseActivityAgg(consumption, leafNodes, optionalNodes, requestContext)

    result.isDefined shouldBe true
    val agg = result.get.activityAgg
    agg.aggregates("completedCount") shouldBe 2.0 // content1 and content3
  }
  
  it should "exclude optional nodes from calculation" in {
    val consumptionMap = Map(
      "content1" -> ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true),
      "content2" -> ContentStatus("content2", 1, 0, 1, 50, null, null, null, fromInput = true)
    )
    val consumption = UserContentConsumption("user1", "batch1", "course1", consumptionMap)

    val leafNodes = List("content1", "content2", "content3")
    val optionalNodes = List("content3") // content3 is optional

    val result = aggUtil.computeCourseActivityAgg(consumption, leafNodes, optionalNodes, requestContext)

    result.isDefined shouldBe true
    val collProgress = result.get.collectionProgress
    collProgress.isDefined shouldBe true
    // Only content1 and content2 count, content1 is completed
    // So progress is 1 out of 2 required = 50%
    collProgress.get.progress shouldBe 1
  }

  it should "mark course as completed when all required contents are done" in {
    val consumptionMap = Map(
      "content1" -> ContentStatus("content1", 2, 1, 1, 100, null, null, null, fromInput = true),
      "content2" -> ContentStatus("content2", 2, 1, 1, 100, null, null, null, fromInput = true)
    )
    val consumption = UserContentConsumption("user1", "batch1", "course1", consumptionMap)

    val leafNodes = List("content1", "content2")
    val optionalNodes = List.empty[String]

    val result = aggUtil.computeCourseActivityAgg(consumption, leafNodes, optionalNodes, requestContext)

    result.isDefined shouldBe true
    val collProgress = result.get.collectionProgress.get
    collProgress.completed shouldBe true
    collProgress.completedOn should not be null
  }

  "getCompletionPercentage" should "calculate percentage correctly" in {
    aggUtil.getCompletionPercentage(5, 10) shouldBe 50
    aggUtil.getCompletionPercentage(10, 10) shouldBe 100
    aggUtil.getCompletionPercentage(0, 10) shouldBe 0
  }

  it should "handle division by zero" in {
    aggUtil.getCompletionPercentage(5, 0) shouldBe 0
  }

  "getCompletionStatus" should "return correct status codes" in {
    aggUtil.getCompletionStatus(0, 10) shouldBe 0 // Not started
    aggUtil.getCompletionStatus(5, 10) shouldBe 1 // In progress
    aggUtil.getCompletionStatus(10, 10) shouldBe 2 // Completed
    aggUtil.getCompletionStatus(15, 10) shouldBe 2 // Over-completed
  }

  "compareTime" should "return latest timestamp" in {
    val earlier = new Date(1000000)
    val later = new Date(2000000)

    aggUtil.compareTime(earlier, later) shouldBe later
    aggUtil.compareTime(later, earlier) shouldBe later
  }

  it should "handle null timestamps" in {
    val someDate = new Date()
    
    aggUtil.compareTime(null, someDate) shouldBe someDate
    aggUtil.compareTime(someDate, null) shouldBe someDate
    aggUtil.compareTime(null, null) should not be null
  }

  "parseDate" should "parse date string correctly" in {
    val dateString = "2024-01-01 10:00:00:000+0000"
    val result = aggUtil.parseDate(dateString)
    
    result should not be null
  }

  it should "handle null gracefully" in {
    aggUtil.parseDate(null) shouldBe null
    aggUtil.parseDate("null") shouldBe null
    aggUtil.parseDate("") shouldBe null
  }

  it should "handle Date object passthrough" in {
    val date = new Date()
    aggUtil.parseDate(date) shouldBe date
  }
}
