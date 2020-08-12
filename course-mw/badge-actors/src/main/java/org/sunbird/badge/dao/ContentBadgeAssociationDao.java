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
  public Response insertBadgeAssociation(List<Map<String, Object>> contentInfoList, RequestContext requestContext);

  /*
   * This method will update content-badge association details.
   *
   * @param Map<String, Object> contentBadgeDetails
   * @return Response
   */
  public Response updateBadgeAssociation(Map<String, Object> updateMap, RequestContext requestContext);

  public void createDataToES(Map<String, Object> badgeMap);

  public void updateDataToES(Map<String, Object> badgeMap);
}
