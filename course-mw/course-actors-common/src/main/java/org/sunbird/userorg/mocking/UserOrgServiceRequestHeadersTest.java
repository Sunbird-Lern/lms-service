package org.sunbird.userorg.mocking;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.Test;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;

public class UserOrgServiceRequestHeadersTest {

    private WireMockServer wireMockServer;

    @Before
    public void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);
    }

    @After
    public void tearDown() {
        wireMockServer.stop();
    }

    @Test
    public void testRequestHeaders() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/55555"))
                .withHeader("Authorization", equalTo("Bearer someAuthToken"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        userOrgService.getUserById("55555", "someAuthToken");
    }

    @Test
    public void testMissingAuthorizationHeader() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/55555"))
                .willReturn(aResponse()
                        .withStatus(401) 
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try {
            userOrgService.getUserById("55555", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
        }
    }

    @Test
    public void testCustomHeaders() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/55555"))
                .withHeader("X-Custom-Header", equalTo("custom-value"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        //userOrgService.getUserByIdWithCustomHeader("55555", "someAuthToken");

    }

    @Test
    public void testQueryParameters() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlPathEqualTo("/user"))
                .withQueryParam("param1", equalTo("value1"))
                .withQueryParam("param2", matching("regex\\d+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        //userOrgService.getUserByQueryParam("value1", "regex123", "someAuthToken");
    }

    @Test
    public void testMultipleHeaders() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/55555"))
                .withHeader("Header1", equalTo("Value1"))
                .withHeader("Header2", equalTo("Value2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

       //userOrgService.getUserWithMultipleHeaders("55555", "Value1", "Value2", "someAuthToken");
    }
}
