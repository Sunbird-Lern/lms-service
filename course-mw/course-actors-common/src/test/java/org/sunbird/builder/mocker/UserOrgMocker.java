package org.sunbird.builder.mocker;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.powermock.api.mockito.PowerMockito;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

public class UserOrgMocker implements Mocker<UserOrgService> {
  private UserOrgService userOrgService;

  public UserOrgMocker() {
    PowerMockito.mockStatic(UserOrgServiceImpl.class);
    userOrgService = mock(UserOrgServiceImpl.class);
    when(UserOrgServiceImpl.getInstance()).thenReturn(userOrgService);
  }

  @Override
  public UserOrgService getServiceMock() {
    return userOrgService;
  }
}
