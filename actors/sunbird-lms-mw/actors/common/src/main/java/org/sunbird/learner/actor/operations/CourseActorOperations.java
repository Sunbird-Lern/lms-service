package org.sunbird.learner.actor.operations;

public enum CourseActorOperations {
  ISSUE_CERTIFICATE("issueCertificate"),
  ADD_CERTIFICATE("addCertificate"),
  GET_CERTIFICATE("getCertificate"),
  DELETE_CERTIFICATE("deleteCertificate");

  private String value;

  private CourseActorOperations(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
