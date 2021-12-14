package org.sunbird.learner.actors.eventAttendance.dao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.event.attendance.EventAttendance;

public interface EventAttendanceDao {

    /**
     * Create event attendance
     *
     * @param requestContext the request context
     * @param eventAttendanceMap Event attendance information to be created
     * @return Response containing identifier of created event attendance
     */
    Response create(RequestContext requestContext, Map<String, Object> eventAttendanceMap);

    /**
     * Update event attendance
     *
     * @param contentId the content id
     * @param batchId the batch id
     * @param userId the user id
     * @param requestContext the request context
     * @param id the uuid
     * @param updateAttributes Event attendance information to be updated
     * @return The event attendance data
     */
    Response update(RequestContext requestContext, String contentId, String batchId, String userId, UUID id, Map<String, Object> updateAttributes);

    /**
     * Reads event attendance for given identifier
     *
     * @param contentId the content id
     * @param batchId the batch id
     * @param userId the user id
     * @param requestContext the request context
     * @return The event attendance data
     */
    EventAttendance readById(String contentId, String batchId, String userId, RequestContext requestContext);

    /**
     * Reads event attendance for given identifier
     *
     * @param contentId the content id
     * @param batchId the batch id
     * @param userId the user id
     * @param requestContext the request context
     * @return The event attendance data
     */
    List<EventAttendance> readById(RequestContext requestContext, String contentId, String batchId, String userId);

}
