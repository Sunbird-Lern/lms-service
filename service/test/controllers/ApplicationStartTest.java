package controllers;

import org.junit.Test;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.RequestContext;
import org.mockito.Mockito;

import static modules.ApplicationStart.mockServiceSetup;

public class ApplicationStartTest {

    @Test
    public void testMockServiceSetupWithMockEnabledTrue() {
        System.setProperty(JsonKey.CONTENT_SERVICE_MOCK_ENABLED, "true");
        LoggerUtil logger = Mockito.mock(LoggerUtil.class);
        RequestContext mockRequestContext = Mockito.mock(RequestContext.class);
        mockServiceSetup();
        Mockito.verify(logger, Mockito.never())
                .info(Mockito.eq(mockRequestContext), Mockito.anyString());
    }

    @Test
    public void testMockServiceSetupWithMockEnabledFalse() {
        System.setProperty(JsonKey.CONTENT_SERVICE_MOCK_ENABLED, "false");
        LoggerUtil logger = Mockito.mock(LoggerUtil.class);
        RequestContext mockRequestContext = Mockito.mock(RequestContext.class);
        mockServiceSetup();
        Mockito.verify(logger, Mockito.never())
                .info(Mockito.eq(mockRequestContext), Mockito.anyString());
    }
}
