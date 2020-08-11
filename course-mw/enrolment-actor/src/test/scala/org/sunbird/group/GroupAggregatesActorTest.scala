/*
package org.sunbird.group

import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cache.interfaces.Cache
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl

import scala.concurrent.duration.FiniteDuration

class GroupAggregatesActorTest extends FlatSpec with Matchers with MockFactory {

  val system = ActorSystem.create("system")

  "GroupAggregatesActor" should "return sucess" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    //(redisCache.get(_: String, _:String, _: Class[_])).expects(*,*,*).returns(null)
    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(validRestResponse())
    (groupDao.read(_: String, _: String, _: java.util.List[String])).expects(*,*,*).returns(validDBResponse())
    (redisCache.put(_: String, _: String, _: AnyRef)).expects(*,*,*)
    (redisCache.setMapExpiry(_: String, _: Long)).expects(*,*)
    val response = callActor(getGroupActivityAggRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))

    assert(response.getResponseCode == ResponseCode.OK)
  }

  "GroupAggregatesActor" should "return member not found" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    //(redisCache.get(_: String, _:String, _: Class[_])).expects(*,*,*).returns(null)
    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(blankRestResponse())
    val response = callActor(getGroupActivityAggRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))
    assert(response.getResponseCode == ResponseCode.OK)
  }

  "GroupAggregatesActor" should "return no enrolled member found" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    //(redisCache.get(_: String, _:String, _: Class[_])).expects(*,*,*).returns(null)
    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(validRestResponse())
    (groupDao.read(_: String, _: String, _: java.util.List[String])).expects(*,*,*).returns(blankDBResponse())
    val response = callActor(getGroupActivityAggRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))
    assert(response.getResponseCode == ResponseCode.OK)
  }

  "GroupAggregatesActor" should "return error db response" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    //(redisCache.get(_: String, _:String, _: Class[_])).expects(*,*,*).returns(null)
    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(validRestResponse())
    (groupDao.read(_: String, _: String, _: java.util.List[String])).expects(*,*,*).returns(errorDBResponse())
    val response = callActorForFailure(getGroupActivityAggRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))
    assert(response.getResponseCode == ResponseCode.SERVER_ERROR.getResponseCode)
  }

  "GroupAggregatesActor" should "return wrong operation" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    val response = callActorForFailure(getGroupActivityAggWrongRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
  }



  def blankRestResponse(): Response = {
    val response = new Response()
    response
  }

  def validRestResponse(): Response = {
    val response = new Response()
    val members = new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
      add(new java.util.HashMap[String, AnyRef] {{
        put("userId", "user1")
        put("role","admin")
        put("status", "active")
        put("name", "userName")
        put("createdBy", "userBy")
      }})
    }}
    response.put("members", members)
    response
  }

  def blankDBResponse(): Response = {
    val response = new Response()
    response
  }

  def errorDBResponse(): Response = {
    val response = new Response()
    response.setResponseCode(ResponseCode.SERVER_ERROR)
    response
  }

  def validDBResponse(): Response = {
    val response = new Response()
    val members = new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
      add(new java.util.HashMap[String, AnyRef] {{
        put("activity_type", "course1")
        put("activity_id","do_123")
        put("user_id", "user1")
        put("context_id", "context")
        put("agg", new util.HashMap[String, AnyRef](){{
          put("completedCount", 1.asInstanceOf[AnyRef])
        }})
        put("agg_last_updated", new util.HashMap[String, AnyRef](){{
          put("completedCount", new java.util.Date())
        }})
      }})
    }}
    response.put("response", members)
    response
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

  def getGroupActivityAggRequest(): Request = {
    val request = new Request
    request.setOperation("groupActivityAggregates")
    request.put("groupId", "groupid")
    request.put("activityId", "activityid")
    request.put("activityType", "activitytype")
    request
  }

  def getGroupActivityAggWrongRequest(): Request = {
    val request = new Request
    request.setOperation("groupActivity")
    request.put("groupId", "groupid")
    request.put("activityId", "activityid")
    request.put("activityType", "activitytype")
    request
  }


}
*/
