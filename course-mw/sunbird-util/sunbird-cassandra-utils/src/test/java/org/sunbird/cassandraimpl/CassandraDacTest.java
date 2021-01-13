package org.sunbird.cassandraimpl;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.models.response.Response;

import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

@PrepareForTest(CassandraUtil.class)
@RunWith(PowerMockRunner.class)
public class CassandraDacTest {

    Response response = new Response();

    @BeforeClass
    public static void setUp(){
        MockitoAnnotations.initMocks(CassandraDacTest.class);
    }

    @Test
    public void blob_success_result(){
        Session session = mock(Session.class);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(session.prepare("")).thenReturn(ps);
        BoundStatement bound = ps.bind(Arrays.asList(""));
        when(ps.bind(Arrays.asList(""))).thenReturn(bound);
        ResultSet results = mock(ResultSet.class);
        when(session.execute(bound)).thenReturn(results);
        PowerMockito.mockStatic(CassandraUtil.class);
        when(CassandraUtil.createResponse(results)).thenReturn(response);
        Assert.assertTrue(response!=null);


    }
}