package org.sunbird.userorg;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Unirest.class})
@PowerMockIgnore({"javax.management.*"})
public class UserOrgServiceTest {
    private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

    @Test
    public void testgetUsersByIds() throws Exception {
        List<String> ids = new ArrayList<String>() {{
            add("8454cb21-3ce9-4e30-85b5-fade097880d8");
            add("95e4942d-cbe8-477d-aebd-ad8e6de4bfc8");
        }};
        mockResponse();
        List<Map<String, Object>> content = userOrgService.getUsersByIds(ids, "authToken");
        for(Map<String, Object> map : content){
            if(StringUtils.equalsIgnoreCase("95e4942d-cbe8-477d-aebd-ad8e6de4bfc8", (String) map.get("createdBy")))
                assertTrue(MapUtils.isNotEmpty((Map<String, Object>) map.get("creatorDetails")));
        }
    }

    private void mockResponse() throws UnirestException {
        GetRequest http = Mockito.mock(GetRequest.class);
        GetRequest http2 = Mockito.mock(GetRequest.class);
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        HttpResponse<String> response2 = Mockito.mock(HttpResponse.class);
        mockStatic(Unirest.class);
        when(Unirest.get(Mockito.anyString())).thenReturn(http, http2);
        when(http.headers(Mockito.anyMap())).thenReturn(http);
        when(http2.headers(Mockito.anyMap())).thenReturn(http2);
        when(http.asString()).thenReturn(response);
        when(http2.asString()).thenReturn(response2);
        when(response.getStatus()).thenReturn(200);
        when(response2.getStatus()).thenReturn(200);
        String resp1 = "{\"result\":{\"response\":{\"id\":\"8454cb21-3ce9-4e30-85b5-fade097880d8\",\"firstName\":\"FirstName1\",\"lastName\":\"LastName1\"}}}";
        String resp2 = "{\"result\":{\"response\":{\"id\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"firstName\":\"FirstName2\",\"lastName\":\"LastName2\"}}}";
        when(response.getBody()).thenReturn(resp1);
        when(response2.getBody()).thenReturn(resp2);
    }
}
