package org.sunbird.userorg.mocking;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class UserOrgServiceExceptionHandlingTest {

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
    public void testExceptionHandling() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/77777"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try {
            userOrgService.getUserById("77777", null);
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
        }
    }

    @Test
    public void testNullInput() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        try {
            userOrgService.getUserById(null, null);
            fail("Expected an exception to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid input parameters", e.getMessage());
        }
    }

    @Test
    public void testRequestTimeout() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/66666"))
                .willReturn(aResponse()
                        .withFixedDelay(2000) 
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try {
            userOrgService.getUserById("66666", "someAuthToken");
            fail("Expected a timeout exception to be thrown");
        } catch (TimeoutException e) {
        }
    }

    @Test
    public void testNetworkError() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/77777"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        try {
            userOrgService.getUserById("77777", "someAuthToken");
            fail("Expected a network error exception to be thrown");
        } catch (NetworkErrorException e) {
        }
    }

    @Test
    public void testOutOfMemoryError() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/88888"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("".repeat(1024 * 1024 * 100)))); 

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an out-of-memory error exception to be thrown");
        } catch (OutOfMemoryError e) {
        }
    }
}
