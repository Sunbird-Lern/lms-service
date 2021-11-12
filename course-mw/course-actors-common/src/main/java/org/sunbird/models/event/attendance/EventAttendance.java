package org.sunbird.models.event.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventAttendance implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String batchId;
    private String contentId;
    private String role;
    private Date firstJoined;
    private Date lastJoined;
    private Date firstLeft;
    private Date lastLeft;
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

    public Date getFirstJoined() {
        return firstJoined;
    }

    public void setFirstJoined(Date firstJoined) {
        this.firstJoined = firstJoined;
    }

    public Date getLastJoined() {
        return lastJoined;
    }

    public void setLastJoined(Date lastJoined) {
        this.lastJoined = lastJoined;
    }

    public Date getFirstLeft() {
        return firstLeft;
    }

    public void setFirstLeft(Date firstLeft) {
        this.firstLeft = firstLeft;
    }

    public Date getLastLeft() {
        return lastLeft;
    }

    public void setLastLeft(Date lastLeft) {
        this.lastLeft = lastLeft;
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
}
