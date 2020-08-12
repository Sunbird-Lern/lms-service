package org.sunbird.badge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.sunbird.badge.model.BadgeClassExtension;
import org.sunbird.badge.service.BadgeClassExtensionService;
import org.sunbird.badge.service.BadgingService;
import org.sunbird.badge.util.BadgingUtil;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.HttpUtilResponse;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.BadgingJsonKey;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;

/** @author Manzarul */
public class BadgrServiceImpl implements BadgingService {
  private BadgeClassExtensionService badgeClassExtensionService;
  private ObjectMapper mapper = new ObjectMapper();
  private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  public static Map<String, String> headerMap = new HashMap<>();

  static {
    String header = System.getenv(JsonKey.EKSTEP_AUTHORIZATION);
    if (StringUtils.isBlank(header)) {
      header = PropertiesCache.getInstance().readProperty(JsonKey.EKSTEP_AUTHORIZATION);
    } else {
      header = JsonKey.BEARER + header;
    }
    headerMap.put(JsonKey.AUTHORIZATION, header);
    headerMap.put("Content-Type", "application/json");
  }

  public BadgrServiceImpl() {
    this.badgeClassExtensionService = new BadgeClassExtensionServiceImpl();
  }

  @Override
  public Response searchBadgeClass(Request request) throws ProjectCommonException {
    Response response = new Response();

    Map<String, Object> filtersMap =
        (Map<String, Object>) request.getRequest().get(JsonKey.FILTERS);

    List<String> issuerList = (List<String>) filtersMap.get(BadgingJsonKey.ISSUER_LIST);
    List<String> badgeList = (List<String>) filtersMap.get(BadgingJsonKey.BADGE_LIST);
    String rootOrgId = (String) filtersMap.get(JsonKey.ROOT_ORG_ID);
    String type = (String) filtersMap.get(JsonKey.TYPE);
    String subtype = (String) filtersMap.get(JsonKey.SUBTYPE);
    List<String> allowedRoles = (List<String>) filtersMap.get(JsonKey.ROLES);

    if (type != null) {
      type = type.toLowerCase();
    }

    if (subtype != null) {
      subtype = subtype.toLowerCase();
    }

    List<BadgeClassExtension> badgeClassExtList =
        badgeClassExtensionService.search(
            issuerList, badgeList, rootOrgId, type, subtype, allowedRoles, request.getRequestContext());
    List<String> filteredIssuerList =
        badgeClassExtList
            .stream()
            .map(badge -> badge.getIssuerId())
            .distinct()
            .collect(Collectors.toList());

    List<Object> badges = new ArrayList<>();

    for (String issuerSlug : filteredIssuerList) {
      badges.addAll(listBadgeClassForIssuer(issuerSlug, badgeClassExtList));
    }

    response.put(BadgingJsonKey.BADGES, badges);

    return response;
  }

  private List<Object> listBadgeClassForIssuer(
      String issuerSlug, List<BadgeClassExtension> badgeClassExtensionList)
      throws ProjectCommonException {
    List<Object> filteredBadges = new ArrayList<>();

    try {
      Map<String, String> headers = BadgingUtil.getBadgrHeaders();
      String badgrUrl = BadgingUtil.getBadgeClassUrl(issuerSlug);

      HttpUtilResponse httpUtilResponse = HttpUtil.doGetRequest(badgrUrl, headers);
      String badgrResponseStr = httpUtilResponse.getBody();

      BadgingUtil.throwBadgeClassExceptionOnErrorStatus(
          httpUtilResponse.getStatusCode(), badgrResponseStr, BadgingJsonKey.BADGE_CLASS);

      List<Map<String, Object>> badges = mapper.readValue(badgrResponseStr, ArrayList.class);

      for (Map<String, Object> badge : badges) {
        BadgeClassExtension matchedBadgeClassExt =
            badgeClassExtensionList
                .stream()
                .filter(x -> x.getBadgeId().equals(badge.get(BadgingJsonKey.SLUG)))
                .findFirst()
                .orElse(null);

        if (matchedBadgeClassExt != null) {
          Map<String, Object> mappedBadge = new HashMap<>();
          BadgingUtil.prepareBadgeClassResponse(badge, matchedBadgeClassExt, mappedBadge);
          filteredBadges.add(mappedBadge);
        }
      }
    } catch (IOException e) {
      BadgingUtil.throwBadgeClassExceptionOnErrorStatus(
          ResponseCode.SERVER_ERROR.getResponseCode(), e.getMessage(), BadgingJsonKey.BADGE_CLASS);
    }

    return filteredBadges;
  }
}
