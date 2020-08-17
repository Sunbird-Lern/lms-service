package org.sunbird.badge.dao.impl;

import java.util.List;
import java.util.Map;
import org.sunbird.badge.dao.ContentBadgeAssociationDao;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;

public class ContentBadgeAssociationDaoImpl implements ContentBadgeAssociationDao {

  private static final String KEYSPACE = "sunbird" + "";
  private static final String TABLE_NAME = "content_badge_association";
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);
  private LoggerUtil logger = new LoggerUtil(ContentBadgeAssociationDaoImpl.class);

  @Override
  public Response insertBadgeAssociation(RequestContext requestContext, List<Map<String, Object>> contentInfo) {
    return cassandraOperation.batchInsert(requestContext, KEYSPACE, TABLE_NAME, contentInfo);
  }

  @Override
  public Response updateBadgeAssociation(RequestContext requestContext, Map<String, Object> updateMap) {
    return cassandraOperation.updateRecord(requestContext, KEYSPACE, TABLE_NAME, updateMap);
  }

  @Override
  public void createDataToES(RequestContext requestContext, Map<String, Object> badgeMap) {
    esUtil.save(requestContext, 
        ProjectUtil.EsType.badgeassociations.getTypeName(),
        (String) badgeMap.get(JsonKey.ID),
        badgeMap);
  }

  @Override
  public void updateDataToES(RequestContext requestContext, Map<String, Object> badgeMap) {
    logger.info(requestContext, "ContentBadgeAssociationDaoImpl:updateDataToES: Updating data to ES for associationId: "
            + (String) badgeMap.get(JsonKey.ID));
    try {
      esUtil.update(requestContext, 
          ProjectUtil.EsType.badgeassociations.getTypeName(),
          (String) badgeMap.get(JsonKey.ID),
          badgeMap);
    } catch (Exception e) {
      logger.error(requestContext, "ContentBadgeAssociationDaoImpl:updateDataToES: Exception occured while Updating data to ES for associationId: "
              + (String) badgeMap.get(JsonKey.ID)
              + " with exception "
              + e.getMessage(), e);
    }
  }
}
