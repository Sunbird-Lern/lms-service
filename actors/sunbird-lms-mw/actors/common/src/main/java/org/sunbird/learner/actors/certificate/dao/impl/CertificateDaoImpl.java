package org.sunbird.learner.actors.certificate.dao.impl;

import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.certificate.dao.CertificateDao;
import org.sunbird.learner.constants.CourseJsonKey;
import org.sunbird.learner.util.Util;

import java.util.Map;

public class CertificateDaoImpl implements CertificateDao {

    private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
    private Util.DbInfo courseBatchDb = Util.dbInfoMap.get(CourseJsonKey.CERTIFICATE_TEMPLATE_DB);

    @Override
    public Response add(Map<String,Object> certificateMap) {
        return cassandraOperation.insertRecord(
                courseBatchDb.getKeySpace(), courseBatchDb.getTableName(), certificateMap);
    }
}
