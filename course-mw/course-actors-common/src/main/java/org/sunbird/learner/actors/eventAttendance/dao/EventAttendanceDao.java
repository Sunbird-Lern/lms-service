package org.sunbird.learner.actors.eventAttendance.dao;

import java.util.List;
import java.util.Map;

import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.event.attendance.EventAttendance;

public interface EventAttendanceDao {

    /**
     * Create event attendance.
     *
     * @param requestContext
     * @param eventAttendanceMap Event attendance information to be created
     * @return Response containing identifier of created event attendance
     */
    Response create(RequestContext requestContext, Map<String, Object> eventAttendanceMap);

    /**
     * Update event attendance.
     *
     * @param eventAttendanceMap Event attendance information to be updated
     * @return Response containing status of event attendance update
     */
    Response update(RequestContext requestContext, String contentId, String batchId, String userId, Map<String, Object> eventAttendanceMap);

    /**
     * Read event attendace for given identifier.
     *
     * @param contentId Event identifier
     * @return Event attendance information
     */
    EventAttendance readById(String contentId, String batchId, String userId, RequestContext requestContext);

    /**
     * Read event attendances for given identifier.
     *
     * @param contentId Event identifier
     * @return Course batch information
     */
    List<EventAttendance> readById(String contentId, String batchId, RequestContext requestContext);

}
