package org.sunbird.userorg;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UserOrgServiceImplTest {

    private WireMockServer wireMockServer;

    @Before
    public void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();

        configureFor("localhost", 8080);

        String responseBodyGetUserById = "{\n" +
                "  \"request\": {\n" +
                "    \"orgName\": \"Org Name\",\n" +
                "    \"channel\": \"Channel\",\n" +
                "    \"description\": \"Description\",\n" +
                "    \"externalId\": \"ExtId\",\n" +
                "    \"email\": \"info@org.org\",\n" +
                "    \"isSSOEnabled\": true,\n" +
                "    \"organisationType\": \"school\",\n" +
                "    \"orgLocation\": [\n" +
                "      {\n" +
                "        \"id\": \"9541f516-4c01-4322-aa06-4062687a0ce5\",\n" +
                "        \"type\": \"block\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"6dd69f1c-ba40-4b3b-8981-4fb6813c5e71\",\n" +
                "        \"type\": \"district\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"e9207c22-41cf-4a0d-81fb-1fbe3e34ae24\",\n" +
                "        \"type\": \"cluster\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"ccc7be29-8e40-4d0a-915b-26ec9228ac4a\",\n" +
                "        \"type\": \"state\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"isTenant\": true\n" +
                "  }\n" +
                "}";

        String responseBodyGetOrgById = "ORG-response";

        // Stub the getUserById API endpoint
        stubFor(get(urlEqualTo("/user/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetUserById)));

        // Stub the getUsersByIds API endpoint
        stubFor(post(urlEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{ \"id\": \"12345\", \"name\": \"John Doe\" }]")));

        // Stub the getOrgById API endpoint
        stubFor(get(urlEqualTo("/org/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetOrgById)));

    }

    @After
    public void tearDown() {
        wireMockServer.stop();
    }

    @Test
    public void testGetUserById() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
        Map<String, Object> user = userOrgService.getUserById("12345", "someAuthToken");

        assertNotNull(user);
        assertEquals("12345", user.get("externalId"));
        assertEquals("Org Name", user.get("orgName"));
        assertEquals("school", user.get("organisationType"));
        assertEquals("info@org.org", user.get("email"));

    }


    @Test
    public void testGetUsersByIds() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
        List<Map<String, Object>> users = userOrgService.getUsersByIds(Arrays.asList("12345", "67890"), "someAuthToken");


        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("12345", users.get(0).get("id"));
        assertEquals("John Doe", users.get(0).get("name"));
    }

    @Test
    public void testGetUserByOrg() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();
        Map<String, Object> org = userOrgService.getOrganisationById("12345");


        assertNotNull(org);

    }

}
