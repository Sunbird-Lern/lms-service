package org.sunbird.enrolments

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.inf.ElasticSearchService
import org.sunbird.common.models.response.Response
import org.sunbird.common.request.Request
import org.sunbird.dto.SearchDTO

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class CourseConsumptionActorTest extends FlatSpec with Matchers with MockFactory {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val system = ActorSystem.create("system")
    
    "get Consumption" should "return success on not giving contentIds" in {
        val cassandraOperation = mock[CassandraOperation]
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
            }})
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_789")
            }})
        }})
        (cassandraOperation.getRecords(_: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String])).expects(*,*,*,*).returns(response)
        val result = callActor(getStateReadRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false)))
        assert(null!= result)
    }

    "update Consumption" should "return success on updating the progress" in {
        val cassandraOperation = mock[CassandraOperation]
        val esService = mock[ElasticSearchService]
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
            }})
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_789")
            }})
        }})
        (esService.search(_: SearchDTO, _: String)).expects(*,*).returns(concurrent.Future{validBatchData()})
        (cassandraOperation.getRecords(_: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String])).expects(*,*,*,*).returns(response)
        (cassandraOperation.batchInsert(_: String, _: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*,*,*)
        (cassandraOperation.upsertRecord(_: String, _: String, _: java.util.Map[String, AnyRef])).expects(*,*,*)
        val result = callActor(getStateUpdateRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false).setEsService(esService)))
        assert(null!= result)
    }

    def callActor(request: Request, props: Props): Response = {
        val probe = new TestKit(system)
        val actorRef = system.actorOf(props)
        actorRef.tell(request, probe.testActor)
        probe.expectMsgType[Response](FiniteDuration.apply(10, TimeUnit.SECONDS))
    }


    def getStateReadRequest(): Request = {
        val request = new Request
        request.setOperation("getConsumption")
        request.put("userId", "user1")
        request.put("courseId", "do_123")
        request.put("batchId", "0123")
        request
    }

    def getStateUpdateRequest(): Request = {
        val request = new Request
        request.setOperation("updateConsumption")
        request.put("userId", "user1")
        request.put("requestedBy", "user1")
        request.put("contents", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
                put("status", 2.asInstanceOf[AnyRef])
            }})
            add(new java.util.HashMap[String, AnyRef] {{
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_789")
                put("status", 2.asInstanceOf[AnyRef])
            }})
        }})
        request
    }

    def validBatchData(): java.util.Map[String, AnyRef] = {
        new java.util.HashMap[String, AnyRef] {{
            put("content", new java.util.ArrayList[java.util.Map[String, AnyRef]]{{
                add(new java.util.HashMap[String, AnyRef] {{
                    put("batchId", "0123")
                    put("courseId", "do_123")
                    put("status", 1.asInstanceOf[AnyRef])
                }})
            }})
        }}
    }

}
