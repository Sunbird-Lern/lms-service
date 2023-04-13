package org.sunbird.learner.actors.eventAttendance.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.eventAttendance.dao.EventAttendanceDao;
import org.sunbird.learner.util.Util;
import org.sunbird.models.event.attendance.EventAttendance;

import java.util.*;
import java.util.stream.Collectors;

public class EventAttendanceDaoImpl implements EventAttendanceDao {

    private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private static final Util.DbInfo eventAttendanceDb = Util.dbInfoMap.get(JsonKey.EVENT_ATTENDANCE_DB);
    private static final String KEYSPACE_NAME = eventAttendanceDb.getKeySpace();
    private static final String TABLE_NAME = eventAttendanceDb.getTableName();
    private ObjectMapper mapper = new ObjectMapper();


    /**
     * Create event attendance
     *
     * @param requestContext the request context
     * @param eventAttendanceMap Event attendance information to be created
     * @return Response containing identifier of created event attendance
     */
    @Override
    public Response create(RequestContext requestContext, Map<String, Object> eventAttendanceMap) {
        return cassandraOperation.insertRecord(requestContext, KEYSPACE_NAME, TABLE_NAME, eventAttendanceMap);
    }

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
    @Override
    public Response update(RequestContext requestContext, String contentId, String batchId, String userId, UUID id, Map<String, Object> updateAttributes) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(JsonKey.CONTENT_ID_KEY, contentId);
        primaryKey.put(JsonKey.BATCH_ID_KEY, batchId);
        primaryKey.put(JsonKey.USER_ID_KEY, userId);
        primaryKey.put(JsonKey.ID_KEY, id);
        Map<String, Object> updateList = new HashMap<>();
        updateList.putAll(updateAttributes);
        updateList.remove(JsonKey.CONTENT_ID_KEY);
        updateList.remove(JsonKey.BATCH_ID_KEY);
        updateList.remove(JsonKey.USER_ID_KEY);
        updateList.remove(JsonKey.ID_KEY);
        updateList = CassandraUtil.changeCassandraColumnMapping(updateList);
        return cassandraOperation.updateRecord(
                requestContext, KEYSPACE_NAME, TABLE_NAME, updateList, primaryKey);
    }

    /**
     * Reads event attendance for given identifier
     *
     * @param contentId the content id
     * @param batchId the batch id
     * @param userId the user id
     * @param requestContext the request context
     * @return The event attendance data
     */
    @Override
    public EventAttendance readById(String contentId, String batchId, String userId, RequestContext requestContext) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(JsonKey.CONTENT_ID, contentId);
        primaryKey.put(JsonKey.BATCH_ID, batchId);
        primaryKey.put(JsonKey.USER_ID, userId);
        Response eventAttendanceResult =
                cassandraOperation.getRecordByIdentifier(
                        requestContext, KEYSPACE_NAME, TABLE_NAME, primaryKey, null);
        List<Map<String, Object>> eventList =
                (List<Map<String, Object>>) eventAttendanceResult.get(JsonKey.RESPONSE);
        if (eventList.isEmpty()) {
            return null;
        } else {
            return mapper.convertValue(eventList.get(0), EventAttendance.class);
        }
    }

    /**
     * Reads event attendance for given identifier
     *
     * @param contentId the content id
     * @param batchId the batch id
     * @param userId the user id
     * @param requestContext the request context
     * @return The event attendance data
     */
    @Override
    public List<EventAttendance> readById(RequestContext requestContext, String contentId, String batchId, String userId) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(JsonKey.CONTENT_ID, contentId);
        if (null != batchId) primaryKey.put(JsonKey.BATCH_ID, batchId);
        if (null != userId) primaryKey.put(JsonKey.USER_ID, userId);
        Response eventAttendanceResult =
                cassandraOperation.getRecordsByProperties(requestContext, KEYSPACE_NAME, TABLE_NAME, primaryKey);
        List<Map<String, Object>> eventList =
                (List<Map<String, Object>>) eventAttendanceResult.get(JsonKey.RESPONSE);
        if (eventList.isEmpty()) {
            return null;
        } else {
            return eventList.stream().map(el -> mapper.convertValue(el, EventAttendance.class)).collect(Collectors.toList());
        }
    }
}
