package org.sunbird;

import com.github.tomakehurst.wiremock.WireMockServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.*;


public class WireMockTest {
    private static WireMockServer wireMockServer;

    @BeforeClass
    public static void setUp() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @Test
    public void testGetUser() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/user/v1/read/13317";

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
    public void testGetOrg() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/user/v1/org/3995";

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
    public void testOrgSearch() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/v1/org/search/9871";

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

    @Test
    public void testUserSearch() throws IOException {
        String wireMockUrl = "http://localhost:8080";
        String endPoint = "/v1/user/search/5522";

        URL url = new URL(wireMockUrl + endPoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        System.out.println("\n\nResponse Code:\n" + responseCode + "\n\n");



        // Read and print the response body
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder responseBody = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            responseBody.append(line);
        }
        reader.close();
        System.out.println("\n\nResponse Body:\n" + responseBody.toString() + "\n\n");

        // Close the connection
        connection.disconnect();
    }

    @AfterClass
    public static void tearDown() {
        wireMockServer.stop();
    }
}
