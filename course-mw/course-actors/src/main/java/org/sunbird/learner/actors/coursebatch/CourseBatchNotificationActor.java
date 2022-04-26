package org.sunbird.learner.actors.coursebatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.util.JsonUtil;
import org.sunbird.learner.util.ContentUtil;
import org.sunbird.learner.util.CourseBatchSchedulerUtil;
import org.sunbird.models.course.batch.CourseBatch;
import org.sunbird.userorg.UserOrgService;
import org.sunbird.userorg.UserOrgServiceImpl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Actor responsible to sending email notifications to participants and mentors in open and
 * invite-only batches.
 */
public class CourseBatchNotificationActor extends BaseActor {
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
      logger.info(request.getRequestContext(), "CourseBatchNotificationActor:onReceive: operation = " + request.getOperation());
      courseBatchNotification(request);
    } else {
      logger.error(request.getRequestContext(), "CourseBatchNotificationActor:onReceive: Unsupported operation = "
              + request.getOperation(), null);
    }
  }

  private void courseBatchNotification(Request request) throws Exception {

    Map<String, Object> requestMap = request.getRequest();

    CourseBatch courseBatch = (CourseBatch) requestMap.get(JsonKey.COURSE_BATCH);
    String authToken = (String) request.getContext().getOrDefault(JsonKey.X_AUTH_TOKEN, "");

    String userId = (String) requestMap.get(JsonKey.USER_ID);
    logger.info(request.getRequestContext(), "CourseBatchNotificationActor:courseBatchNotification: userId = " + userId);

    Map<String, String> headers = CourseBatchSchedulerUtil.headerMap;
    Map<String, Object> contentDetails =
        ContentUtil.getCourseObjectFromEkStep(courseBatch.getCourseId(), headers);

    if (userId != null) {
      logger.info(request.getRequestContext(), "CourseBatchNotificationActor:courseBatchNotification: Open batch");

      // Open batch
      String template = JsonKey.OPEN_BATCH_LEARNER_UNENROL;
      String subject = JsonKey.UNENROLL_FROM_COURSE_BATCH;

      String operationType = (String) requestMap.get(JsonKey.OPERATION_TYPE);

      if (operationType.equals(JsonKey.ADD)) {
        template = JsonKey.OPEN_BATCH_LEARNER_ENROL;
        subject = JsonKey.COURSE_INVITATION;
      }

      triggerEmailNotification( request.getRequestContext(), 
          Arrays.asList(userId), courseBatch, subject, template, contentDetails, authToken);

    } else {
      logger.info(request.getRequestContext(), "CourseBatchNotificationActor:courseBatchNotification: Invite only batch");

      List<String> addedMentors = (List<String>) requestMap.get(JsonKey.ADDED_MENTORS);
      List<String> removedMentors = (List<String>) requestMap.get(JsonKey.REMOVED_MENTORS);

      triggerEmailNotification(
              request.getRequestContext(), addedMentors,
          courseBatch,
          JsonKey.COURSE_INVITATION,
          JsonKey.BATCH_MENTOR_ENROL,
          contentDetails, authToken);
      triggerEmailNotification(
              request.getRequestContext(), removedMentors,
          courseBatch,
          JsonKey.UNENROLL_FROM_COURSE_BATCH,
          JsonKey.BATCH_MENTOR_UNENROL,
          contentDetails, authToken);

      List<String> addedParticipants = (List<String>) requestMap.get(JsonKey.ADDED_PARTICIPANTS);
      List<String> removedParticipants =
          (List<String>) requestMap.get(JsonKey.REMOVED_PARTICIPANTS);

      triggerEmailNotification(
              request.getRequestContext(), addedParticipants,
          courseBatch,
          JsonKey.COURSE_INVITATION,
          JsonKey.BATCH_LEARNER_ENROL,
          contentDetails, authToken);
      triggerEmailNotification(
              request.getRequestContext(), removedParticipants,
          courseBatch,
          JsonKey.UNENROLL_FROM_COURSE_BATCH,
          JsonKey.BATCH_LEARNER_UNENROL,
          contentDetails, authToken);
    }
  }

  private void triggerEmailNotification(
          RequestContext requestContext, List<String> userIdList,
          CourseBatch courseBatch,
          String subject,
          String template,
          Map<String, Object> contentDetails, String authToken) throws Exception {

    logger.debug(requestContext, "CourseBatchNotificationActor:triggerEmailNotification: userIdList = "
            + userIdList);

    if (CollectionUtils.isEmpty(userIdList)) return;

    for (String userId : userIdList) {
      Map<String, Object> requestMap =
          createEmailRequest(userId, courseBatch, contentDetails, subject, template);

      logger.info(requestContext, "CourseBatchNotificationActor:triggerEmailNotification: requestMap = " + requestMap);
      sendMail(requestContext, requestMap, authToken);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> createEmailRequest(
      String userId,
      CourseBatch courseBatch,
      Map<String, Object> contentDetails,
      String subject,
      String template) throws Exception {
    Map<String, Object> courseBatchObject = JsonUtil.convert(courseBatch, Map.class);

    Map<String, Object> request = new HashMap<>();
    Map<String, Object> requestMap = new HashMap<String, Object>();

    requestMap.put(JsonKey.SUBJECT, subject);
    requestMap.put(JsonKey.EMAIL_TEMPLATE_TYPE, template);
    requestMap.put(JsonKey.BODY, "Notification mail Body");
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
    requestMap.put(JsonKey.RECIPIENT_USERIDS, Arrays.asList(userId));
    request.put(JsonKey.REQUEST, requestMap);
    return request;
  }

  private String getCourseBatchUrl(String courseId, String batchId) {

    String url = baseUrl + "/learn/course/" + courseId + "/batch/" + batchId;
    return url;
  }

  private void sendMail(RequestContext requestContext, Map<String, Object> requestMap, String authToken) {
    logger.info(requestContext, "CourseBatchNotificationActor:sendMail: email ready");
    try {
      userOrgService.sendEmailNotification(requestMap, authToken);
      logger.info(requestContext, "CourseBatchNotificationActor:sendMail: Email sent successfully");
    } catch (Exception e) {
      logger.error(requestContext, "CourseBatchNotificationActor:sendMail: Exception occurred with error message = "
                      + e.getMessage(), e);
    }
  }
}
