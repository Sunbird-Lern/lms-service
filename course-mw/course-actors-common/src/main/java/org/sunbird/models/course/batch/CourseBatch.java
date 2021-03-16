package org.sunbird.models.course.batch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.sunbird.learner.util.CustomDateSerializer;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourseBatch implements Serializable {

  private static final long serialVersionUID = 1L;
  private String batchId;
  private String courseCreator;
  private String courseId;
  private String createdBy;

  @JsonSerialize(using = CustomDateSerializer.class)
  private Date createdDate;

  private List<String> createdFor;
  private String description;

  @JsonSerialize(using = CustomDateSerializer.class)
  private Date endDate;

  @JsonSerialize(using = CustomDateSerializer.class)
  private Date enrollmentEndDate;

  private String enrollmentType;
  private String hashTagId;
  private List<String> mentors;
  private String name;

  @JsonSerialize(using = CustomDateSerializer.class)
  private Date startDate;

  private Integer status;

  @JsonSerialize(using = CustomDateSerializer.class)
  private Date updatedDate;

  private Map<String, Object> cert_templates;

  private Boolean convertDateAsString;

  public String getCourseCreator() {
    return courseCreator;
  }

  public void setCourseCreator(String courseCreator) {
    this.courseCreator = courseCreator;
  }

  public String getCourseId() {
    return courseId;
  }

  public void setCourseId(String courseId) {
    this.courseId = courseId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Date getCreatedDate() {
    return createdDate;
  }

  public void setCreatedDate(Date createdDate) {
    this.createdDate = createdDate;
  }

  public List<String> getCreatedFor() {
    return createdFor;
  }

  public void setCreatedFor(List<String> createdFor) {
    this.createdFor = createdFor;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Date getEndDate() {
    return endDate;
  }

  public void setEndDate(Date endDate) {
    this.endDate = endDate;
  }

  public String getEnrollmentType() {
    return enrollmentType;
  }

  public void setEnrollmentType(String enrollmentType) {
    this.enrollmentType = enrollmentType;
  }

  public Date getEnrollmentEndDate() {
    return enrollmentEndDate;
  }

  public void setEnrollmentEndDate(Date enrollmentEndDate) {
    this.enrollmentEndDate = enrollmentEndDate;
  }

  public String getHashTagId() {
    return hashTagId;
  }

  public void setHashTagId(String hashTagId) {
    this.hashTagId = hashTagId;
  }

  public List<String> getMentors() {
    return mentors;
  }

  public void setMentors(List<String> mentors) {
    this.mentors = mentors;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Date getStartDate() {
    return startDate;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public Date getUpdatedDate() {
    return updatedDate;
  }

  public void setUpdatedDate(Date updatedDate) {
    this.updatedDate = updatedDate;
  }

  public void setContentDetails(Map<String, Object> contentDetails, String createdBy) {
    this.setCreatedBy(createdBy);
    this.setCreatedDate(new Date());
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public Map<String, Object> getCert_templates() {
    return cert_templates;
  }

  public Boolean getConvertDateAsString() {
    return this.convertDateAsString;
  }

  public void setConvertDateAsString(boolean convert) {
    this.convertDateAsString = convert;
  }

}
