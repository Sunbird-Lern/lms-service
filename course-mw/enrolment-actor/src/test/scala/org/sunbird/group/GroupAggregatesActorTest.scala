package org.sunbird.group

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.learner.actors.group.dao.impl.GroupDaoImpl

import scala.concurrent.duration.FiniteDuration

class GroupAggregatesActorTest extends FlatSpec with Matchers with MockFactory {

  val system = ActorSystem.create("system")

  "GroupAggregatesActor" should "return success on enrol" in {
    val groupAggregateUtil = mock[GroupAggregatesUtil]
    val groupDao = mock[GroupDaoImpl]

    (groupAggregateUtil.getGroupDetails(_:String, _:Request)).expects(*,*).returns(validRestResponse())
    (groupDao.read(_: String, _: String, _: java.util.List[String])).expects(*,*,*).returns(validDBResponse())
    val response = callActor(getEnrolRequest(), Props(new GroupAggregatesActor().setInsranceVariable(groupAggregateUtil, groupDao)))
    assert("CLIENT_ERROR".equalsIgnoreCase(response.getResponseCode().asInstanceOf[String]))
  }

  def validRestResponse(): Response = {
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

  def getEnrolRequest(): Request = {
    val request = new Request
    request.setOperation("groupActivityAggregates")
    request.put("groupId", "groupid")
    request.put("activityId", "activityid")
    request.put("activityType", "activitytype")
    request
  }


}
