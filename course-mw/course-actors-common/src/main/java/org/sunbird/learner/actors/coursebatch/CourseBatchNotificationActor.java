package org.sunbird.learner.actors.coursebatch;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.actor.background.BackgroundOperations;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.models.util.*;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.notificationservice.EmailServiceActor;
import org.sunbird.learner.util.CourseBatchSchedulerUtil;
import org.sunbird.models.course.batch.CourseBatch;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

/**
 * Actor responsible to sending email notifications to participants and mentors in open and
 * invite-only batches.
 */
public class CourseBatchNotificationActor extends BaseActor {
  private static final Props props = Props.create(EmailServiceActor.class);
  private static ActorSystem system = ActorSystem.create("system");
  private static String courseBatchNotificationSignature =
      PropertiesCache.getInstance()
          .getProperty(JsonKey.SUNBIRD_COURSE_BATCH_NOTIFICATION_SIGNATURE);
  private static String baseUrl =
      PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_WEB_URL);
  private UserOrgService userOrgService = UserOrgServiceImpl.getInstance();

  @Override
  public void onReceive(Request request) throws Throwable {
    String requestedOperation = request.getOperation();

    if (requestedOperation.equals(ActorOperations.COURSE_BATCH_NOTIFICATION.getValue())) {
      ProjectLogger.log(
          "CourseBatchNotificationActor:onReceive: operation = " + request.getOperation(),
          LoggerEnum.INFO);
      courseBatchNotification(request);
    } else {
      ProjectLogger.log(
          "CourseBatchNotificationActor:onReceive: Unsupported operation = "
              + request.getOperation(),
          LoggerEnum.ERROR);
    }
  }

  private void courseBatchNotification(Request request) {

    Map<String, Object> requestMap = request.getRequest();

    CourseBatch courseBatch = (CourseBatch) requestMap.get(JsonKey.COURSE_BATCH);

    String userId = (String) requestMap.get(JsonKey.USER_ID);
    ProjectLogger.log(
        "CourseBatchNotificationActor:courseBatchNotification: userId = " + userId,
        LoggerEnum.INFO);

    Map<String, String> headers = CourseBatchSchedulerUtil.headerMap;
    Map<String, Object> contentDetails =
        CourseEnrollmentActor.getCourseObjectFromEkStep(courseBatch.getCourseId(), headers);

    if (userId != null) {
      ProjectLogger.log(
          "CourseBatchNotificationActor:courseBatchNotification: Open batch", LoggerEnum.INFO);

      // Open batch
      String template = JsonKey.OPEN_BATCH_LEARNER_UNENROL;
      String subject = JsonKey.UNENROLL_FROM_COURSE_BATCH;

      String operationType = (String) requestMap.get(JsonKey.OPERATION_TYPE);

      if (operationType.equals(JsonKey.ADD)) {
        template = JsonKey.OPEN_BATCH_LEARNER_ENROL;
        subject = JsonKey.COURSE_INVITATION;
      }

      triggerEmailNotification(
          Arrays.asList(userId), courseBatch, subject, template, contentDetails);

    } else {
      ProjectLogger.log(
          "CourseBatchNotificationActor:courseBatchNotification: Invite only batch",
          LoggerEnum.INFO);

      List<String> addedMentors = (List<String>) requestMap.get(JsonKey.ADDED_MENTORS);
      List<String> removedMentors = (List<String>) requestMap.get(JsonKey.REMOVED_MENTORS);

      triggerEmailNotification(
          addedMentors,
          courseBatch,
          JsonKey.COURSE_INVITATION,
          JsonKey.BATCH_MENTOR_ENROL,
          contentDetails);
      triggerEmailNotification(
          removedMentors,
          courseBatch,
          JsonKey.UNENROLL_FROM_COURSE_BATCH,
          JsonKey.BATCH_MENTOR_UNENROL,
          contentDetails);

      List<String> addedParticipants = (List<String>) requestMap.get(JsonKey.ADDED_PARTICIPANTS);
      List<String> removedParticipants =
          (List<String>) requestMap.get(JsonKey.REMOVED_PARTICIPANTS);

      triggerEmailNotification(
          addedParticipants,
          courseBatch,
          JsonKey.COURSE_INVITATION,
          JsonKey.BATCH_LEARNER_ENROL,
          contentDetails);
      triggerEmailNotification(
          removedParticipants,
          courseBatch,
          JsonKey.UNENROLL_FROM_COURSE_BATCH,
          JsonKey.BATCH_LEARNER_UNENROL,
          contentDetails);
    }
  }

  private void triggerEmailNotification(
      List<String> userIdList,
      CourseBatch courseBatch,
      String subject,
      String template,
      Map<String, Object> contentDetails) {

    ProjectLogger.log(
        "CourseBatchNotificationActor:triggerEmailNotification: userIdList = "
            + CollectionUtils.isEmpty(userIdList),
        LoggerEnum.INFO);

    if (CollectionUtils.isEmpty(userIdList)) return;

    for (String userId : userIdList) {
      Map<String, Object> requestMap = this.createEmailRequest(userId, courseBatch, contentDetails);

      requestMap.put(JsonKey.SUBJECT, subject);
      requestMap.put(JsonKey.EMAIL_TEMPLATE_TYPE, template);

      ProjectLogger.log(
          "CourseBatchNotificationActor:triggerEmailNotification: requestMap = " + requestMap,
          LoggerEnum.INFO);

      sendMail(requestMap);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> createEmailRequest(
      String userId, CourseBatch courseBatch, Map<String, Object> contentDetails) {
    ProjectLogger.log("CourseBatchNotificationActor: createEmailRequest:  ", LoggerEnum.INFO);
    Map<String, Object> courseBatchObject = new ObjectMapper().convertValue(courseBatch, Map.class);

    Map<String, Object> requestMap = new HashMap<String, Object>();

    requestMap.put(JsonKey.REQUEST, BackgroundOperations.emailService.name());

    requestMap.put(JsonKey.ORG_NAME, courseBatchObject.get(JsonKey.ORG_NAME));
    requestMap.put(JsonKey.COURSE_LOGO_URL, contentDetails.get(JsonKey.APP_ICON));
    requestMap.put(JsonKey.START_DATE, courseBatchObject.get(JsonKey.START_DATE));
    requestMap.put(JsonKey.END_DATE, courseBatchObject.get(JsonKey.END_DATE));
    requestMap.put(JsonKey.COURSE_ID, courseBatchObject.get(JsonKey.COURSE_ID));
    requestMap.put(JsonKey.BATCH_NAME, courseBatch.getName());
    requestMap.put(JsonKey.COURSE_NAME, contentDetails.get(JsonKey.NAME));
    requestMap.put(
        JsonKey.COURSE_BATCH_URL,
        getCourseBatchUrl(courseBatch.getCourseId(), courseBatch.getBatchId()));
    requestMap.put(JsonKey.SIGNATURE, courseBatchNotificationSignature);
    requestMap.put(JsonKey.RECIPIENT_USERIDS, userId);
    ProjectLogger.log(
        "CourseBatchNotificationActor:createEmailRequest: success  ", LoggerEnum.INFO);

    return requestMap;
  }

  private String getCourseBatchUrl(String courseId, String batchId) {

    String url = baseUrl + "/learn/course/" + courseId + "/batch/" + batchId;
    return url;
  }

  private void sendMail(Map<String, Object> requestMap) {
    ProjectLogger.log("CourseBatchNotificationActor:sendMail: email ready  ", LoggerEnum.INFO);
    try {
      userOrgService.sendEmailNotification(requestMap);
      ProjectLogger.log(
          "CourseBatchNotificationActor:sendMail: Email sent successfully", LoggerEnum.INFO);
    } catch (Exception e) {
      ProjectLogger.log(
          "CourseBatchNotificationActor:sendMail: Exception occurred with error message = "
              + e.getMessage(),
          LoggerEnum.ERROR);
    }
  }
}
