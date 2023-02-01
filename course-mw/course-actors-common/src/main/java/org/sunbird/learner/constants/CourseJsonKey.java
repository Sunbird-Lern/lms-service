package org.sunbird.learner.constants;

import java.util.Arrays;
import java.util.List;

public abstract class CourseJsonKey {

  public static final String ACTOR = "actor";
  public static final String ACTION = "action";
  public static final String OBJECT = "object";
  public static final String E_DATA = "edata";
  public static final String ITERATION = "iteration";
  public static final String CERTIFICATE = "certificate";
  public static final String REISSUE = "reIssue";
  public static final String UNDERSCORE = "_";
  public static final String CERTIFICATES = "certificates";
  public static final String CERTIFICATE_COUNT = "certificateCount";
  public static final String CERTIFICATES_DOT_NAME = "certificates.name";
  public static final String CERTIFICATE_NAME = "certificateName";
  public static final String CERTIFICATE_TEMPLATES_COLUMN = "cert_templates";
  public static final String LAST_UPDATED_ON = "lastUpdatedOn";
  public static final String TEMPLATE = "template";
  public static final String TEMPLATE_ID = "templateId";
  public static final String ISSUER = "issuer";
  public static final String SIGNATORY_LIST = "signatoryList";
  public static final List<String> SIGNATORY_LIST_ATTRIBUTES = Arrays.asList("name","id","designation","image");
  public static final String ENROLLMENT = "enrollment";
  public static final String SCORE = "score";
  public static final String ASSESMENT_CRITERIA = "ge, le, eq";
  public static final String SUNBIRD_SEND_EMAIL_NOTIFICATION_API =
      "sunbird_send_email_notifictaion_api";
  public static final String NOTIFY_TEMPLATE="notifyTemplate";
  public static final String CERT_TEMPLATES = "certTemplates";
    public static final String ADDITIONAL_PROPS = "additionalProps";
}
