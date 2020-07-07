package util;

import org.sunbird.badge.actors.BadgeAssociationActor;
import org.sunbird.learner.actors.BackgroundJobManager;
import org.sunbird.learner.actors.LearnerStateActor;
import org.sunbird.learner.actors.LearnerStateUpdateActor;
import org.sunbird.learner.actors.PageManagementActor;
import org.sunbird.learner.actors.bulkupload.BulkUploadBackGroundJobActor;
import org.sunbird.learner.actors.bulkupload.BulkUploadManagementActor;
import org.sunbird.learner.actors.cache.CacheManagementActor;
import org.sunbird.learner.actors.certificate.service.CertificateActor;
import org.sunbird.learner.actors.certificate.service.CourseBatchCertificateActor;
import org.sunbird.learner.actors.course.CourseManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseBatchManagementActor;
import org.sunbird.learner.actors.coursebatch.CourseBatchNotificationActor;
import org.sunbird.learner.actors.coursebatch.CourseEnrollmentActor;
import org.sunbird.learner.actors.health.HealthActor;
import org.sunbird.learner.actors.qrcodedownload.QRCodeDownloadManagementActor;
import org.sunbird.learner.actors.search.SearchHandlerActor;
import org.sunbird.learner.actors.syncjobmanager.EsSyncActor;
import org.sunbird.learner.actors.textbook.TextbookTocActor;

public enum ACTOR_NAMES {
  COURSE_BATCH_MANAGEMENT_ACTOR(CourseBatchManagementActor.class, "course-batch-management-actor"),
  COURSE_ENROLLEMENT_ACTOR(CourseEnrollmentActor.class, "course-enrollment-actor"),
  CACHE_MANAGEMENT_ACTOR(CacheManagementActor.class, "cache-management-actor"),
  PAGE_MANAGEMENT_ACTOR(PageManagementActor.class, "page-management-actor"),
  SEARCH_HANDLER_ACTOR(SearchHandlerActor.class, "search-handler-actor"),
  LEARNER_STATE_ACTOR(LearnerStateActor.class, "learner-state-actor"),
  LEARNER_STATE_UPDATE_ACTOR(LearnerStateUpdateActor.class, "learner-state-update-actor"),
  TEXTBOOK_TOC_ACTOR(TextbookTocActor.class, "textbook-toc-actor"),
  HEALTH_ACTOR(HealthActor.class, "health-actor"),
  COURSEBATCH_CERTIFICATE_ACTOR(
      CourseBatchCertificateActor.class, "course-batch-certificate-actor"),
  CERTIFICATE_ACTOR(CertificateActor.class, "certificate-actor"),
  QRCODE_DOWNLOAD_MANAGEMENT_ACTOR(
      QRCodeDownloadManagementActor.class, "qrcode-download-management-actor"),
  BADGE_ASSOCIATION_ACTOR(BadgeAssociationActor.class, "badge-association-actor"),
  BULK_UPLOAD_MANAMGEMENT_ACTOR(BulkUploadManagementActor.class, "bulk-upload-management-actor"),
  BULK_UPLOAD_BACKGROUND_JOB_ACTOR(
      BulkUploadBackGroundJobActor.class, "bulk-upload-background-job-actor"),
  ES_SYNC_ACTOR(EsSyncActor.class, "es-sync-actor"),
  COURSE_BATCH_NOTIFICATION_ACTOR(CourseBatchNotificationActor.class, "course-batch-notification-actor"),
    BACKGROUND_JOB_MANAGER_ACTOR(BackgroundJobManager.class, "background-job-manager-actor"),
  COURSE_MANAGEMENT_ACTOR(
          CourseManagementActor.class, "course-management-actor");

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
