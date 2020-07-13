package org.sunbird.learner.actors.group.dao.impl;

import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.SunbirdKey;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.courses.UserCourses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/*private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
private ObjectMapper mapper = new ObjectMapper();
static UserCoursesDao userCoursesDao;
private static final String KEYSPACE_NAME =Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB).getKeySpace();
private static final String TABLE_NAME =Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB).getTableName();
private static final String USER_ENROLMENTS = "user_enrolments";
public static UserCoursesDao getInstance() {
        if (userCoursesDao == null) {
        userCoursesDao = new UserCoursesDaoImpl();
        }
        return userCoursesDao;
        }*/
public class GroupDaoImpl {
    private static CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private static final String KEYSPACE_NAME = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB).getKeySpace();
    private static final String TABLE_NAME = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB).getTableName();

    public static Response read(String activityId, String activityType) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(SunbirdKey.ACTIVITY_TYPE, activityType);
        primaryKey.put(SunbirdKey.ACTIVITY_ID, activityId);
        Response response = cassandraOperation.getRecordById(KEYSPACE_NAME, TABLE_NAME, primaryKey);
        return response;
    }

    /*List<Map<String, Object>> greoupActivityList = (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
        if (CollectionUtils.isEmpty(greoupActivityList)) {
            return null;
        }
        try {
            return mapper.convertValue((Map<String, Object>) userCoursesList.get(0), UserCourses.class);
        } catch (Exception e) {
            ProjectLogger.log(e.getMessage(), e);
        }
        return null;*/



    /*List<Map<String, Object>> courseList =
            (List<Map<String, Object>>) courseBatchResult.get(JsonKey.RESPONSE);
    if (courseList.isEmpty()) {
        throw new ProjectCommonException(
                ResponseCode.invalidCourseBatchId.getErrorCode(),
                ResponseCode.invalidCourseBatchId.getErrorMessage(),
                ResponseCode.CLIENT_ERROR.getResponseCode());
    } else {
        courseList.get(0).remove(JsonKey.PARTICIPANT);
        return mapper.convertValue(courseList.get(0), CourseBatch.class);
    }*/
}
