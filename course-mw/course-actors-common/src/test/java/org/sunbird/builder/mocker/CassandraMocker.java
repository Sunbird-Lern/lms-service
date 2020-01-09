package org.sunbird.builder.mocker;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.powermock.api.mockito.PowerMockito;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraDACImpl;
import org.sunbird.helper.ServiceFactory;

public class CassandraMocker implements Mocker<CassandraOperation> {
  private CassandraOperation cassandraOperation;

  public CassandraMocker() {
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = mock(CassandraDACImpl.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
  }

  public CassandraOperation getServiceMock() {
    return cassandraOperation;
  }
}
