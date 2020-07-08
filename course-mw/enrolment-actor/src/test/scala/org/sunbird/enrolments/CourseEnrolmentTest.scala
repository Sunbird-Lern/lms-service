package org.sunbird.enrolments

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.coursebatch.dao.impl.{CourseBatchDaoImpl, UserCoursesDaoImpl}
import org.sunbird.models.course.batch.CourseBatch
import org.sunbird.models.user.courses.UserCourses

import scala.concurrent.duration.FiniteDuration

class CourseEnrolmentTest extends FlatSpec with Matchers with MockFactory {
    val system = ActorSystem.create("system")
    
    "CourseEnrolmentActor" should "return success on enrol" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(validCourseBatch())
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(null)
        (userDao.insertV2(_: java.util.Map[String, AnyRef])).expects(*)
        val response = callActor(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }

    "On invalid course batch" should "return client error" in  {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(null)
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }
    
    "On invite only batch" should "return client error" in  {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val courseBatch = validCourseBatch()
        courseBatch.setEnrollmentType("invite-only")
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(courseBatch)
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On completed batch" should "return client error" in  {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val courseBatch = validCourseBatch()
        courseBatch.setStatus(2)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(courseBatch)
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On active enrolment" should "return client error" in  {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val courseBatch = validCourseBatch()
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(courseBatch)
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(userCourse)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On previous batch end date" should "return client error" in  {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val courseBatch = validCourseBatch()
        courseBatch.setEndDate("2019-07-01")
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(courseBatch)
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(null)
        val response = callActorForFailure(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "On existing enrolment" should "return success on enrol" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val userCourse = validUserCourse()
        userCourse.setActive(false)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(validCourseBatch())
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(userCourse)
        (userDao.updateV2(_: String,_: String,_: String, _: java.util.Map[String, AnyRef])).expects(*,*,*,*)
        val response = callActor(getEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }
    
    "Unenrol" should "return success on enrol" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(validCourseBatch())
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(userCourse)
        (userDao.updateV2(_: String,_: String,_: String, _: java.util.Map[String, AnyRef])).expects(*,*,*,*)
        val response = callActor(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert("Success".equalsIgnoreCase(response.get("response").asInstanceOf[String]))
    }
    
    "already inactive" should " return client error on unenroll" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val userCourse = validUserCourse()
        userCourse.setActive(false)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(validCourseBatch())
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(userCourse)
        val response = callActorForFailure(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }

    "already completed course" should " return client error on unenroll" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setStatus(2)
        (courseDao.readById(_: String, _: String)).expects(*,*).returns(validCourseBatch())
        (userDao.read(_: String,_: String,_: String)).expects(*,*,*).returns(userCourse)
        val response = callActorForFailure(getUnEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
    }


    "listEnrol" should "return success on listing" in {
        val courseDao = mock[CourseBatchDaoImpl]
        val userDao = mock[UserCoursesDaoImpl]
        val userCourse = validUserCourse()
        userCourse.setActive(true)
        userCourse.setCourseId("do_123")
        userCourse.setBatchId("0123")
        
        val list = new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId","do_123")
                put("batchId", "0123")
                put("active", true.asInstanceOf[AnyRef])
            }})
        }}
        
        
        (userDao.listEnrolments(_ : String)).expects(*).returns(list)
        
        val response = callActor(getListEnrolRequest(), Props(new CourseEnrolmentActor(null).setDao(courseDao, userDao)))
        println(response)
        assert(null != response)
    }

    def validCourseBatch(): CourseBatch = {
        val courseBatch = new CourseBatch()
        courseBatch.setBatchId("0123")
        courseBatch.setCourseId("do_123")
        courseBatch.setEndDate("2099-07-01")
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
        probe.expectMsgType[Response](FiniteDuration.apply(10, TimeUnit.SECONDS))
    }

    def callActorForFailure(request: Request, props: Props): ProjectCommonException = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[ProjectCommonException](FiniteDuration.apply(10, TimeUnit.SECONDS))
    }
}
