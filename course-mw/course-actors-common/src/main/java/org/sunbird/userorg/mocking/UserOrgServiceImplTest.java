package org.sunbird.userorg.mocking;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
public class UserOrgServiceImplTest {

    private WireMockServer wireMockServer;

    @Before
    public void setup() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();

        configureFor("localhost", 8080);

        String responseBodyGetUserById = Files.readString(Paths.get("./sampleResponses/userGet.json"));
        String responseBodyGetOrgById = Files.readString(Paths.get("./sampleResponses/orgGet.json"));
        String responseBodySearchUserById = Files.readString(Paths.get("./sampleResponses/useSearch.json"));
        String responseBodySearchOrgById = Files.readString(Paths.get("./sampleResponses/orgSearch.json"));



        // Stub the getUserById API endpoint
        stubFor(get(urlEqualTo("/user/v1/read/13317"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetUserById)));

        // Stub the searchUsersById API endpoint
        stubFor(post(urlEqualTo("/v1/user/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodySearchUserById)));

        // Stub the getOrgById API endpoint
        stubFor(get(urlEqualTo("/org/v1/read/23167"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetOrgById)));

        // Stub the searchOrgById API endpoint
        stubFor(post(urlEqualTo("/v1/user/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodySearchOrgById)));


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

        // Configure the expected URL and response for getUsersByIds
        String responseBodyGetUsersByIds = "[\n" +
                "  {\n" +
                "    \"id\": \"12345\",\n" +
                "    \"name\": \"John Doe\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"67890\",\n" +
                "    \"name\": \"Jane Smith\"\n" +
                "  }\n" +
                "]";

        stubFor(post(urlEqualTo("/users"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetUsersByIds)));

        List<Map<String, Object>> users = userOrgService.getUsersByIds(Arrays.asList("12345", "67890"), "someAuthToken");

        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("12345", users.get(0).get("id"));
        assertEquals("John Doe", users.get(0).get("name"));
        assertEquals("67890", users.get(1).get("id"));
        assertEquals("Jane Smith", users.get(1).get("name"));
    }

    @Test
    public void testGetUserByOrg() {
        UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

        // Configure the expected URL and response for getOrgById
        String responseBodyGetOrgById = "{\n" +
                "  \"id\": \"org123\",\n" +
                "  \"name\": \"Organization Name\",\n" +
                "  \"type\": \"school\",\n" +
                "  \"email\": \"org@example.com\"\n" +
                "}";

        stubFor(get(urlEqualTo("/org/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBodyGetOrgById)));

        Map<String, Object> org = userOrgService.getOrganisationById("12345");

        assertNotNull(org);
        assertEquals("org123", org.get("id"));
        assertEquals("Organization Name", org.get("name"));
        assertEquals("school", org.get("type"));
        assertEquals("org@example.com", org.get("email"));
    }


}
