package org.sunbird.badge.service;

import java.util.List;
import org.sunbird.badge.model.BadgeClassExtension;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.request.RequestContext;

public interface BadgeClassExtensionService {

  List<BadgeClassExtension> search(
          List<String> issuerList,
          List<String> badgeList,
          String rootOrgId,
          String type,
          String subtype,
          List<String> roles, RequestContext requestContext);

  BadgeClassExtension get(String badgeId, RequestContext requestContext) throws ProjectCommonException;
}
