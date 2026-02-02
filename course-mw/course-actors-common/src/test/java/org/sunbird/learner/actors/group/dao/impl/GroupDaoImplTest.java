package org.sunbird.learner.actors.group.dao.impl;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.*;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.response.Response;
import org.sunbird.keys.JsonKey;
import org.sunbird.response.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.JsonUtil;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class})
@PowerMockIgnore("javax.management.*")
public class GroupDaoImplTest {
    private static CassandraOperation cassandraOperation;

    @BeforeClass
    public static void setUp() {
        PowerMockito.mockStatic(ServiceFactory.class);
        cassandraOperation = mock(CassandraOperationImpl.class);
        when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    }

    @Test
    public void readSuccess() {
        Response response = new Response();
        GroupDaoImpl groupDao = new GroupDaoImpl();
        Map<String, Object> groupActivityMap = new HashMap<>();
        groupActivityMap.put("user_id", "user1");
        response.put(JsonKey.RESPONSE, Arrays.asList(groupActivityMap));
        when(cassandraOperation.getRecordByIdentifier(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any(), Mockito.any())).thenReturn(response);
        Response readResponse = groupDao.read("do_1234", "course", Arrays.asList("user1"), null);
        Assert.assertNotNull(readResponse);
    }

    @Test
    public void readEntriesSuccess() throws Exception {
        GroupDaoImpl groupDao = new GroupDaoImpl();
        Response response = getReadEntriesResponse();
        when(cassandraOperation.getRecordsByProperties(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any())).thenReturn(response);
        Response readResponse = groupDao.readEntries("Course",  Arrays.asList("user1"), Arrays.asList("do_1234", "do_3456"), null);
        Assert.assertNotNull(readResponse);
        Assert.assertEquals(readResponse.getResponseCode(), ResponseCode.OK);
        Assert.assertNotNull(readResponse.getResult());
    }

    private Response getReadEntriesResponse() throws Exception {
        String  responseString = "{\"id\":null,\"ver\":null,\"ts\":null,\"params\":null,\"responseCode\":\"OK\",\"result\":{\"response\":[{\"agg\":{\"completedCount\":1},\"user_id\":\"95e4942d-cbe8-477d-aebd-ad8e6de4bfc8\",\"activity_type\":\"Course\",\"agg_last_updated\":{\"completedCount\":1595506598142},\"activity_id\":\"do_11305984881537024012255\",\"context_id\":\"cb:0130598559365038081\"}]}}";
        return JsonUtil.deserialize(responseString, Response.class);
    }
}
