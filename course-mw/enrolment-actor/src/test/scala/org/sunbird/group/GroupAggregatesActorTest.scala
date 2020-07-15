package org.sunbird.group

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
import org.sunbird.redis.RedisCache

import scala.concurrent.duration.FiniteDuration

class GroupAggregatesActorTest extends FlatSpec with Matchers with MockFactory {

  val system = ActorSystem.create("system")

  "GroupAggregatesActor" should "return member not found" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]
    val redisCache = mock[Cache]
    (redisCache.get(_: String, _:String, _: Class[_])).expects(*,*,*).returns(null)
    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(blankRestResponse())
    val response = callActorForFailure(getEnrolRequest(), Props(new GroupAggregatesActor().setInstanceVariable(groupAggregateUtil, groupDao, redisCache)))
    assert(response.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
  }

  def blankRestResponse(): Response = {
    val response = new Response()
    response
  }

  def validDBResponse(): Response = {
    val response = new Response()
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

  def getEnrolRequest(): Request = {
    val request = new Request
    request.setOperation("groupActivityAggregates")
    request.put("groupId", "groupid")
    request.put("activityId", "activityid")
    request.put("activityType", "activitytype")
    request
  }


}
