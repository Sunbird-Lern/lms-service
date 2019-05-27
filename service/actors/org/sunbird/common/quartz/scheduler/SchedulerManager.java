/** */
package org.sunbird.common.quartz.scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;

/**
 * This class will manage all the Quartz scheduler. We need to call the schedule method at one time.
 * we are calling this method from Util.java class.
 *
 * @author Manzarul
 */
public final class SchedulerManager {

  private static final String FILE = "quartz.properties";
  private static Scheduler scheduler = null;
  private static SchedulerManager schedulerManager = null;

  private SchedulerManager() {
    schedule();
  }

  /** This method will register the quartz scheduler job. */
  private void schedule() {
    ProjectLogger.log(
        "SchedulerManager:schedule: Call to start scheduler jobs - org.sunbird.common.quartz.scheduler.SchedulerManager",
        LoggerEnum.INFO.name());

    try {
      Thread.sleep(240000);
      boolean isEmbedded = false;
      Properties configProp = null;
      String embeddVal = System.getenv(JsonKey.SUNBIRD_QUARTZ_MODE);
      if (JsonKey.EMBEDDED.equalsIgnoreCase(embeddVal)) {
        isEmbedded = true;
      } else {
        configProp = setUpClusterMode();
      }
      if (!isEmbedded && configProp != null) {
        ProjectLogger.log("Quartz scheduler is running in cluster mode.");
        scheduler = new StdSchedulerFactory(configProp).getScheduler();
      } else {
        ProjectLogger.log("Quartz scheduler is running in embedded mode.");
        scheduler = new StdSchedulerFactory().getScheduler();
      }
      String identifier = "NetOps-PC1502295457753";
      scheduleCourseBatchCount(identifier);
      // scheduleCoursePublishJob(identifier);
      scheduleMetricsReportJob(identifier);
    } catch (Exception e) {
      ProjectLogger.log(
          "SchedulerManager:schedule: Error in starting scheduler jobs - org.sunbird.common.quartz.scheduler.SchedulerManager ",
          e);
    } finally {
      registerShutDownHook();
    }
    ProjectLogger.log(
        "SchedulerManager:schedule: started scheduler jobs - org.sunbird.common.quartz.scheduler.SchedulerManager",
        LoggerEnum.INFO.name());
  }

