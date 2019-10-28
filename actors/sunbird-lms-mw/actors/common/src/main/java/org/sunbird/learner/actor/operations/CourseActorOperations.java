package org.sunbird.learner.actor.operations;

public enum CourseActorOperations {
  ISSUE_CERTIFICATE("issueCertificate");

  private String value;

  private CourseActorOperations(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
