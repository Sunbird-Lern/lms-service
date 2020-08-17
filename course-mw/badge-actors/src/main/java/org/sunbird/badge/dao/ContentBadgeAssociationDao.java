package org.sunbird.badge.dao;

import java.util.List;
import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;

public interface ContentBadgeAssociationDao {

  /*
   * This method will insert new badge association list with content.
   *
   * @param Map<String, Object> contentBadgeDetails
   * @return Response
   */
  public Response insertBadgeAssociation(RequestContext requestContext, List<Map<String, Object>> contentInfoList);

  /*
   * This method will update content-badge association details.
   *
   * @param Map<String, Object> contentBadgeDetails
   * @return Response
   */
  public Response updateBadgeAssociation(RequestContext requestContext, Map<String, Object> updateMap);

  public void createDataToES(RequestContext requestContext, Map<String, Object> badgeMap);

  public void updateDataToES(RequestContext requestContext, Map<String, Object> badgeMap);
}
