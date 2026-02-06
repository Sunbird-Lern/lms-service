package util;

import modules.StartModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.auth.verifier.AccessTokenValidator;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.common.PropertiesCache;
import org.sunbird.helper.ServiceFactory;
import play.Application;
import play.Mode;
import play.api.http.MediaRange;
import play.api.mvc.Request;
import play.i18n.Lang;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http;
import play.mvc.Http.Flash;
import play.test.Helpers;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class, PropertiesCache.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class RequestInterceptorTest {

  public Application application;

  private CassandraOperationImpl cassandraOperation;
  private PropertiesCache properties;

  @Before
  public void before() {
    application =
        new GuiceApplicationBuilder()
            .in(new File("path/to/app"))
            .in(Mode.TEST)
            .disable(StartModule.class)
            .build();
    Helpers.start(application);
    cassandraOperation = PowerMockito.mock(CassandraOperationImpl.class);
    PowerMockito.mockStatic(ServiceFactory.class);
    PowerMockito.when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    properties = PowerMockito.mock(PropertiesCache.class);
    PowerMockito.mockStatic(PropertiesCache.class);
    PowerMockito.when(PropertiesCache.getInstance()).thenReturn(properties);
  }

  @Test
  @PrepareForTest({ServiceFactory.class, PropertiesCache.class, AccessTokenValidator.class})
  public void testVerifyRequestDataWithUserAccessTokenWithPrivateRequestPath() {
    PowerMockito.when(properties.getProperty(JsonKey.SSO_PUBLIC_KEY)).thenReturn("somePublicKey");
    PowerMockito.when(properties.getProperty(JsonKey.IS_SSO_ENABLED)).thenReturn("false");
      PowerMockito.mockStatic(AccessTokenValidator.class);
      PowerMockito.when(AccessTokenValidator.verifyUserToken(Mockito.anyString(), Mockito.anyBoolean())).thenReturn("userId");
    Http.Request req = createRequest("user", "/v1/course/batch/search");
    PowerMockito.when(cassandraOperation.getRecordByIdentifier(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.any()))
        .thenReturn(getMockCassandraRecordByIdResponse(JsonKey.USER_ID, "userId"));
    Assert.assertEquals("userId", RequestInterceptor.verifyRequestData(req));
  }

  @Test
  @PrepareForTest({ServiceFactory.class, PropertiesCache.class, AccessTokenValidator.class})
  public void testVerifyRequestDataWithUserAccessTokenWithPublicRequestPath() {
    PowerMockito.when(properties.getProperty(JsonKey.SSO_PUBLIC_KEY)).thenReturn("somePublicKey");
    PowerMockito.when(properties.getProperty(JsonKey.IS_SSO_ENABLED)).thenReturn("false");
    Http.Request req = createRequest("user", "/v1/course/batch/create");
    PowerMockito.mockStatic(AccessTokenValidator.class);
    PowerMockito.when(AccessTokenValidator.verifyUserToken(Mockito.anyString(), Mockito.anyBoolean())).thenReturn("userId");
    PowerMockito.when(cassandraOperation.getRecordByIdentifier(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyList(), Mockito.any()))
        .thenReturn(getMockCassandraRecordByIdResponse(JsonKey.USER_ID, "userId"));
    Assert.assertEquals("userId", RequestInterceptor.verifyRequestData(req));
  }

  @Test
  @PrepareForTest({ServiceFactory.class, PropertiesCache.class})
  public void testVerifyRequestDataWithAuthClientTokenWithPublicRequestPath() {
    PowerMockito.when(properties.getProperty(JsonKey.SSO_PUBLIC_KEY)).thenReturn("somePublicKey");
    PowerMockito.when(properties.getProperty(JsonKey.IS_SSO_ENABLED)).thenReturn("false");
    Http.Request req = createRequest("client", "/v1/course/batch/create");
    PowerMockito.when(cassandraOperation.getRecordsByProperties(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(getMockCassandraRecordByIdResponse(JsonKey.ID, "clientId"));
    Assert.assertEquals("clientId", RequestInterceptor.verifyRequestData(req));
  }

  @Test
  @PrepareForTest({ServiceFactory.class, PropertiesCache.class})
  public void testVerifyRequestDataWithoutUserAccessToken() {
    PowerMockito.when(properties.getProperty(JsonKey.SSO_PUBLIC_KEY)).thenReturn("somePublicKey");
    PowerMockito.when(properties.getProperty(JsonKey.IS_SSO_ENABLED)).thenReturn("false");
    Http.Request req = createRequest("user", "/v1/course/batch/search");
    PowerMockito.when(cassandraOperation.getRecordsByProperties(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(null);
    Assert.assertEquals("Anonymous", RequestInterceptor.verifyRequestData(req));
  }

  private Http.Request createRequest(String token, String path) {
    Http.Request req =
        new Http.Request() {
          @Override
          public Http.RequestBody body() {
            return null;
          }

          @Override
          public Http.Request withBody(Http.RequestBody requestBody) {
            return null;
          }

          @Override
          public Http.Request withAttrs(TypedMap typedMap) {
            return null;
          }

          @Override
          public <A> Http.Request addAttr(TypedKey<A> typedKey, A a) {
            return null;
          }

          @Override
          public Http.Request addAttrs(List<play.libs.typedmap.TypedEntry<?>> entries) {
            return null;
          }

          @Override
          public Http.Request addAttrs(play.libs.typedmap.TypedEntry<?> e1) {
            return null;
          }

          @Override
          public Http.Request addAttrs(play.libs.typedmap.TypedEntry<?> e1, 
                                       play.libs.typedmap.TypedEntry<?> e2) {
            return null;
          }

          @Override
          public Http.Request addAttrs(play.libs.typedmap.TypedEntry<?> e1, 
                                       play.libs.typedmap.TypedEntry<?> e2, 
                                       play.libs.typedmap.TypedEntry<?> e3) {
            return null;
          }

          // Varargs version is a default method in the interface
          public Http.Request addAttrs(play.libs.typedmap.TypedEntry<?>... entries) {
            return null;
          }

          @Override
          public Http.Request removeAttr(TypedKey<?> typedKey) {
            return null;
          }

          @Override
          public Request<Http.RequestBody> asScala() {
            return null;
          }

          @Override
          public String uri() {
            return null;
          }

          @Override
          public String method() {
            return null;
          }

          @Override
          public String version() {
            return null;
          }

          @Override
          public String remoteAddress() {
            return null;
          }

          @Override
          public boolean secure() {
            return false;
          }

          @Override
          public TypedMap attrs() {
            return null;
          }

          @Override
          public String host() {
            return null;
          }

          @Override
          public String path() {
            return (path);
          }

          @Override
          public List<Lang> acceptLanguages() {
            return null;
          }

          @Override
          public List<MediaRange> acceptedTypes() {
            return null;
          }

          @Override
          public boolean accepts(String s) {
            return false;
          }

          @Override
          public Map<String, String[]> queryString() {
            return null;
          }

          @Override
          public Optional<String> queryString(String s) {
            String value = getQueryString(s);
            return value != null ? Optional.of(value) : Optional.empty();
          }

          @Override
          public String getQueryString(String s) {
            return null;
          }

          @Override
          public Http.Cookies cookies() {
            return null;
          }

          @Override
          public Optional<Http.Cookie> cookie(String s) {
            return Optional.empty();
          }

          @Override
          public Optional<Http.Cookie> getCookie(String s) {
            return cookie(s);
          }

          @Override
          public Http.Headers getHeaders() {
            Map<String, List<String>> headerMap = new HashMap<>();
            Http.Headers headers = new Http.Headers(headerMap);
            if (token.equals("user"))
              headers.addHeader("x-authenticated-user-token", "userAccessToken");
            else {
              headers.addHeader("x-authenticated-client-token", "clientAccessToken");
              headers.addHeader("x-authenticated-client-id", "authorized");
            }
            return headers;
          }

          @Override
          public Http.Headers headers() {
            return getHeaders();
          }

          @Override
          public boolean hasBody() {
            return false;
          }

          @Override
          public Optional<String> contentType() {
            return Optional.empty();
          }

          @Override
          public Optional<String> charset() {
            return Optional.empty();
          }

          @Override
          public Optional<List<X509Certificate>> clientCertificateChain() {
            return Optional.empty();
          }

          public Flash flash() {
            return new Http.Flash(new HashMap<String, String>());
          }
        };
    return req;
  }

  private Response getMockCassandraRecordByIdResponse(String type, String id) {

    Response response = new Response();
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> authResponseMap = new HashMap<>();
    authResponseMap.put(type, id);
    list.add(authResponseMap);
    response.put(JsonKey.RESPONSE, list);
    return response;
  }
}
