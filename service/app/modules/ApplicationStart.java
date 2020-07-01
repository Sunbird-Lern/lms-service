package modules;

import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.sunbird.auth.verifier.KeyManager;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.learner.util.SchedulerManager;
import org.sunbird.learner.util.Util;
import play.api.Environment;
import play.api.inject.ApplicationLifecycle;

/**
 * This class will be called after on application startup. only one instance of this class will be
 * created. StartModule class has responsibility to eager load this class.
 *
 * @author Jaikumar Soundara Rajan
 */
@Singleton
public class ApplicationStart {

  public static ProjectUtil.Environment env;
  public static String ssoPublicKey = "";

  /**
   * All one time initialization which required during server startup will fall here.
   *
   * @param lifecycle ApplicationLifecycle
   * @param environment Environment
   */
  @Inject
  public ApplicationStart(ApplicationLifecycle lifecycle, Environment environment) {
    System.out.println("ApplicationStart:ApplicationStart: Start");
    setEnvironment(environment);
    ssoPublicKey = System.getenv(JsonKey.SSO_PUBLIC_KEY);
    ProjectLogger.log("Server started.. with environment: " + env.name(), LoggerEnum.INFO.name());
    checkCassandraConnections();
    SchedulerManager.schedule();
    lifecycle.addStopHook(
        () -> {
          return CompletableFuture.completedFuture(null);
        });
    System.out.println("keymanger.init():starts");
    KeyManager.init();
    System.out.println("ApplicationStart:ApplicationStart: End");
  }

  private void checkCassandraConnections() {
    Util.checkCassandraDbConnections();
  }

  /**
   * This method will identify the environment and update with enum.
   *
   * @return Environment
   */
  public ProjectUtil.Environment setEnvironment(Environment environment) {
    if (environment.asJava().isDev()) {
      return env = ProjectUtil.Environment.dev;
    } else if (environment.asJava().isTest()) {
      return env = ProjectUtil.Environment.qa;
    } else {
      return env = ProjectUtil.Environment.prod;
    }
  }
}
