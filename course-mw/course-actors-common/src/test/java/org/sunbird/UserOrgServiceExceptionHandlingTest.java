package org.sunbird;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.*;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.sunbird.common.models.util.HttpUtil.logger;

public class UserOrgServiceExceptionHandlingTest {

    private static WireMockServer wireMockServer;

    @BeforeClass
    public static void setUp() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }
    @AfterClass
    public static void tearDown() {
        wireMockServer.stop();
    }

    @Test
    public void testExceptionHandling() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/v1/user/search/78199"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try {
            userOrgService.getUserById("77777", null);
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            assertEquals("Invalid input parameters", e.getMessage());
            System.out.println("An exception occurred in testExceptionHandling: " + e.getMessage());
        }
    }

    @Test
    public void testNullInput() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        System.out.println("\n\n\ntesting Null Input\n");

        try {
            userOrgService.getUserById(null, null);
            fail("Expected an exception to be thrown due to null input");
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid input parameters", e.getMessage());
            System.out.println("An exception occurred in testNullInput: " + e.getMessage());
        }
    }

    @Test
    public void testRequestTimeout() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        System.out.println("\n\n\ntesting Request TimeOut\n");

        stubFor(get(urlEqualTo("/v1/user/search/99234"))
                .willReturn(aResponse()
                        .withFixedDelay(2000)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try {
            userOrgService.getUserById("66666", "someAuthToken");
            fail("Expected a timeout exception to be thrown");
        } catch (Exception e) {
            System.out.println("A timeout exception occurred in testRequestTimeout: " + e.getMessage());
        }
    }

    @Test
    public void testNetworkError() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        System.out.println("\n\n\ntesting Network Error\n");

        stubFor(get(urlEqualTo("/v1/user/search/62216"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        try {
            userOrgService.getUserById("77777", "someAuthToken");
            fail("Expected a network error exception to be thrown");
        } catch (Exception e) {
            System.out.println("A network error exception occurred in testNetworkError: " + e.getMessage());
        }
    }

    @Test
    public void testOutOfMemoryError() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
        System.out.println("\n\n\ntesting Out Of Memory\n");

        stubFor(get(urlEqualTo("/v1/user/search/43386"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("".repeat(1024 * 1024 * 100))));

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an out-of-memory error exception to be thrown");
        } catch (OutOfMemoryError e) {
            System.out.println("An out-of-memory error occurred in testOutOfMemoryError");
        }
    }

}
