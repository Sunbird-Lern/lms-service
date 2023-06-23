package org.sunbird.cassandraimpl;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.WriteType;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.exceptions.WriteTimeoutException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.BaseTest;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TableNameUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionManagerImpl;
import org.sunbird.helper.CassandraConnectionMngrFactory;
import org.sunbird.helper.ServiceFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CassandraConnectionMngrFactory.class, CassandraConnectionManagerImpl.class})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*", "sun.security.ssl.*", "javax.net.ssl.*" , "javax.crypto.*"})
public class CassandraDACImplTest extends BaseTest {

    String keyspace = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE);
    String table = TableNameUtil.ASSESSMENT_AGGREGATOR_TABLENAME;
    String user_consumption_table = TableNameUtil.USER_CONTENT_CONSUMPTION_TABLENAME;
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    @Mock
    CassandraConnectionManager connectionManager;
    @Mock
    Session session2;

    String createKeyspace = "CREATE KEYSPACE IF NOT EXISTS " + keyspace
            + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}";
    String createTable = "CREATE TABLE IF NOT EXISTS " + keyspace + "." + table
            + " (course_id text,batch_id text,user_id text,content_id text,attempt_id text,created_on timestamp,grand_total text,last_attempted_on timestamp,total_max_score double,total_score double,updated_on timestamp,PRIMARY KEY (course_id, batch_id, user_id, content_id, attempt_id));";
    String insertTable = "INSERT INTO " + keyspace + "." + table
            + "(user_id, course_id, batch_id, content_id, attempt_id, total_max_score, total_score, last_attempted_on) VALUES ('user_001','course_001','batch_001', 'content_001', 'attempt_001', 1, 1, '" + timestamp + "');";
    String create_user_consumption_table = "CREATE TABLE IF NOT EXISTS " + keyspace + "." + user_consumption_table
            + " (userid text,courseid text,batchid text,contentid text,completedcount int,datetime timestamp,lastaccesstime text,lastcompletedtime text,lastupdatedtime text,progress int,status int,viewcount int,PRIMARY KEY (userid, courseid, batchid, contentid));";

    CassandraOperation cassandraOperation = ServiceFactory.getInstance();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        executeScript(createKeyspace, createTable, create_user_consumption_table, insertTable);
    }

    @Test
    public void testGetRecordsWithLimit() {
        Request request = getRequest();
        Map<String, Object> filters = new HashMap<String, Object>() {
            {
                put("user_id", "user_001");
                put("course_id", "course_001");
                put("batch_id", "batch_001");
                put("content_id", new ArrayList<String>() {{
                    add("content_001");
                }});
            }
        };
        ArrayList<String> fieldsToGet = new java.util.ArrayList<String>() {{
            add("attempt_id");
            add("last_attempted_on");
            add("total_max_score");
            add("total_score");
        }};

        Map<String, Object> result = new HashMap<String, Object>() {{
            put("totalMaxScore", 1.0);
            put("lastAttemptedOn", timestamp);
            put("totalScore", 1.0);
            put("attemptId", "attempt_001");
        }};

        PowerMockito.stub(PowerMockito.method(CassandraConnectionMngrFactory.class, "getInstance")).toReturn(connectionManager);
        PowerMockito.stub(PowerMockito.method(CassandraConnectionManagerImpl.class, "getSession")).toReturn(session);
        Response response = cassandraOperation.getRecordsWithLimit(request.getRequestContext(), keyspace, table, filters, fieldsToGet, 25);
        Assert.assertEquals(response.getResponseCode(), ResponseCode.OK);
        Assert.assertTrue(((ArrayList<Map<String, Object>>) response.getResult().get("response")).get(0).equals(result));
    }

    @Test
    public void testBatchInsertLogged() {
        Request request = getRequest();
        ArrayList<Map<String, Object>> records = new ArrayList<Map<String, Object>>() {
            {
                add(new HashMap<String, Object>() {{
                put("userId", "user_001");
                put("courseId", "course_001");
                put("batchId", "batch_001");
                put("contentId", new ArrayList<String>() {{
                    add("content_001");
                }});
            }});
            }
        };
        PowerMockito.stub(PowerMockito.method(CassandraConnectionMngrFactory.class, "getInstance")).toReturn(connectionManager);
        PowerMockito.stub(PowerMockito.method(CassandraConnectionManagerImpl.class, "getSession")).toReturn(session);
        Response response = cassandraOperation.batchInsertLogged(request.getRequestContext(), keyspace, user_consumption_table, records);
        Assert.assertEquals(response.getResponseCode(), ResponseCode.OK);
    }

    @Test(expected = InvalidQueryException.class)
    public void testBatchInsertLoggedException() {
        Request request = getRequest();
        ArrayList<Map<String, Object>> records = new ArrayList<Map<String, Object>>() {
            {
                add(new HashMap<String, Object>() {{
                    put("courseId", "course_001");
                    put("batchId", "batch_001");
                    put("contentId", new ArrayList<String>() {{
                        add("content_001");
                    }});
                }});
            }
        };
        PowerMockito.stub(PowerMockito.method(CassandraConnectionMngrFactory.class, "getInstance")).toReturn(connectionManager);
        PowerMockito.stub(PowerMockito.method(CassandraConnectionManagerImpl.class, "getSession")).toReturn(session);
        cassandraOperation.batchInsertLogged(request.getRequestContext(), keyspace, user_consumption_table, records);
    }

    @Test
    @Ignore
    public void testBatchInsertLoggedPartialWrite() {
        Request request = getRequest();
        ArrayList<Map<String, Object>> records = new ArrayList<Map<String, Object>>() {
            {
                add(new HashMap<String, Object>() {{
                    put("userId", "user_001");
                    put("courseId", "course_001");
                    put("batchId", "batch_001");
                    put("contentId", new ArrayList<String>() {{
                        add("content_001");
                    }});
                }});
            }
        };
        PowerMockito.stub(PowerMockito.method(CassandraConnectionMngrFactory.class, "getInstance")).toReturn(connectionManager);
        PowerMockito.stub(PowerMockito.method(CassandraConnectionManagerImpl.class, "getSession")).toReturn(session2);
        PowerMockito.when(session2.execute(Mockito.any(BatchStatement.class))).thenThrow(new WriteTimeoutException(ConsistencyLevel.QUORUM, WriteType.SIMPLE, 1, 1));
        Response response = cassandraOperation.batchInsertLogged(request.getRequestContext(), keyspace, user_consumption_table, records);
        Assert.assertEquals(response.getResponseCode(), ResponseCode.OK);
    }

    public Request getRequest() {
        Request request = new Request();
        request.setContext(new HashMap<String, Object>() {
        });
        return request;
    }
}
