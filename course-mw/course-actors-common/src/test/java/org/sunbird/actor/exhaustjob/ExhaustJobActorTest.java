package org.sunbird.actor.exhaustjob;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.application.test.SunbirdApplicationActorTest;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;

import java.util.HashMap;
import java.util.Map;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({
        "javax.management.*",
        "javax.net.ssl.*",
        "javax.security.*","javax.crypto.*",
        "jdk.internal.reflect.*",
        "jdk.internal.util.*"
})
@PrepareForTest({
        Unirest.class
})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExhaustJobActorTest extends SunbirdApplicationActorTest {
    @Before
    public void setUp() {
        init(ExhaustJobActor.class);
        PowerMockito.mockStatic(Unirest.class);
    }

    @Test
    public void testSubmitJobRequestSuccess() throws Exception{
        mockJobSubmitResponse();
        Response response = executeInTenSeconds(createJobSubmitRequest(), Response.class);
        Assert.assertNotNull(response);
    }
    @Test
    public void testListJobRequestSuccess() throws Exception{
        mockListJobResponse();
        Response response = executeInTenSeconds(createListJobRequest(), Response.class);
        Assert.assertNotNull(response);
    }

    private Request createJobSubmitRequest() {
        Request req = new Request();
        req.setOperation(ActorOperations.SUBMIT_JOB_REQUEST.getValue());
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(JsonKey.TAG, "do_2137002173427875841205_01370023185341644822");
        requestMap.put(JsonKey.REQUESTED_BY, "fca2925f-1eee-4654-9177-fece3fd6afc9");
        requestMap.put(JsonKey.DATASET, "progress-exhaust");
        requestMap.put(JsonKey.CONTENT_TYPE, "Course");
        requestMap.put(JsonKey.OUTPUT_FORMAT, "csv");
        requestMap.put(JsonKey.ENCRYPTIONKEY, "test");
        requestMap.put(JsonKey.DATASETCONFIG, new HashMap<>().put(JsonKey.BATCH_ID,"01370023185341644822"));
        req.setRequest(requestMap);
        return req;
    }
    private void mockJobSubmitResponse() throws UnirestException {
        HttpRequestWithBody http = Mockito.mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = Mockito.mock(RequestBodyEntity.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.post(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.body(Mockito.anyString())).thenReturn(entity);
        when(entity.asString()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"ekstep.analytics.dataset.request.submit\",\n" +
                "    \"ver\": \"1.0\",\n" +
                "    \"ts\": \"2020-06-29T08:31:03ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"7f88375d-4c59-4f3a-9626-be0fd3b3ad72\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null,\n" +
                "        \"client_key\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "        \"attempts\": 0,\n" +
                "        \"lastUpdated\": 1683113415084,\n" +
                "        \"tag\": \"do_2137002173427875841205_01370023185341644822:01269878797503692810\",\n" +
                "        \"expiresAt\": 1683115215120,\n" +
                "        \"datasetConfig\": {\n" +
                "               \"batchId\": \"01370023185341644822\"\n" +
                "           },\n" +
                "        \"downloadUrls\": [],\n" +
                "        \"requestedBy\": \"fca2925f-1eee-4654-9177-fece3fd6afc9\",\n" +
                "        \"jobStats\": {\n" +
                "               \"dtJobSubmitted\": 1683113415084,\n" +
                "               \"dtJobCompleted\": null,\n" +
                "               \"executionTime\": null\n" +
                "           },\n" +
                "        \"status\": \"SUBMITTED\",\n" +
                "        \"dataset\": \"progress-exhaust\",\n" +
                "        \"requestId\": \"768A4C249657E994FC3F8FB2D86281FA\",\n" +
                "        \"requestedChannel\": \"01269878797503692810\"\n" +
                "    }\n" +
                "}").thenThrow(ProjectCommonException.class);
    }
    private Request createListJobRequest() {
        Request req = new Request();
        req.setOperation(ActorOperations.LIST_JOB_REQUEST.getValue());
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put(JsonKey.TAG, "do_2137002173427875841205_01370023185341644822");
        req.setRequest(requestMap);
        return req;
    }
    private void mockListJobResponse() throws UnirestException {
        GetRequest http = Mockito.mock(GetRequest.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(Unirest.get(Mockito.anyString())).thenReturn(http);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http.asString()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn("{\n" +
                "    \"id\": \"eekstep.analytics.dataset.request.list\",\n" +
                "    \"ver\": \"1.0\",\n" +
                "    \"ts\": \"2020-06-29T08:31:03ZZ\",\n" +
                "    \"params\": {\n" +
                "        \"resmsgid\": \"7f88375d-4c59-4f3a-9626-be0fd3b3ad72\",\n" +
                "        \"msgid\": null,\n" +
                "        \"err\": null,\n" +
                "        \"status\": \"successful\",\n" +
                "        \"errmsg\": null,\n" +
                "        \"client_key\": null\n" +
                "    },\n" +
                "    \"responseCode\": \"OK\",\n" +
                "    \"result\": {\n" +
                "       \"count\": 3,\n" +
                "       \"jobs\": [\n" +
                "           {\n"+
                "               \"attempts\": 0,\n" +
                "               \"lastUpdated\": 1683113415084,\n" +
                "               \"tag\": \"do_2137002173427875841205_01370023185341644822:01269878797503692810\",\n" +
                "               \"expiresAt\": 1683115215120,\n" +
                "               \"datasetConfig\": {\n" +
                "                       \"batchId\": \"01370023185341644822\"\n" +
                "                   },\n" +
                "               \"downloadUrls\": [],\n" +
                "               \"requestedBy\": \"fca2925f-1eee-4654-9177-fece3fd6afc9\",\n" +
                "               \"jobStats\": {\n" +
                "                       \"dtJobSubmitted\": 1683113415084,\n" +
                "                       \"dtJobCompleted\": null,\n" +
                "                       \"executionTime\": null\n" +
                "                   },\n" +
                "               \"status\": \"SUBMITTED\",\n" +
                "               \"dataset\": \"progress-exhaust\",\n" +
                "               \"requestId\": \"768A4C249657E994FC3F8FB2D86281FA\",\n" +
                "               \"requestedChannel\": \"01269878797503692810\"\n" +
                "           }\n"+
                "       ]\n"+
                "   }\n" +
                "}").thenThrow(ProjectCommonException.class);
    }
}
