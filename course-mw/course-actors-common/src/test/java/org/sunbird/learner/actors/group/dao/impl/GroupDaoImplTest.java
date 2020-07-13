package org.sunbird.learner.actors.group.dao.impl;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.sun.tools.javac.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Assert;
import org.junit.Before;
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
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class})
@PowerMockIgnore("javax.management.*")
public class GroupDaoImplTest {

    private CassandraOperation cassandraOperation;
    private GroupDaoImpl groupDao;

    @BeforeClass
    public static void setUp() {}

    @Before
    public void beforeEachTest() {
        PowerMockito.mockStatic(ServiceFactory.class);
        cassandraOperation = mock(CassandraOperationImpl.class);
        when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    }

    @Test
    public void readSuccess() {
        Response response = new Response();
        Map<String, Object> groupActivityMap = new HashMap<>();
        groupActivityMap.put("user_id", "user1");
        response.put(JsonKey.RESPONSE, Arrays.asList(groupActivityMap));
        when(cassandraOperation.getRecordById(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap())).thenReturn(response);
        Response readResponse = GroupDaoImpl.read("do_1234", "course");
        Assert.assertNotNull(readResponse);
    }
    @Test
    public void readFailure() {

        Response response = new Response();
        response.put(JsonKey.RESPONSE, new ArrayList<>());
        when(cassandraOperation.getRecordById(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(response);
        Response readResponse = GroupDaoImpl.read("do_1234", "course");
        Assert.assertTrue(CollectionUtils.isEmpty((ArrayList)readResponse.get(JsonKey.RESPONSE)));

    }
}
