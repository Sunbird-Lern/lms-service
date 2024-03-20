package util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.auth.verifier.AccessTokenValidator;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.HeaderParam;
import play.mvc.Http;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request interceptor responsible to authenticated HTTP requests
 *
 * @author Amit Kumar
 */
public class RequestInterceptor {

  public static List<String> restrictedUriList = null;
  private static ConcurrentHashMap<String, Short> apiHeaderIgnoreMap = new ConcurrentHashMap<>();
  private static LoggerUtil logger =  new LoggerUtil(RequestInterceptor.class);

  private RequestInterceptor() {}

  static {
    restrictedUriList = new ArrayList<>();
    restrictedUriList.add("/v1/content/state/update");

    short var = 1;
    apiHeaderIgnoreMap.put("/service/health", var);
    apiHeaderIgnoreMap.put("/v1/page/assemble", var);
    apiHeaderIgnoreMap.put("/v1/dial/assemble", var);
    apiHeaderIgnoreMap.put("/health", var);
    apiHeaderIgnoreMap.put("/v1/data/sync", var);
    apiHeaderIgnoreMap.put("/v1/content/link", var);
    apiHeaderIgnoreMap.put("/v1/content/unlink", var);
    apiHeaderIgnoreMap.put("/v1/content/link/search", var);
    apiHeaderIgnoreMap.put("/v1/course/batch/search", var);
    apiHeaderIgnoreMap.put("/v1/cache/clear", var);
    apiHeaderIgnoreMap.put("/private/v1/course/batch/create", var);
    apiHeaderIgnoreMap.put("/v1/course/create", var);
    apiHeaderIgnoreMap.put("/v2/user/courses/list", var);
    apiHeaderIgnoreMap.put("/v1/collection/summary", var);
  }

  /**
   * Authenticates given HTTP request context
   *
   * @param request HTTP play request
   * @return User or Client ID for authenticated request. For unauthenticated requests, UNAUTHORIZED
   *     is returned
   */
  public static String verifyRequestData(Http.Request request) {
    String clientId = JsonKey.UNAUTHORIZED;
    Optional<String> accessToken = request.header(HeaderParam.X_Authenticated_User_Token.getName());
    Optional<String> authClientToken =
        request.header(HeaderParam.X_Authenticated_Client_Token.getName());
    Optional<String> authClientId = request.header(HeaderParam.X_Authenticated_Client_Id.getName());
    if (!isRequestInExcludeList(request.path()) && !isRequestPrivate(request.path())) {
      if (accessToken.isPresent()) {
        // This is to handle Mobile App expired token for content state update API.
        if (StringUtils.contains(request.path(), "v1/content/state/update")) {
          clientId = AccessTokenValidator.verifyUserToken(accessToken.get(), false);
        } else {
          clientId = AccessTokenValidator.verifyUserToken(accessToken.get(), true);
        }
      } else if (authClientToken.isPresent() && authClientId.isPresent()) {
        clientId =
            AuthenticationHelper.verifyClientAccessToken(authClientId.get(), authClientToken.get());
        if (!JsonKey.UNAUTHORIZED.equals(clientId)) {
          request = request.addAttr(Attrs.AUTH_WITH_MASTER_KEY, Boolean.toString(true));
        }
      }
      return clientId;
    } else {
      if (accessToken.isPresent()) {
        String clientAccessTokenId = null;
        try {
          // This is to handle Mobile App expired token for content state update API.
          if (StringUtils.contains(request.path(), "v1/content/state/update")) {
            clientAccessTokenId = AccessTokenValidator.verifyUserToken(accessToken.get(), false);
          } else {
            clientAccessTokenId = AccessTokenValidator.verifyUserToken(accessToken.get(), true);
          }
          if (JsonKey.UNAUTHORIZED.equalsIgnoreCase(clientAccessTokenId)) {
            clientAccessTokenId = null;
          }
        } catch (Exception ex) {
          logger.error(null, ex.getMessage(), ex);
          clientAccessTokenId = null;
        }
        return StringUtils.isNotBlank(clientAccessTokenId)
            ? clientAccessTokenId
            : JsonKey.ANONYMOUS;
      }
      return JsonKey.ANONYMOUS;
    }
  }

  private static boolean isRequestPrivate(String path) {
    return path.contains(JsonKey.PRIVATE);
  }

  /**
   * Checks if request URL is in excluded (i.e. public) URL list or not
   *
   * @param requestUrl Request URL
   * @return True if URL is in excluded (public) URLs. Otherwise, returns false
   */
  public static boolean isRequestInExcludeList(String requestUrl) {
    boolean resp = false;
    if (!StringUtils.isBlank(requestUrl)) {
      if (apiHeaderIgnoreMap.containsKey(requestUrl)) {
        resp = true;
      } else {
        String[] splitPath = requestUrl.split("[/]");
        String urlWithoutPathParam = removeLastValue(splitPath);
        if (apiHeaderIgnoreMap.containsKey(urlWithoutPathParam)) {
          resp = true;
        }
      }
    }
    return resp;
  }

  /**
   * Returns URL without path and query parameters.
   *
   * @param splitPath URL path split on slash (i.e. /)
   * @return URL without path and query parameters
   */
  private static String removeLastValue(String splitPath[]) {

    StringBuilder builder = new StringBuilder();
    if (splitPath != null && splitPath.length > 0) {
      for (int i = 1; i < splitPath.length - 1; i++) {
        builder.append("/" + splitPath[i]);
      }
    }
    return builder.toString();
  }
}
