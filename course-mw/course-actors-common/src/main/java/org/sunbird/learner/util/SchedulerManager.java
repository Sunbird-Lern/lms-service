/** */
package org.sunbird.learner.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sunbird.common.cacheloader.PageCacheLoaderService;
import org.sunbird.logging.LoggerEnum;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.logging.ProjectLogger;

/** @author Manzarul All the scheduler job will be handle by this class. */
public class SchedulerManager {

  private static final int PAGE_DATA_TTL = 4;
  private static LoggerUtil logger = new LoggerUtil(SchedulerManager.class);

  /*
   * service ScheduledExecutorService object
   */
  public static ScheduledExecutorService service = ExecutorManager.getExecutorService();

  /** all scheduler job will be configure here. */
  public static void schedule() {
    service.scheduleWithFixedDelay(new DataCacheHandler(), 0, PAGE_DATA_TTL, TimeUnit.HOURS);
    service.scheduleWithFixedDelay(new PageCacheLoaderService(), 0, PAGE_DATA_TTL, TimeUnit.HOURS);
    logger.info(null, 
        "SchedulerManager:schedule: Started scheduler job for cache refresh.");
  }
}
