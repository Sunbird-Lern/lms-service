package modules;

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.learner.util.SchedulerManager;
import org.sunbird.learner.util.Util;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;

@RunWith(PowerMockRunner.class)
// @SuppressStaticInitializationFor("org.sunbird.learner.util.Util")
@PrepareForTest({Util.class, SchedulerManager.class})
@PowerMockIgnore("javax.management.*")
public class ModuleTest {
  @Before
  public void setup() throws Exception {
    PowerMockito.mockStatic(Util.class);
    PowerMockito.mockStatic(SchedulerManager.class);
    PowerMockito.doNothing().when(Util.class, "checkCassandraDbConnections", Mockito.anyString());
    PowerMockito.doNothing().when(SchedulerManager.class);
    SchedulerManager.schedule();
  }

  @Test
  public void startApplicationTest() {
    Application application =
        new GuiceApplicationBuilder().in(new File("path/to/app")).in(Mode.TEST).build();
    Helpers.start(application);
  }
}
