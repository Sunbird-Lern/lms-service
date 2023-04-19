package org.sunbird.learner.actors.qrcode;


import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.RestUtil;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.qrcodedownload.QRCodeDownloadManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        ProjectUtil.class,
        HttpUtil.class,
        Unirest.class,
        RestUtil.class
})
public class QRCodeDownloadManagerTest {

    private QRCodeDownloadManager downloadManager = new QRCodeDownloadManager();

    @Before
    public void beforeEachTest() throws Exception {
        PowerMockito.mockStatic(ProjectUtil.class);
        PowerMockito.mockStatic(HttpUtil.class);
        PowerMockito.mockStatic(Unirest.class);
        PowerMockito.mockStatic(RestUtil.class);
        PowerMockito.when(ProjectUtil.getConfigValue(Mockito.anyString())).thenReturn("");
        String qrImageListAPIResponse = "{\"id\": \"sunbird.dialcode.images.list\",\"ver\": \"3.0\",\"ts\": \"2023-02-01T12:16:52Z+05:30\",\"params\": {\"resmsgid\": \"505bce18-1feb-44c3-91c3-07b8324de4f9\",\"msgid\": null,\"err\": null,\"status\": \"successful\",\"errmsg\": null},\"responseCode\": \"OK\",\"result\": {\"count\": 1, \"dialcodes\": [{ \"dialcode_index\": 14711964,\"identifier\": \"F6A5C7\",\"imageUrl\": \"https://sunbirddevbbpublic.blob.core.windows.net/dial/01309282781705830427//4_F6A5C7.png\", \"channel\": \"01309282781705830427\",\"batchcode\": \"do_21373837923890790415\",\"generated_on\": \"2023-02-22T06:44:48.449+0000\",\"objectType\": \"DialCode\",\"status\": \"Draft\"}]}}";
        PowerMockito.when(HttpUtil.sendPostRequest(Mockito.anyString(),Mockito.anyString(),Mockito.anyMap())).thenReturn(qrImageListAPIResponse);
    }

    private void mockSearchResponse() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.post(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"api.v1.search\",\n" +
                "    \"ver\": \"1.0\",\n" +
                "    \"ts\": \"2023-02-06T09:42:48.238Z\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"9d966ce0-a602-11ed-9900-a3e42864e097\",\n" +
                "        \"msgid\": \"9d9422f0-a602-11ed-8434-d7238806f152\",\n" +
                "        \"status\": \"successful\",\n" +
                "        \"err\": null,\n" +
                "        \"errmsg\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"count\": 5216,\n" +
                "        \"content\": [\n" +
                "            {\n" +
                "                \"identifier\": \"do_2137252043365253121125\",\n" +
                "                \"dialcodes\": [\n" +
                "                    \"C1Q6A1\"\n" +
                "                ],\n" +
                "                \"name\": \"Course_V\",\n" +
                "                \"objectType\": \"Content\"\n" +
                "            },\n" +
                "            {\n" +
                "                \"identifier\": \"do_2137251125051883521118\",\n" +
                "                \"dialcodes\": [\n" +
                "                    \"H4G4G1\"\n" +
                "                ],\n" +
                "                \"name\": \"CourseAT1567Sanford\",\n" +
                "                \"objectType\": \"Content\"\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}");
    }

    @Test
    public void getContentSearchResponseTest() throws Exception {
        mockSearchResponse();
        Request request = new Request();
        Map<String, Object> searchCoursesResponse = downloadManager.searchCourses(request.getRequestContext(), new HashMap<String, Object>(), new HashMap<String, String>() );
        Assert.assertTrue((Integer) searchCoursesResponse.get("count") > 0);
    }

    @Test
    public void getQRImagesFromListAPITest() {
        Set<String> dialcodes = new HashSet();
        dialcodes.add("Q1I5I3");
        dialcodes.add("A5Z7I3");
        String channel = "sunbird";
        Map<String, String> qrCodeImageURLObjs = downloadManager.getQRCodeImageURLs(dialcodes, channel);
        Assert.assertTrue(qrCodeImageURLObjs.size()>0);

    }



}
