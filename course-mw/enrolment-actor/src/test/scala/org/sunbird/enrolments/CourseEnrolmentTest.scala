package org.sunbird.enrolments

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.codehaus.jackson.map.ObjectMapper
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl
import org.sunbird.learner.util.{ContentUtil, JsonUtil}
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses
import org.sunbird.common.models.util.ProjectUtil

import java.util.Date
import scala.collection.JavaConverters
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration.FiniteDuration

class CourseEnrolmentTest extends FlatSpec with Matchers with MockFactory {
    val system = ActorSystem.create("system")
    val mapper: ObjectMapper = new ObjectMapper()
    val courseDao = mock[CourseBatchDaoImpl]
    val userDao = mock[UserCoursesDaoImpl]
    val groupDao = mock[GroupDaoImpl]
    val cacheUtil = mock[RedisCacheUtil]
    val projectUtil = mock[ProjectUtil]
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ")


    "CourseEnrolmentActor" should "return success on enrol" in {
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(validCourseBatch())
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        (userDao.insertV2(_: RequestContext, _: java.util.Map[String, AnyRef])).expects(*, *)
        (cacheUtil.delete(_: String)).expects(*).once()
        val response = callActor(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }
    
    "On invalid course batch" should "return client error" in  {
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(null)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On invite only batch" should "return client error" in  {
        val courseBatch = validCourseBatch()
        courseBatch.setEnrollmentType("invite-only")
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(courseBatch)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On completed batch" should "return client error" in  {
        val courseBatch = validCourseBatch()
        courseBatch.setStatus(2)
        (courseDao.readById(_: String, _: String, _:RequestContext)).expects(*,*,*).returns(courseBatch)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On previous enrollment end  batch" should "return client error" in  {
        val courseBatch = validCourseBatch()
        courseBatch.setStatus(1)
        val date = simpleDateFormat.parse("2019-01-01")
        courseBatch.setEnrollmentEndDate(new Timestamp(date.getTime()))
        (courseDao.readById(_: String, _: String, _:RequestContext)).expects(*,*,*).returns(courseBatch)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On active enrolment" should "return client error" in  {
        val courseBatch = validCourseBatch()
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        (courseDao.readById(_: String, _: String, _:RequestContext)).expects(*,*,*).returns(courseBatch)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(userCourse)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On previous batch end date" should "return client error" in  {
        val courseBatch = validCourseBatch()
        val date = simpleDateFormat.parse("2019-07-01")
        courseBatch.setEndDate(new Timestamp(date.getTime()))
        (courseDao.readById(_: String, _: String, _:RequestContext)).expects(*,*,*).returns(courseBatch)
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On existing enrolment" should "return success on enrol" in {
        val userCourse = validUserCourse()
        userCourse.setActive(false)
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(validCourseBatch())
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(userCourse)
        (userDao.updateV2(_: RequestContext, _: String,_: String,_: String, _: java.util.Map[String, AnyRef])).expects(*,*,*,*,*)
        (cacheUtil.delete(_: String)).expects(*).once()
        val response = callActor(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }

    "Unenrol" should "return success on enrol" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        (courseDao.readById(_: String, _: String, _: RequestContext)).expects(*,*,*).returns(validCourseBatch())
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(userCourse)
        (userDao.updateV2(_: RequestContext, _: String,_: String,_: String, _: java.util.Map[String, AnyRef])).expects(*,*,*,*,*)
        (cacheUtil.delete(_: String)).expects(*).once()
        val response = callActor(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }

    "already inactive" should " return client error on unenroll" in {
        val userCourse = validUserCourse()
        userCourse.setActive(false)
        (courseDao.readById(_: String, _: String, _: RequestContext)).expects(*,*,*).returns(validCourseBatch())
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(userCourse)
        val response = callActorForFailure(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "already completed course" should " return client error on unenroll" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setStatus(2)
        userCourse.setCourseId("do_113056113694425088166")
        userCourse.setProgress(11)
        (courseDao.readById(_: String, _: String, _: RequestContext)).expects(*,*, *).returns(validCourseBatch())
        (userDao.read(_: RequestContext, _: String,_: String,_: String)).expects(*,*,*,*).returns(userCourse)
        val response = callActorForFailure(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }


    "listEnrol" should "return success on listing" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_11305605610466508811")
        userCourse.setBatchId("0130598559365038081")
        (userDao.listEnrolments(_: RequestContext, _: String, null: java.util.List[String])).expects(*,*).returns(getEnrolmentLists())
            val response = callActor(getListEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        println(response)
        assert(null != response)
    }

    "listEnrol with multibatch same course" should "return success on listing" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_11305984881537024012255")
        userCourse.setBatchId("0130598559365038081")
        val enrolmentsString = "[{\"dateTime\":1594219912979,\"lastReadContentStatus\":2,\"completionpercentage\":100,\"enrolledDate\":\"1594219912979\",\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"contentstatus\":{\"do_11305605610466508811\":2},\"batchId\":\"0130598559365038081\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"completedOn\":1595422618082,\"grade\":null,\"progress\":1,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":2},{\"dateTime\":1594219912979,\"completionpercentage\":0,\"enrolledDate\":\"1594219912978\",\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"batchId\":\"0130598559365038083\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"grade\":null,\"progress\":0,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":0}]"
        val enrolmentsList: java.util.List[java.util.Map[String, AnyRef]] = mapper.readValue(enrolmentsString, classOf[java.util.List[java.util.Map[String, AnyRef]]])
        for (i <- 0 until 2) {
            val map: java.util.Map[String, AnyRef] = enrolmentsList.get(i)
            map.put("enrolledDate", new Timestamp(System.currentTimeMillis))
        }
        (userDao.listEnrolments(_: RequestContext, _: String, null: java.util.List[String])).expects(*,*).returns(enrolmentsList)
        val response = callActor(getListEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        println(response.getResult)
        assert(null != response)
        // TODO: Unable to mock search response as it is static method, hence commented below line to run it in local.
        assert(2 == response.getResult.get("courses").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].size)
        assert(null != response.getResult.get("courses").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].get(0).get("completionPercentage"))

    }

