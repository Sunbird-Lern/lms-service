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

public class UserOrgServiceHttpStatusTest {

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
    public void testServerError() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/88888"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
        }

        wireMockServer.stop();
    }

    @Test
    public void testNotFound() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/88888"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\ntestNotFound");
        }

        wireMockServer.stop();
    }

    @Test
    public void testUnauthorized() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/77777"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("77777", "invalidAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\ntest UnAuthorized");
        }

        wireMockServer.stop();
    }

    @Test
    public void testServerErrorWithMessage() {
        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        configureFor("localhost", 8080);

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/99999"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal Server Error\"}")));

        try {
            userOrgService.getUserById("99999", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\nerror in server");
        }

        wireMockServer.stop();
    }
}
