package org.sunbird.learner.actors.certificate.dao.impl;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.certificate.dao.CertificateDao;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.Util;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertificateDaoImpl implements CertificateDao {

    private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private Util.DbInfo certificateTemplate_db = Util.dbInfoMap.get(CourseJsonKey.CERTIFICATE_TEMPLATE_DB);

    @Override
    public void add(Map<String,Object> certificateMap) {
        certificateMap.put(CourseJsonKey.LAST_UPDATED_ON,new Timestamp(System.currentTimeMillis()));
        certificateMap.remove(JsonKey.ID);
        certificateMap.remove(JsonKey.USER_ID);
        cassandraOperation.insertRecord(
                certificateTemplate_db.getKeySpace(), certificateTemplate_db.getTableName(), certificateMap);
    }

    @Override
    public List<Map<String, Object>> readById(String courseId, String batchId) {
        Map<String, Object> primaryKey = new HashMap<>();
        primaryKey.put(JsonKey.COURSE_ID, courseId);
        if(batchId == null) {
            batchId = StringUtils.EMPTY;
        }
        primaryKey.put(JsonKey.BATCH_ID,batchId);
        Response certificateResult =
                cassandraOperation.getRecordById(
                        certificateTemplate_db.getKeySpace(), certificateTemplate_db.getTableName(), primaryKey);
        List<Map<String, Object>> certificateList =
                (List<Map<String, Object>>) certificateResult.get(JsonKey.RESPONSE);
        for(Map<String, Object> certificate : certificateList)

        {
            if(StringUtils.isBlank((String)certificate.get(JsonKey.BATCH_ID))){
                certificate.remove(JsonKey.BATCH_ID);
            }
        }
        return certificateList;
    }
}
