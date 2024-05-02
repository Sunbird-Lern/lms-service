package org.sunbird.enrolments

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.actor.base.BaseActor
import org.sunbird.common.ElasticSearchHelper
import org.sunbird.common.factory.EsClientFactory
import org.sunbird.common.inf.ElasticSearchService
import org.sunbird.common.models.util.{JsonKey, ProjectUtil}
import org.sunbird.common.request.RequestContext
import org.sunbird.dto.SearchDTO

import java.util

abstract class BaseEnrolmentActor extends BaseActor {

    var esService = EsClientFactory.getInstance()
    
    def getBatches(requestContext: RequestContext, batchIds: java.util.List[String], requestedFields: java.util.List[String]): java.util.List[java.util.Map[String, AnyRef]] = {
        val dto = new SearchDTO
        dto.setLimit(batchIds.size())
        dto.getAdditionalProperties().put(JsonKey.FILTERS, new java.util.HashMap[String, AnyRef](){{ put(JsonKey.BATCH_ID, batchIds)}})
        if(CollectionUtils.isNotEmpty(requestedFields))
            dto.setFields(requestedFields)
        val future = esService.search(requestContext, dto, ProjectUtil.EsType.courseBatch.getTypeName)
        val response = ElasticSearchHelper.getResponseFromFuture(future).asInstanceOf[java.util.Map[String, AnyRef]]
        response.getOrDefault(JsonKey.CONTENT, new java.util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    }
    
    def setEsService(es: ElasticSearchService) = {
        esService = es
        this
    }

}
