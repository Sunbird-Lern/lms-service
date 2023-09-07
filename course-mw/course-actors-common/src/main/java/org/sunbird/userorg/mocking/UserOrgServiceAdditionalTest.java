package org.sunbird.userorg.mocking;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

public class UserOrgServiceAdditionalTest {

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
    public void testUserNotFound() {

        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/99999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));

        Map<String, Object> user = userOrgService.getUserById("99999", "someAuthToken");

        assertNull(user);

        wireMockServer.stop();
    }

    @Test
    public void testSuccessfulUserRetrieval() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        String responseBody = "{\"externalId\": \"12345\", \"orgName\": \"OrgName\", \"email\": \"user@example.com\"}";
        stubFor(get(urlEqualTo("/user/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        Map<String, Object> user = userOrgService.getUserById("12345", "someAuthToken");

        assertNotNull(user);
        assertEquals("12345", user.get("externalId"));
        assertEquals("OrgName", user.get("orgName"));
        assertEquals("user@example.com", user.get("email"));

        wireMockServer.stop();
    }

    @Test
    public void testEmptyUserResponse() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Map<String, Object> user = userOrgService.getUserById("12345", "someAuthToken");

        assertNotNull(user);
        assertTrue(user.isEmpty());

        wireMockServer.stop();
    }

    @Test
    public void testInvalidJsonResponse() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("invalid-json")));

        Map<String, Object> user = userOrgService.getUserById("12345", "someAuthToken");

        assertNull(user);

        wireMockServer.stop();
    }

    @Test
    public void testUnauthorizedAccess() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/12345"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("12345", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            
        }

        wireMockServer.stop();
    }



}
    

