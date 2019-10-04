package controllers;


import modules.OnRequestHandler;
import modules.StartModule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.services.sso.SSOManager;
import org.sunbird.services.sso.SSOServiceFactory;
import play.Application;
import play.Mode;
import play.api.http.MediaRange;
import play.api.mvc.Request;
import play.i18n.Lang;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.typedmap.TypedKey;
import play.libs.typedmap.TypedMap;
import play.mvc.Http;
import play.test.Helpers;
import util.RequestInterceptor;
import play.core.j.RequestHeaderImpl;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.*;


import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SSOServiceFactory.class,ServiceFactory.class})
@SuppressStaticInitializationFor( {"util.AuthenticationHelper","util.Global"})
@PowerMockIgnore("javax.management.*")
@Ignore
public class RequestInterceptorTest {

    public static Application application;
    private static SSOManager ssoManager;
    private static CassandraOperationImpl cassandraOperation;

    @Before
    public void before() {
        application =
                new GuiceApplicationBuilder()
                        .in(new File("path/to/app"))
                        .in(Mode.TEST)
                        .disable(StartModule.class)
                        .build();

        Helpers.start(application);
        ssoManager = mock(SSOManager.class);
        PowerMockito.mockStatic(SSOServiceFactory.class);
        when(SSOServiceFactory.getInstance()).thenReturn(ssoManager);
        cassandraOperation=mock(CassandraOperationImpl.class);
        PowerMockito.mockStatic(ServiceFactory.class);
        when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    }

    @Test
    public void testVerifyRequestDataWithUserAccessTokenWithPrivateRequestPath()
    {
        Http.Request req=createRequest("user","/v1/course/batch/search");
        when(cassandraOperation.getRecordById(Mockito.anyString(),Mockito.anyString(),Mockito.anyString())).thenReturn(getMockCassandraRecordByIdResponse(JsonKey.USER_ID,"userId"));
        Assert.assertEquals("userId", RequestInterceptor.verifyRequestData(req));
    }


    @Test
    public void testVerifyRequestDataWithUserAccessTokenWithPublicRequestPath()
    {
        Http.Request req=createRequest("user","/course/v1/batch/search");
        when(cassandraOperation.getRecordById(Mockito.anyString(),Mockito.anyString(),Mockito.anyString())).thenReturn(getMockCassandraRecordByIdResponse(JsonKey.USER_ID,"userId"));
        Assert.assertEquals("userId", RequestInterceptor.verifyRequestData(req));
    }


    @Test
    public void testVerifyRequestDataWithAuthClientTokenWithPublicRequestPath()
    {
        Http.Request req=createRequest("client","/course/v1/batch/search");
        when(cassandraOperation.getRecordsByProperties(Mockito.anyString(),Mockito.anyString(),Mockito.anyMap())).thenReturn(getMockCassandraRecordByIdResponse(JsonKey.ID,"clientId"));
        Assert.assertEquals("userId", RequestInterceptor.verifyRequestData(req));
    }

    @Test
    public void testVerifyRequestDataWithoutUserAccessToken()
    {
        Http.Request req=createRequest("user","/v1/course/batch/search");
        when(cassandraOperation.getRecordsByProperties(Mockito.anyString(),Mockito.anyString(),Mockito.anyMap())).thenReturn(null);
        Assert.assertEquals("Anonymous", RequestInterceptor.verifyRequestData(req));
    }

    private Http.Request createRequest(String token,String path) {
        Http.Request req = new Http.Request() {
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
            public String getQueryString(String s) {
                return null;
            }

            @Override
            public Http.Cookies cookies() {
                return null;
            }

            @Override
            public Http.Cookie cookie(String s) {
                return null;
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
        };
    return  req;
    }

    private Response getMockCassandraRecordByIdResponse(String type,String id) {

        Response response = new Response();
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> authResponseMap = new HashMap<>();
        authResponseMap.put(type,id);
        list.add(authResponseMap);
        response.put(JsonKey.RESPONSE, list);
        return response;
    }
}