    "listEnrol with multibatch same course" should "return sorted listing by last content access time" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_11305984881537024012255")
        userCourse.setBatchId("0130598559365038081")
        val enrolmentsString = "[{\"dateTime\":1594219912978,\"lastReadContentStatus\":2,\"completionPercentage\":100,\"enrolledDate\":null,\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"contentstatus\":{\"do_11305605610466508811\":2},\"batchId\":\"0130598559365038081\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"completedOn\":1595422618082,\"grade\":null,\"progress\":1,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":2},{\"dateTime\":1594219912979,\"completionpercentage\":0,\"enrolledDate\":null,\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"batchId\":\"0130598559365038083\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"grade\":null,\"progress\":0,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":0},{\"dateTime\":1594219912979,\"completionpercentage\":0,\"enrolledDate\":null,\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"batchId\":\"0130598559365038083\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"grade\":null,\"progress\":0,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":0}]"
        val enrolmentsList: java.util.List[java.util.Map[String, AnyRef]] = mapper.readValue(enrolmentsString, classOf[java.util.List[java.util.Map[String, AnyRef]]])
        for (i <- 0 until 3) {
            val map: java.util.Map[String, AnyRef] = enrolmentsList.get(i)
            map.put("enrolledDate", new Timestamp(System.currentTimeMillis))
        }
        enrolmentsList.get(0).put("lastContentAccessTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").parse("2021-12-24 08:20:15.875000+0000"))
        enrolmentsList.get(1).put("lastContentAccessTime", new Date())
        enrolmentsList.get(2).put("lastContentAccessTime", null)
        (userDao.listEnrolments(_: RequestContext, _: String, null: java.util.List[String])).expects(*,*).returns(enrolmentsList)
        val response = callActor(getListEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        println(response)
        assert(null != response)
        val courses = response.getResult.get("courses").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
        assert(null != courses)
        val firstElementDate = courses.get(0).get("lastContentAccessTime").asInstanceOf[Date]
        assert(null != firstElementDate)
        val secElementDate = courses.get(1).get("lastContentAccessTime").asInstanceOf[Date]
        assert(null != secElementDate)
        //assert(firstElementDate.after(secElementDate))
        assert(null == courses.get(2).get("lastContentAccessTime"))
    }

    "listEnrol with RedisConnector is true" should "return success on listing from redis RedisConnector" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_11305605610466508811")
        userCourse.setBatchId("0130598559365038081")
        (cacheUtil.get(_: String, _: String => String, _: Int)).expects(*, *, *).returns(getRedisString())
        val request = getListEnrolRequest()
        request.getContext.put("cache", true.asInstanceOf[AnyRef])
        val response = callActor(request, Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        println(response)
        assert(null != response)
    }

    "listEnrol with RedisConnector is true but empty" should "return success on listing from redis RedisConnector" in {
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_11305605610466508811")
        userCourse.setBatchId("0130598559365038081")
        (cacheUtil.get(_: String, _: String => String, _: Int)).expects(*, *, *).returns(null)
        (userDao.listEnrolments(_: RequestContext, _: String, null: java.util.List[String])).expects(*, *).returns(getEnrolmentLists())
        (cacheUtil.set(_: String, _: String, _: Int)).expects(*, *, *).once()
        val request = getListEnrolRequest()
        request.getContext.put("cache", true.asInstanceOf[AnyRef])
        val response = callActor(request, Props(new CourseEnrolmentActor(null)( cacheUtil).setDao(courseDao, userDao, groupDao)))
        println(response)
        assert(null != response)
    }

    "CourseEnrolmentActor: enrol at the enrolmentEndDate and batchEndDate" should "return success on enrol" in {
        (courseDao.readById(_: String, _: String, _: RequestContext)).expects(*,*,*).returns(getBatchWithValidEnrolmentEndDateAndBatchEndDate())
        (userDao.read(_: RequestContext,_: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        (userDao.insertV2(_: RequestContext,_: java.util.Map[String, AnyRef])).expects(*,*)
        (cacheUtil.delete(_: String)).expects(*).once()
        val response = callActor(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }

    "CourseEnrolmentActor: enrol after batchEndDate" should "return Exception on enrol" in {
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(getBatchWithInvalidBatchEndDate())
        (userDao.read(_: RequestContext,_: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
        assert(response.getMessage().equals(ResponseCode.courseBatchAlreadyCompleted.getErrorMessage))
    }

    "CourseEnrolmentActor: enrol after enrolmentEndDate" should "return Exception on enrol" in {
        (courseDao.readById(_: String, _: String,_: RequestContext)).expects(*,*,*).returns(getBatchWithInvalidEnrolmentEndDate())
        (userDao.read(_: RequestContext,_: String,_: String,_: String)).expects(*,*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null)(cacheUtil).setDao(courseDao, userDao, groupDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
        assert(response.getMessage().equals(ResponseCode.courseBatchEnrollmentDateEnded.getErrorMessage))
    }

    def validCourseBatch(): CourseBatch = {
        val courseBatch = new CourseBatch()
        courseBatch.setBatchId("0123")
        courseBatch.setCourseId("do_123")
        val date = simpleDateFormat.parse("2099-07-01")
        courseBatch.setEndDate(new Timestamp(date.getTime()))
        courseBatch.setStatus(1)
        courseBatch
    }

    def validUserCourse() : UserCourses = {
        val userCourses = new UserCourses()
        userCourses.setActive(false)
        userCourses.setUserId("user1")
        userCourses
    }

    def getEnrolRequest(): Request = {
        val request = new Request
        request.setOperation("enrol")
        request.put("userId", "user1")
        request.put("courseId", "do_123")
        request.put("batchId", "0123")
        request
    }
    def getUnEnrolRequest(): Request = {
        val request = new Request
        request.setOperation("unenrol")
        request.put("userId", "user1")
        request.put("courseId", "do_123")
        request.put("batchId", "0123")
        request
    }

    def getListEnrolRequest(): Request = {
        val request = new Request
        request.setOperation("listEnrol")
        request.put("userId", "user1")
        request
    }

    def callActor(request: Request, props: Props): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[Response](FiniteDuration.apply(120, TimeUnit.SECONDS))
    }

    def callActorWithResult(request: Request, props: Props, result: java.util.List[java.util.Map[String, AnyRef]]): Response = {
        val response = callActor(request, props)
        response.getResult.put("courses", result)
        response
    }

    def callActorForFailure(request: Request, props: Props): ProjectCommonException = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[ProjectCommonException](FiniteDuration.apply(10, TimeUnit.SECONDS))
    }


    def getEnrolmentLists(): java.util.List[java.util.Map[String, AnyRef]] = {
        val enrolmentString = "[{\"dateTime\":1594219912979,\"lastReadContentStatus\":2,\"completionpercentage\":100,\"enrolledDate\":\"1594219912979\",\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"active\":true,\"contentstatus\":{\"do_11305605610466508811\":2},\"batchId\":\"0130598559365038081\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"certificates\":[],\"completedOn\":1595422618082,\"grade\":null,\"progress\":1,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":2}]"
        mapper.readValue(enrolmentString, classOf[java.util.List[java.util.Map[String, AnyRef]]])
    }

    def getCourseSearchResult(): java.util.List[java.util.Map[String, AnyRef]] = {
        val searchCourseString = "{\"contents\":[{\"ownershipType\":[\"createdBy\"],\"copyright\":\"Sunbird\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"organisation\":[\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"online\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884430_do_11305984881537024012255_1.0_online.ecar\",\"size\":4916.0},\"spine\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"size\":76270.0}},\"leafNodes\":[\"do_11305605610466508811\"],\"c_sunbird_dev_private_batch_count\":0,\"objectType\":\"Content\",\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11305984881537024012255/artifact/06_maths_book_1566813333849_1580197829074.thumb.jpg\",\"children\":[\"do_11305605610466508811\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"lockKey\":\"46c45d18-a377-4b65-9abd-06e9c14c35a1\",\"totalCompressedSize\":499149.0,\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"application/vnd.ekstep.ecml-archive\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_11305984881537024012255\",\"lastUpdatedBy\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"audience\":[\"Learner\"],\"visibility\":\"Default\",\"toc_url\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11305984881537024012255/artifact/do_11305984881537024012255_toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",\"consumerId\":\"b22e0a14-075a-4f8b-8ccb-e35467488723\",\"childNodes\":[\"do_11305984921113395212256\",\"do_11305605610466508811\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"Ekstep\",\"version\":2,\"license\":\"CC BY 4.0\",\"prevState\":\"Draft\",\"size\":76270.0,\"lastPublishedOn\":\"2020-07-08T14:51:23.883+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Auto-cert-issue-9th\",\"status\":\"Live\",\"code\":\"org.sunbird.yrWzZq\",\"prevStatus\":\"Processing\",\"description\":\"Enter description for Course\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11294498061374259215/artifact/06_maths_book_1566813333849_1580197829074.jpg\",\"createdOn\":\"2020-07-08T14:50:26.097+0000\",\"reservedDialcodes\":\"{\\\"G8R5D1\\\":0}\",\"batches\":[{\"createdFor\":[\"ORG_001\"],\"endDate\":null,\"name\":\"Auto-cert-issue-9th\",\"batchId\":\"0130598559365038081\",\"enrollmentType\":\"open\",\"enrollmentEndDate\":null,\"startDate\":\"2020-07-08\",\"status\":1},{\"createdFor\":[\"ORG_001\"],\"endDate\":null,\"name\":\"Auto-cert-issue-9th\",\"batchId\":\"01306955770809548820\",\"enrollmentType\":\"open\",\"enrollmentEndDate\":null,\"startDate\":\"2020-07-22\",\"status\":1}],\"copyrightYear\":2020,\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-07-08T14:51:23.359+0000\",\"licenseterms\":\"By creating and uploading content on DIKSHA, you consent to publishing this content under the Creative Commons Framework, specifically under the CC-BY-SA 4.0 license.\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2020-07-23T12:24:38.441+0000\",\"dialcodeRequired\":\"No\",\"createdFor\":[\"ORG_001\"],\"lastStatusChangedOn\":\"2020-07-08T14:51:24.775+0000\",\"creator\":\"Mentor First User\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1594219883359\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"TPD\",\"depth\":0,\"s3Key\":\"ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"dialcodes\":[\"G8R5D1\"],\"createdBy\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"compatibilityLevel\":4,\"leafNodesCount\":1,\"IL_UNIQUE_ID\":\"do_11305984881537024012255\",\"resourceType\":\"Course\",\"node_id\":450530,\"c_sunbird_dev_open_batch_count\":2}],\"count\":1,\"params\":{\"resmsgId\":\"5d848293-eee3-4a57-9c84-3867dbe3746a\",\"apiId\":\"api.search-service.search\"}}"
        mapper.readValue(searchCourseString, classOf[java.util.List[java.util.Map[String, AnyRef]]])
    }

    def getRedisString(): String = {
        "{\"id\":null,\"ver\":null,\"ts\":null,\"params\":null,\"responseCode\":\"OK\",\"result\":{\"courses\":[{\"dateTime\":1594219912979,\"lastReadContentStatus\":2,\"completionpercentage\":100,\"enrolledDate\":\"1594219912979\",\"addedBy\":\"6cf06951-55fe-2a81-4e37-4475428ece80\",\"delta\":null,\"contentId\":\"do_11305984881537024012255\",\"active\":true,\"description\":\"Enter description for Course\",\"courseLogoUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11305984881537024012255/artifact/06_maths_book_1566813333849_1580197829074.thumb.jpg\",\"contentstatus\":{\"do_11305605610466508811\":2},\"batchId\":\"0130598559365038081\",\"userId\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"content\":{\"ownershipType\":[\"createdBy\"],\"copyright\":\"Sunbird\",\"channel\":\"b00bc992ef25f1a9a8d63291e20efc8d\",\"downloadUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"organisation\":[\"Sunbird\"],\"language\":[\"English\"],\"mimeType\":\"application/vnd.ekstep.content-collection\",\"variants\":{\"online\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884430_do_11305984881537024012255_1.0_online.ecar\",\"size\":4916.0},\"spine\":{\"ecarUrl\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"size\":76270.0}},\"leafNodes\":[\"do_11305605610466508811\"],\"c_sunbird_dev_private_batch_count\":0,\"objectType\":\"Content\",\"appIcon\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11305984881537024012255/artifact/06_maths_book_1566813333849_1580197829074.thumb.jpg\",\"children\":[\"do_11305605610466508811\"],\"appId\":\"dev.sunbird.portal\",\"contentEncoding\":\"gzip\",\"lockKey\":\"46c45d18-a377-4b65-9abd-06e9c14c35a1\",\"totalCompressedSize\":499149.0,\"mimeTypesCount\":\"{\\\"application/vnd.ekstep.content-collection\\\":1,\\\"application/vnd.ekstep.ecml-archive\\\":1}\",\"contentType\":\"Course\",\"identifier\":\"do_11305984881537024012255\",\"lastUpdatedBy\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"audience\":[\"Learner\"],\"visibility\":\"Default\",\"toc_url\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11305984881537024012255/artifact/do_11305984881537024012255_toc.json\",\"contentTypesCount\":\"{\\\"CourseUnit\\\":1,\\\"Resource\\\":1}\",\"consumerId\":\"b22e0a14-075a-4f8b-8ccb-e35467488723\",\"childNodes\":[\"do_11305984921113395212256\",\"do_11305605610466508811\"],\"mediaType\":\"content\",\"osId\":\"org.ekstep.quiz.app\",\"graph_id\":\"domain\",\"nodeType\":\"DATA_NODE\",\"lastPublishedBy\":\"Ekstep\",\"version\":2,\"license\":\"CC BY 4.0\",\"prevState\":\"Draft\",\"size\":76270.0,\"lastPublishedOn\":\"2020-07-08T14:51:23.883+0000\",\"IL_FUNC_OBJECT_TYPE\":\"Content\",\"name\":\"Auto-cert-issue-9th\",\"status\":\"Live\",\"code\":\"org.sunbird.yrWzZq\",\"prevStatus\":\"Processing\",\"description\":\"Enter description for Course\",\"idealScreenSize\":\"normal\",\"posterImage\":\"https://sunbirddev.blob.core.windows.net/sunbird-content-dev/content/do_11294498061374259215/artifact/06_maths_book_1566813333849_1580197829074.jpg\",\"createdOn\":\"2020-07-08T14:50:26.097+0000\",\"reservedDialcodes\":\"{\\\"G8R5D1\\\":0}\",\"batches\":[{\"createdFor\":[\"ORG_001\"],\"endDate\":null,\"name\":\"Auto-cert-issue-9th\",\"batchId\":\"0130598559365038081\",\"enrollmentType\":\"open\",\"enrollmentEndDate\":null,\"startDate\":\"2020-07-08\",\"status\":1},{\"createdFor\":[\"ORG_001\"],\"endDate\":null,\"name\":\"Auto-cert-issue-9th\",\"batchId\":\"01306955770809548820\",\"enrollmentType\":\"open\",\"enrollmentEndDate\":null,\"startDate\":\"2020-07-22\",\"status\":1}],\"copyrightYear\":2020,\"contentDisposition\":\"inline\",\"lastUpdatedOn\":\"2020-07-08T14:51:23.359+0000\",\"licenseterms\":\"By creating and uploading content on DIKSHA, you consent to publishing this content under the Creative Commons Framework, specifically under the CC-BY-SA 4.0 license.\",\"SYS_INTERNAL_LAST_UPDATED_ON\":\"2020-07-23T12:24:38.441+0000\",\"dialcodeRequired\":\"No\",\"createdFor\":[\"ORG_001\"],\"lastStatusChangedOn\":\"2020-07-08T14:51:24.775+0000\",\"creator\":\"Mentor First User\",\"IL_SYS_NODE_TYPE\":\"DATA_NODE\",\"os\":[\"All\"],\"pkgVersion\":1.0,\"versionKey\":\"1594219883359\",\"idealScreenDensity\":\"hdpi\",\"framework\":\"TPD\",\"depth\":0,\"s3Key\":\"ecar_files/do_11305984881537024012255/auto-cert-issue-9th_1594219884062_do_11305984881537024012255_1.0_spine.ecar\",\"dialcodes\":[\"G8R5D1\"],\"createdBy\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"compatibilityLevel\":4,\"leafNodesCount\":1,\"IL_UNIQUE_ID\":\"do_11305984881537024012255\",\"resourceType\":\"Course\",\"node_id\":450530,\"c_sunbird_dev_open_batch_count\":2},\"completionPercentage\":100,\"courseName\":\"Auto-cert-issue-9th\",\"certificates\":[],\"completedOn\":1595422618082,\"leafNodesCount\":1,\"grade\":null,\"progress\":1,\"lastReadContentId\":\"do_11305605610466508811\",\"courseId\":\"do_11305984881537024012255\",\"status\":2}]}}"
    }

    @throws[Exception]
    private def getReadEntriesResponse: Response = {
        val responseString = "{\"id\":null,\"ver\":null,\"ts\":null,\"params\":null,\"responseCode\":\"OK\",\"result\":{\"response\":[{\"agg\":{\"completedCount\":1},\"user_id\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"activity_type\":\"Course\",\"agg_last_updated\":{\"completedCount\":1595506598142},\"activity_id\":\"do_11305984881537024012255\",\"context_id\":\"cb:0130598559365038081\"}]}}"
        JsonUtil.deserialize(responseString, classOf[Response])
    }
    
    private def getBatchWithValidEnrolmentEndDateAndBatchEndDate(): CourseBatch = {
        val startDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(3).format(dateTimeFormatter)))
        val enrolmentEndDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().plusDays(7).format(dateTimeFormatter)))
        val endDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().plusDays(10).format(dateTimeFormatter)))
        val batchData: String = "{\"batchId\": \"0130901005678510081\",\"endDate\": \""+ endDate +"\",\"description\": \"batch description1\",\"batchId\": \"0130901005678510081\",\"createdDate\": \"2020-08-20 08:28:47:534+0000\",\"createdBy\": \"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"name\": \"Batch-3\",\"enrollmentType\": \"open\",\"courseId\": \"do_11308799051844812811152\",\"enrollmentEndDate\": \""+ enrolmentEndDate +"\",\"startDate\": \""+ startDate +"\",\"status\": 1}"
        mapper.setDateFormat(sd)
        mapper.readValue(batchData, classOf[CourseBatch])
    }

    private def getBatchWithInvalidEnrolmentEndDate(): CourseBatch = {
        val startDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(3).format(dateTimeFormatter)))
        val endDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().plusDays(1).format(dateTimeFormatter)))
        val enrolmentEnddate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(1).format(dateTimeFormatter)))
        val batchData: String = "{\"batchId\": \"0130901005678510081\",\"endDate\": \""+ endDate +"\",\"description\": \"batch description1\",\"batchId\": \"0130901005678510081\",\"createdDate\": \"2020-08-20 08:28:47:534+0000\",\"createdBy\": \"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"name\": \"Batch-3\",\"enrollmentType\": \"open\",\"courseId\": \"do_11308799051844812811152\",\"enrollmentEndDate\": \""+ enrolmentEnddate +"\",\"startDate\": \""+ startDate +"\",\"status\": 1}"
        mapper.setDateFormat(sd)
        mapper.readValue(batchData, classOf[CourseBatch])
    }

    private def getBatchWithInvalidBatchEndDate(): CourseBatch = {
        val startDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(3).format(dateTimeFormatter)))
        val endDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(1).format(dateTimeFormatter)))
        val enrolmentEndDate = sd.format(simpleDateFormat.parse(LocalDateTime.now().minusDays(2).format(dateTimeFormatter)))
        val batchData: String = "{\"batchId\": \"0130901005678510081\",\"endDate\": \""+ endDate +"\",\"description\": \"batch description1\",\"batchId\": \"0130901005678510081\",\"createdDate\": \"2020-08-20 08:28:47:534+0000\",\"createdBy\": \"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"name\": \"Batch-3\",\"enrollmentType\": \"open\",\"courseId\": \"do_11308799051844812811152\",\"enrollmentEndDate\": \""+ enrolmentEndDate +"\",\"startDate\": \""+ startDate +"\",\"status\": 1}"
        mapper.setDateFormat(sd)
        mapper.readValue(batchData, classOf[CourseBatch])
    }
}
