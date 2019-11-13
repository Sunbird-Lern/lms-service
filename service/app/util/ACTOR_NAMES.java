package util;

import org.sunbird.learner.actors.PageManagementActor;
import org.sunbird.learner.actors.cache.CacheManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseBatchManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseEnrollmentActor;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import org.sunbird.metrics.actors.CourseMetricsActor;

public enum ACTOR_NAMES {
  COURSE_BATCH_MANAGEMENT_ACTOR(CourseBatchManagementActor.class, "course-batch-management-actor"),
  COURSE_ENROLLEMENT_ACTOR(CourseEnrollmentActor.class, "course-enrollment-actor"),
  COURSE_METRICS_ACTOR(CourseMetricsActor.class, "course-metrics-actor"),
  CACHE_MANAGEMENT_ACTOR(CacheManagementActor.class, "cache-management-actor"),
  PAGE_MANAGEMENT_ACTOR(PageManagementActor.class, "page-management-actor"),
  SEARCH_HANDLER_ACTOR(SearchHandlerActor.class, "search-handler-actor");

  private ACTOR_NAMES(Class clazz, String name) {
    actorClass = clazz;
    actorName = name;
  }

  private Class actorClass;
  private String actorName;

  public Class getActorClass() {
    return actorClass;
  }

  public String getActorName() {
    return actorName;
  }
}
