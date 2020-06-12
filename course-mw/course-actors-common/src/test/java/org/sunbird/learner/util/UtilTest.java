package org.sunbird.learner.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.builder.mocker.MockerBuilder;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionManagerImpl;
import org.sunbird.helper.CassandraConnectionMngrFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PropertiesCache.class, CassandraConnectionMngrFactory.class, System.class})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class UtilTest {

  private static MockerBuilder.MockersGroup group;
  private PropertiesCache propertiesCache;
  private CassandraConnectionManager cassandraConnectionManager;

  @Before
  public void setup() {
    group =
        MockerBuilder.getFreshMockerGroup()
            .andStaticMock(PropertiesCache.class)
            .andStaticMock(CassandraConnectionMngrFactory.class);
    propertiesCache = PowerMockito.mock(PropertiesCache.class);
    cassandraConnectionManager = PowerMockito.mock(CassandraConnectionManagerImpl.class);
    PowerMockito.when(PropertiesCache.getInstance()).thenReturn(propertiesCache);
    PowerMockito.when(CassandraConnectionMngrFactory.getObject(Mockito.anyString()))
        .thenReturn(cassandraConnectionManager);
  }

  @Test
  public void checkCassandraDbConnectionsEmbeddedTest() {
    PowerMockito.when(propertiesCache.getProperty(JsonKey.SUNBIRD_CASSANDRA_MODE))
        .thenReturn(JsonKey.EMBEDDED_MODE);
    PowerMockito.when(
            cassandraConnectionManager.createConnection(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()))
        .thenReturn(true);
    Util.checkCassandraDbConnections("sunbird-test");
  }

  @Test
  @PrepareForTest({PropertiesCache.class, CassandraConnectionMngrFactory.class, System.class})
  public void checkCassandraDbConnectionsStandAloneEnvTest() {
    group.andStaticMock(System.class);
    PowerMockito.when(propertiesCache.getProperty(JsonKey.SUNBIRD_CASSANDRA_MODE))
        .thenReturn(JsonKey.STANDALONE_MODE);
    PowerMockito.when(System.getenv(JsonKey.SUNBIRD_CASSANDRA_IP)).thenReturn("127.0.1.1");
    PowerMockito.when(System.getenv(JsonKey.SUNBIRD_CASSANDRA_PORT)).thenReturn("9041");
    PowerMockito.when(
            cassandraConnectionManager.createConnection(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()))
        .thenReturn(true);
    Util.checkCassandraDbConnections("sunbird-test");
  }

  @Test
  @PrepareForTest({PropertiesCache.class, CassandraConnectionMngrFactory.class, System.class})
  public void checkCassandraDbConnectionsStandAlonePropertyTest() {
    group.andStaticMock(System.class);
    PowerMockito.when(propertiesCache.getProperty(JsonKey.SUNBIRD_CASSANDRA_MODE))
        .thenReturn(JsonKey.STANDALONE_MODE);
    PowerMockito.when(System.getenv(JsonKey.SUNBIRD_CASSANDRA_IP)).thenReturn(null);
    PowerMockito.when(System.getenv(JsonKey.SUNBIRD_CASSANDRA_PORT)).thenReturn(null);
    PowerMockito.when(
            cassandraConnectionManager.createConnection(
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()))
        .thenReturn(true);
    Util.checkCassandraDbConnections("sunbird-test");
  }

  @Test
  public void createSearchDTOFromQueryMapTest() {
    Map queryMap = new HashMap<>();
    queryMap.put(JsonKey.QUERY, "randomQuery");
    queryMap.put(JsonKey.QUERY_FIELDS, Arrays.asList("field1"));
    queryMap.put(JsonKey.FACETS, Arrays.asList(new HashMap<String, String>()));
    queryMap.put(JsonKey.FIELDS, Arrays.asList("field1"));
    queryMap.put(JsonKey.FILTERS, new HashMap<>());
    queryMap.put(JsonKey.EXISTS, Arrays.asList("field1"));
    queryMap.put(JsonKey.NOT_EXISTS, Arrays.asList("field1"));
    queryMap.put(JsonKey.SORT_BY, new HashMap<>());
    queryMap.put(JsonKey.LIMIT, 6000);
    queryMap.put(JsonKey.OFFSET, 6000);
    queryMap.put(JsonKey.GROUP_QUERY, Arrays.asList(new HashMap<String, Object>()));
    Map<String, BigInteger> softContraints = new HashMap<>();
    softContraints.put("field3", new BigInteger("55"));
    queryMap.put(JsonKey.SOFT_CONSTRAINTS, softContraints);
    SearchDTO dto = Util.createSearchDto(queryMap);
    Assert.assertNotNull(dto);
  }

  @Test
  public void createSearchDTOFromQueryMapOtherTest() {
    Map queryMap = new HashMap<>();
    queryMap.put(JsonKey.QUERY, "randomQuery");
    queryMap.put(JsonKey.QUERY_FIELDS, Arrays.asList("field1"));
    queryMap.put(JsonKey.FACETS, Arrays.asList(new HashMap<String, String>()));
    queryMap.put(JsonKey.FIELDS, Arrays.asList("field1"));
    queryMap.put(JsonKey.FILTERS, new HashMap<>());
    queryMap.put(JsonKey.EXISTS, Arrays.asList("field1"));
    queryMap.put(JsonKey.NOT_EXISTS, Arrays.asList("field1"));
    queryMap.put(JsonKey.SORT_BY, new HashMap<>());
    queryMap.put(JsonKey.LIMIT, new BigInteger("16000"));
    queryMap.put(JsonKey.OFFSET, new BigInteger("6000"));
    queryMap.put(JsonKey.GROUP_QUERY, Arrays.asList(new HashMap<String, Object>()));
    Map<String, BigInteger> softContraints = new HashMap<>();
    softContraints.put("field3", new BigInteger("55"));
    queryMap.put(JsonKey.SOFT_CONSTRAINTS, softContraints);
    SearchDTO dto = Util.createSearchDto(queryMap);
    Assert.assertNotNull(dto);
  }
}
