package org.sunbird.models.event.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventAttendance implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID id;
    private String userId;
    private String batchId;
    private String contentId;
    private String role;
    private Date joinedDateTime;
    private Date leftDateTime;
    private Long duration;
    private String provider;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Date getJoinedDateTime() {
        return joinedDateTime;
    }

    public void setJoinedDateTime(Date joinedDateTime) {
        this.joinedDateTime = joinedDateTime;
    }

    public Date getLeftDateTime() {
        return leftDateTime;
    }

    public void setLeftDateTime(Date leftDateTime) {
        this.leftDateTime = leftDateTime;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
