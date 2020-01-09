package org.sunbird.badge.service;

import java.io.IOException;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.Request;

/**
 * This interface will have all the methods required for badging framework. any new method required
 * for badging need to be added here.
 *
 * @author Manzarul
 */
public interface BadgingService {

  /**
   * This method will provide list of badge class either for one issuer or multiple or all , this
   * will depends on requested param.
   *
   * @param request Request
   * @exception IOException
   * @return Response
   */
  public Response searchBadgeClass(Request request) throws ProjectCommonException;
}
