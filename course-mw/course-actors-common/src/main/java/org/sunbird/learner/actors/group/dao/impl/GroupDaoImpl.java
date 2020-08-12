package org.sunbird.learner.actors.group.dao.impl;

import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GroupDaoImpl {
    private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private static final String KEYSPACE_NAME = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB).getKeySpace();
    private static final String TABLE_NAME = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB).getTableName();
    
    public Response read(String activityId, String activityType, List<String> userId, RequestContext requestContext) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(SunbirdKey.ACTIVITY_TYPE, activityType);
        primaryKey.put(SunbirdKey.ACTIVITY_ID, activityId);
        primaryKey.put(SunbirdKey.USER_ID, userId);
        Response response = cassandraOperation.getRecordByIdentifier(KEYSPACE_NAME, TABLE_NAME, primaryKey, null, requestContext);
        return response;
    }
}
