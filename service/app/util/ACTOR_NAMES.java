package util;

import org.sunbird.aggregate.CollectionSummaryAggregate;
import org.sunbird.enrolments.CourseEnrolmentActor;
import org.sunbird.enrolments.ContentConsumptionActor;
import org.sunbird.enrolments.CourseEnrolmentActor;
import org.sunbird.group.GroupAggregatesActor;
import org.sunbird.learner.actors.BackgroundJobManager;
import org.sunbird.learner.actors.PageManagementActor;
import org.sunbird.learner.actors.bulkupload.BulkUploadBackGroundJobActor;
import org.sunbird.learner.actors.bulkupload.BulkUploadManagementActor;
import org.sunbird.learner.actors.cache.CacheManagementActor;
import org.sunbird.learner.actors.certificate.service.CertificateActor;
import org.sunbird.learner.actors.certificate.service.CourseBatchCertificateActor;
import org.sunbird.learner.actors.course.CourseManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseBatchManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseBatchNotificationActor;
import org.sunbird.learner.actors.health.HealthActor;
import org.sunbird.learner.actors.qrcodedownload.QRCodeDownloadManagementActor;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import org.sunbird.learner.actors.syncjobmanager.EsSyncActor;
import org.sunbird.learner.actors.textbook.TextbookTocActor;
import org.sunbird.collectionhierarchy.actors.CollectionTOCActor;

public enum ACTOR_NAMES {
  COURSE_BATCH_MANAGEMENT_ACTOR(CourseBatchManagementActor.class, "course-batch-management-actor"),
  COLLECTION_AGGREGATE_SUMMARY_ACTOR(CollectionSummaryAggregate.class, "collection-summary-aggregate-actor"),
  CACHE_MANAGEMENT_ACTOR(CacheManagementActor.class, "cache-management-actor"),
  PAGE_MANAGEMENT_ACTOR(PageManagementActor.class, "page-management-actor"),
  SEARCH_HANDLER_ACTOR(SearchHandlerActor.class, "search-handler-actor"),
  TEXTBOOK_TOC_ACTOR(TextbookTocActor.class, "textbook-toc-actor"),
  COLLECTION_TOC_ACTOR(CollectionTOCActor.class, "collection-toc-actor"),
  HEALTH_ACTOR(HealthActor.class, "health-actor"),
  COURSEBATCH_CERTIFICATE_ACTOR(
      CourseBatchCertificateActor.class, "course-batch-certificate-actor"),
  CERTIFICATE_ACTOR(CertificateActor.class, "certificate-actor"),
  QRCODE_DOWNLOAD_MANAGEMENT_ACTOR(
      QRCodeDownloadManagementActor.class, "qrcode-download-management-actor"),
  BULK_UPLOAD_MANAMGEMENT_ACTOR(BulkUploadManagementActor.class, "bulk-upload-management-actor"),
  BULK_UPLOAD_BACKGROUND_JOB_ACTOR(
      BulkUploadBackGroundJobActor.class, "bulk-upload-background-job-actor"),
  ES_SYNC_ACTOR(EsSyncActor.class, "es-sync-actor"),
  COURSE_BATCH_NOTIFICATION_ACTOR(CourseBatchNotificationActor.class, "course-batch-notification-actor"),
    BACKGROUND_JOB_MANAGER_ACTOR(BackgroundJobManager.class, "background-job-manager-actor"),
  COURSE_MANAGEMENT_ACTOR(CourseManagementActor.class, "course-management-actor"),
  //Scala Actors
  COURSE_ENROLMENT_ACTOR(CourseEnrolmentActor.class, "course-enrolment-actor"),
  CONTENT_CONSUMPTION_ACTOR(ContentConsumptionActor.class, "content-consumption-actor"),
  GROUP_AGGREGATES_ACTORS(GroupAggregatesActor.class, "group-aggregates-actor");

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
