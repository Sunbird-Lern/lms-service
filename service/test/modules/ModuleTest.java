package modules;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.learner.util.SchedulerManager;
import org.sunbird.learner.util.Util;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;

import java.io.File;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor("org.sunbird.learner.util.Util")
@PrepareForTest({Util.class, SunbirdMWService.class, SchedulerManager.class})
@PowerMockIgnore("javax.management.*")
public class ModuleTest {
    @Before
    public void setup() throws Exception {
        PowerMockito.mockStatic(Util.class);
        PowerMockito.mockStatic(SunbirdMWService.class);
        PowerMockito.mockStatic(SchedulerManager.class);
        PowerMockito.doNothing().when(Util.class, "checkCassandraDbConnections", Mockito.anyString());
        PowerMockito.doNothing().when(SunbirdMWService.class);
        SunbirdMWService.init();
        PowerMockito.doNothing().when(SchedulerManager.class);
        SchedulerManager.schedule();
    }

    @Test
    public void startApplicationTest() {
        Application application =
                new GuiceApplicationBuilder()
                        .in(new File("path/to/app"))
                        .in(Mode.TEST)
                        .build();
        Helpers.start(application);
    }
}
