package org.sunbird.enrolments

import java.util
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.fasterxml.jackson.databind.ObjectMapper
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.Constants
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.inf.ElasticSearchService
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil
import org.sunbird.common.request.{Request, RequestContext}
import org.sunbird.common.responsecode.ResponseCode
import org.sunbird.dto.SearchDTO

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class CourseConsumptionActorTest extends FlatSpec with Matchers with MockFactory {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val system = ActorSystem.create("system")

    "get Consumption with progressDetails" should "return response" in {
        val cassandraOperation = mock[CassandraOperation]

        val progressResponse = new java.util.HashMap[String, AnyRef]()
        progressResponse.put("key1", "val1")
        progressResponse.put("key2", "val2")

        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
                put("progressdetails", "{}")
                put("progressDetails", progressResponse)
            }})
        }})

        ((requestContext: RequestContext, keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(requestContext, keyspace, table, filters, fields)).expects(*,*,*,*,*).returns(response)
        val result = callActor(getStateReadRequestWithProgressField(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false)))

        result.getResult().get("response").toString.shouldEqual("[{progressDetails={key1=val1, key2=val2}, contentId=do_456, batchId=0123, courseId=do_123, collectionId=do_123, progressdetails={}}]")
        assert(null!= result)
    }

    def getStateReadRequestWithProgressField(): Request = {
        val request = new Request
        request.setOperation("getConsumption")
        request.put("userId", "user1")
        request.put("courseId", "do_123")
        request.put("batchId", "0123")
        request.put("contentId", "do_456")
        request.put("fields", new java.util.ArrayList[String](){{ add("progressDetails")}})

        request
    }

    "update Consumption with progressDetails" should "return success on updating the progress" in {
        val cassandraOperation = mock[CassandraOperation]
        val esService = mock[ElasticSearchService]
        val progressResponse = new java.util.HashMap[String, AnyRef]()
        progressResponse.put("key1", "val1")
        progressResponse.put("key2", "val2")
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
                put("progressDetails", progressResponse)

            }})

        }})
        (esService.search(_:RequestContext, _: SearchDTO, _: String)).expects(*,*,*).returns(concurrent.Future{validBatchData()})
        (cassandraOperation.getRecords(_:RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String])).expects(*,*,*,*,*).returns(response)
        (cassandraOperation.batchInsertLogged(_:RequestContext, _: String, _: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*,*,*,*)
        (cassandraOperation.updateRecordV2(_:RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.Map[String, AnyRef], _: Boolean)).expects(*,"sunbird_courses", "user_enrolments",*,*,true)
        val result = callActor(getStateUpdateRequestWithProgress(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false).setEsService(esService)))
        assert(null!= result)
    }

    def getStateUpdateRequestWithProgress(): Request = {
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
                put("progressDetails",new util.HashMap())

            }})
            add(new java.util.HashMap[String, AnyRef] {{
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_789")
                put("status", 2.asInstanceOf[AnyRef])
                put("progressDetails",new util.HashMap())

            }})
        }})
        request
    }
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
        ((requestContext: RequestContext, keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(requestContext, keyspace, table, filters, fields)).expects(*,*,*,*,*).returns(response)
        val result = callActor(getStateReadRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false)))
        assert(null!= result)
    }

    "get Consumption" should "return empty response" in {
        val cassandraOperation = mock[CassandraOperation]
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]])
        ((requestContext: RequestContext, keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(requestContext, keyspace, table, filters, fields)).expects(*,*,*,*,*).returns(response)
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
        (esService.search(_:RequestContext, _: SearchDTO, _: String)).expects(*,*,*).returns(concurrent.Future{validBatchData()})
        (cassandraOperation.getRecords(_:RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String])).expects(*,*,*,*,*).returns(response)
        (cassandraOperation.batchInsertLogged(_:RequestContext, _: String, _: String, _: java.util.List[java.util.Map[String, AnyRef]])).expects(*,*,*,*)
        (cassandraOperation.updateRecordV2(_:RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.Map[String, AnyRef], _: Boolean)).expects(*,"sunbird_courses", "user_enrolments",*,*,true)
        val result = callActor(getStateUpdateRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false).setEsService(esService)))
        assert(null!= result)
    }

    "update AssementScore " should "return success on updating the progress" in {
        val cassandraOperation = mock[CassandraOperation]
        val esService = mock[ElasticSearchService]
        ((requestContext: RequestContext, searchDTO: _root_.org.sunbird.dto.SearchDTO, index: _root_.scala.Predef.String) => esService.search(requestContext, searchDTO, index)).expects(*,*,*).returns(concurrent.Future{validBatchData()})
        val result = callActorForFailure(getAssementUpdateRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false).setEsService(esService)))
        assert(result.getResponseCode == ResponseCode.CLIENT_ERROR.getResponseCode)
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

    def getAssementUpdateRequest(): Request = {
        val request = new Request
        request.setOperation("updateConsumption")
        request.put("userId", "user1")
        request.put("requestedBy", "user1")
        request.put("assessments", new java.util.ArrayList[java.util.Map[String, AnyRef]]{{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("batchId", "0123")
                put("courseId", "do_123")
                put("attemptId", "abcd")
                put("contentId", "do_789")
                put("events", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
                    add(new java.util.HashMap[String, AnyRef] {{
                        put("eid", "ASSESS")
                        put("ets", 123241.asInstanceOf[AnyRef])
                    }})
                }})
            }})
        }})
        request
    }

    "get Consumption with fields" should "return success" in {
        val cassandraOperation = mock[CassandraOperation]
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]] {{
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_456")
                put("score", new java.util.ArrayList[java.util.Map[String, Object]](){{
                    add(new java.util.HashMap[String, AnyRef](){{
                        put("totalMaxScore", 1.asInstanceOf[AnyRef])
                        put("lastAttemptedOn", "2019-05-13 16:08:45:125+0530")
                        put("totalScore", 1.asInstanceOf[AnyRef])
                        put("attemptId", "do_123")
                    }})
                }})
            }})
            add(new java.util.HashMap[String, AnyRef] {{
                put("userId", "user1")
                put("courseId", "do_123")
                put("batchId", "0123")
                put("contentId", "do_789")
            }})
        }})
        (cassandraOperation.getRecords(_:RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String])).expects(*,*,*,*,*).returns(response)
        (cassandraOperation.getRecordsWithLimit(_: RequestContext, _: String, _: String, _: java.util.Map[String, AnyRef], _: java.util.List[String], _: Int)).expects(*, *, *, *, *, *).returns(response).anyNumberOfTimes()
        val result = callActor(getStateReadRequestWithFields(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false)))
        println("result : " + result)
        assert(null!= result)
    }

    def getStateReadRequestWithFields(): Request = {
        val request = new Request
        request.setOperation("getConsumption")
        request.put("userId", "user1")
        request.put("courseId", "do_123")
        request.put("batchId", "0123")
        request.put("fields", new java.util.ArrayList[String](){{ add("score")}})
        request
    }

}
