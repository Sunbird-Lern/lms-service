package org.sunbird;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.fail;

public class UserOrgServiceHttpStatusTest {

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
    public void testServerError() {

        System.out.println("\n\n\nstarting ServerTestError\n\n");

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/88888"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\nError in Test Server\n\n");
        }

    }

    @Test
    public void testNotFound() {

        System.out.println("\n\n\nstarting NotFound\n\n");

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/88888"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("88888", "someAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\ntest Not Found\n\n");
        }

    }

    @Test
    public void testUnauthorized() {

        System.out.println("\n\n\nstarting unauthorized\n");

        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        stubFor(get(urlEqualTo("/user/77777"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")));

        try {
            userOrgService.getUserById("77777", "invalidAuthToken");
            fail("Expected an exception to be thrown");
        } catch (Exception e) {
            System.out.println("\n\n\ntest UnAuthorized\n\n");
        }

    }

    @Test
    public void Error404() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/v1/user/search/23382";

        URL url = new URL(wireMockUrl + endPoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        System.out.println("\n\nResponse Code:\n" + responseCode + "\n\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder responseBody = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            responseBody.append(line);
        }
        reader.close();
        System.out.println("\n\nResponse Body:\n" + responseBody.toString() + "\n\n");

        connection.disconnect();
    }

    @Test
    public void Error505() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/user/v1/read/18873";

        // Make a GET request to the WireMock server
        URL url = new URL(wireMockUrl + endPoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Get the response code
        int responseCode = connection.getResponseCode();
        System.out.println("\n\nResponse Code:\n" + responseCode + "\n\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder responseBody = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            responseBody.append(line);
        }
        reader.close();
        System.out.println("\n\nResponse Body:\n" + responseBody.toString() + "\n\n");

        connection.disconnect();
    }

}
