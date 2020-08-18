package org.sunbird.enrolments

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.exception.ProjectCommonException
import org.sunbird.common.inf.ElasticSearchService
import org.sunbird.common.models.response.Response
import org.sunbird.common.models.util.ProjectUtil
import org.sunbird.common.request.Request
import org.sunbird.common.responsecode.ResponseCode
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
        ((keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(null, keyspace, table, filters, fields)).expects(*,*,*,*).returns(response)
        val result = callActor(getStateReadRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false)))
        assert(null!= result)
    }

    "get Consumption" should "return empty response" in {
        val cassandraOperation = mock[CassandraOperation]
        val response = new Response()
        response.put("response", new java.util.ArrayList[java.util.Map[String, AnyRef]])
        ((keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(null, keyspace, table, filters, fields)).expects(*,*,*,*).returns(response)
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
        ((searchDTO: _root_.org.sunbird.dto.SearchDTO, index: _root_.scala.Predef.String) => esService.search(requestContext)).expects(*,*).returns(concurrent.Future{validBatchData()})
        ((keyspace: _root_.scala.Predef.String, table: _root_.scala.Predef.String, filters: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef], fields: _root_.java.util.List[_root_.scala.Predef.String]) => cassandraOperation.getRecords(null, keyspace, table, filters, fields)).expects(*,*,*,*).returns(response)
        ((keyspaceName: _root_.scala.Predef.String, tableName: _root_.scala.Predef.String, records: _root_.java.util.List[_root_.java.util.Map[_root_.scala.Predef.String, AnyRef]]) => cassandraOperation.batchInsert(null, keyspaceName, tableName, records)).expects(*,*,*)
        ((keyspaceName: _root_.scala.Predef.String, tableName: _root_.scala.Predef.String, request: _root_.java.util.Map[_root_.scala.Predef.String, AnyRef]) => cassandraOperation.upsertRecord(keyspaceName, tableName, request, null)).expects(*,*,*)
        val result = callActor(getStateUpdateRequest(), Props(new ContentConsumptionActor().setCassandraOperation(cassandraOperation, false).setEsService(esService)))
        assert(null!= result)
    }

    "update AssementScore " should "return success on updating the progress" in {
        val cassandraOperation = mock[CassandraOperation]
        val esService = mock[ElasticSearchService]
        ((searchDTO: _root_.org.sunbird.dto.SearchDTO, index: _root_.scala.Predef.String) => esService.search(requestContext, searchDTO, index)).expects(*,*).returns(concurrent.Future{validBatchData()})
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

}
