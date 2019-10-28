package org.sunbird.learner.constants;

public enum InstructionEvent {
  BATCH_USER_STATE_UPDATE(
      "Course Batch Updater", "System", "CourseBatchEnrolment", "batch-enrolment-update"),
  ISSUE_COURSE_CERTIFICATE(
      "Course Certificate Generator", "System", "CourseCertificateGeneration", "issue-certificate");

  private String actorId;
  private String actorType;
  private String type;
  private String action;

  private InstructionEvent(String actorId, String actorType, String type, String action) {
    this.actorId = actorId;
    this.actorType = actorType;
    this.type = type;
    this.action = action;
  }

  public String getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }

  public String getType() {
    return type;
  }

  public String getAction() {
    return action;
  }
}