  private void scheduleMetricsReportJob(String identifier) {
    // add another job for verifying the MetricsReportJob details from EKStep.
    // 1- create a job and bind with class which is implementing Job
    // interface.
    JobDetail metricsReportJob =
        JobBuilder.newJob(MetricsReportJob.class)
            .requestRecovery(true)
            .withDescription("Scheduler for retry of metrics report generation and upload to azure")
            .withIdentity("metricsReportJob", identifier)
            .build();

    // 2- Create a trigger object that will define frequency of run.
    // This job will run every day 11:30 PM IN GMT and 6 PM on UTC.
    Trigger metricsReportRetryTrigger =
        TriggerBuilder.newTrigger()
            .withIdentity("metricsReportRetryTrigger", identifier)
            .withSchedule(
                CronScheduleBuilder.cronSchedule(
                    PropertiesCache.getInstance().getProperty("quartz_matrix_report_timer")))
            .build();
    try {
      if (scheduler.checkExists(metricsReportJob.getKey())) {
        scheduler.deleteJob(metricsReportJob.getKey());
      }
      scheduler.scheduleJob(metricsReportJob, metricsReportRetryTrigger);
      scheduler.start();
      ProjectLogger.log("MetricsReportJob schedular started", LoggerEnum.INFO.name());
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
  }

  private void scheduleCoursePublishJob(String identifier) {
    // add another job for verifying the course published details from EKStep.
    // 1- create a job and bind with class which is implementing Job
    // interface.
    JobDetail coursePublishedJob =
        JobBuilder.newJob(CoursePublishedUpdate.class)
            .requestRecovery(true)
            .withDescription(
                "Scheduler for batch participants enrolment on course status change to published")
            .withIdentity("coursePublishedScheduler", identifier)
            .build();

    // 2- Create a trigger object that will define frequency of run.
    // This job will run every hours.
    Trigger coursePublishedTrigger =
        TriggerBuilder.newTrigger()
            .withIdentity("coursePublishedTrigger", identifier)
            .withSchedule(
                CronScheduleBuilder.cronSchedule(
                    PropertiesCache.getInstance().getProperty("quartz_course_publish_timer")))
            .build();
    try {
      if (scheduler.checkExists(coursePublishedJob.getKey())) {
        scheduler.deleteJob(coursePublishedJob.getKey());
      }
      scheduler.scheduleJob(coursePublishedJob, coursePublishedTrigger);
      scheduler.start();
      ProjectLogger.log(
          "SchedulerManager:scheduleCoursePublishJob: CoursePublishedUpdate schedular started",
          LoggerEnum.INFO.name());
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
  }

  private void scheduleCourseBatchCount(String identifier) {
    // 1- create a job and bind with class which is implementing Job
    // interface.
    JobDetail job =
        JobBuilder.newJob(ManageCourseBatchCount.class)
            .requestRecovery(true)
            .withDescription("Scheduler for computing active count of batches for each course")
            .withIdentity("schedulerJob", identifier)
            .build();

    // 2- Create a trigger object that will define frequency of run.
    // This scheduler will run every day 11:30 PM IN GMT and 6 PM on UTC.
    // server time is set in UTC so all scheduler need to be manage based on that
    // time only.
    String configValue = PropertiesCache.getInstance().readProperty("quartz_course_batch_timer");
    if (StringUtils.isEmpty(configValue)) {
      ProjectLogger.log(
          "SchedulerManager:scheduleCourseBatchCount: Error in starting Course batch count scheduler , quartz_course_batch_timer value not found",
          LoggerEnum.INFO);
      return;
    }
    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity("schedulertrigger", identifier)
            .withSchedule(
                CronScheduleBuilder.cronSchedule(
                    PropertiesCache.getInstance().readProperty("quartz_course_batch_timer")))
            .build();
    try {
      if (scheduler.checkExists(job.getKey())) {
        scheduler.deleteJob(job.getKey());
      }
      scheduler.scheduleJob(job, trigger);
      scheduler.start();
      ProjectLogger.log(
          "SchedulerManager:scheduleCourseBatchCount: schedular started", LoggerEnum.INFO.name());
    } catch (Exception e) {
      ProjectLogger.log(e.getMessage(), e);
    }
  }

  /**
   * This method will do the Quartz scheduler set up in cluster mode.
   *
   * @return Properties
   * @throws IOException
   */
  public Properties setUpClusterMode() throws IOException {
    Properties configProp = new Properties();
    InputStream in = this.getClass().getClassLoader().getResourceAsStream(FILE);
    String host = System.getenv(JsonKey.SUNBIRD_PG_HOST);
    String port = System.getenv(JsonKey.SUNBIRD_PG_PORT);
    String db = System.getenv(JsonKey.SUNBIRD_PG_DB);
    String username = System.getenv(JsonKey.SUNBIRD_PG_USER);
    String password = System.getenv(JsonKey.SUNBIRD_PG_PASSWORD);
    if (!StringUtils.isBlank(host)
        && !StringUtils.isBlank(port)
        && !StringUtils.isBlank(db)
        && !StringUtils.isBlank(username)
        && !StringUtils.isBlank(password)) {
      ProjectLogger.log(
          "Taking Postgres value from Environment variable...", LoggerEnum.INFO.name());
      configProp.load(in);
      configProp.put(
          "org.quartz.dataSource.MySqlDS.URL", "jdbc:postgresql://" + host + ":" + port + "/" + db);
      configProp.put("org.quartz.dataSource.MySqlDS.user", username);
      configProp.put("org.quartz.dataSource.MySqlDS.password", password);
      ProjectLogger.log(
          "SchedulerManager:setUpClusterMode: Connection is established from environment variable",
          LoggerEnum.INFO);
    } else {
      ProjectLogger.log(
          "SchedulerManager:setUpClusterMode: Environment variable is not set for postgres SQl.",
          LoggerEnum.INFO.name());
      configProp = null;
    }
    return configProp;
  }

  public static SchedulerManager getInstance() {
    if (schedulerManager != null) {
      return schedulerManager;
    } else {
      schedulerManager = new SchedulerManager();
    }
    return schedulerManager;
  }

  /**
   * This class will be called by registerShutDownHook to register the call inside jvm , when jvm
   * terminate it will call the run method to clean up the resource.
   *
   * @author Manzarul
   */
  static class ResourceCleanUp extends Thread {
    @Override
    public void run() {
      ProjectLogger.log(
          "SchedulerManager:ResourceCleanUp: started resource cleanup for Quartz job.",
          LoggerEnum.INFO);
      try {
        scheduler.shutdown();
      } catch (SchedulerException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
      ProjectLogger.log(
          "SchedulerManager:ResourceCleanUp: completed resource cleanup Quartz job.",
          LoggerEnum.INFO);
    }
  }

  /** Register the hook for resource clean up. this will be called when jvm shut down. */
  public static void registerShutDownHook() {
    Runtime runtime = Runtime.getRuntime();
    runtime.addShutdownHook(new ResourceCleanUp());
    ProjectLogger.log(
        "SchedulerManager:registerShutDownHook: ShutDownHook registered for Quartz scheduler.",
        LoggerEnum.INFO);
  }
}
