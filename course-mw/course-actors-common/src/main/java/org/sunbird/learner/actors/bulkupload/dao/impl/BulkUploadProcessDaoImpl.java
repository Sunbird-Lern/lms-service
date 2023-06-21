package org.sunbird.learner.actors.bulkupload.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TableNameUtil;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.bulkupload.dao.BulkUploadProcessDao;
import org.sunbird.learner.actors.bulkupload.model.BulkUploadProcess;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Created by arvind on 24/4/18. */
public class BulkUploadProcessDaoImpl implements BulkUploadProcessDao {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private ObjectMapper mapper = new ObjectMapper();
  private LoggerUtil logger = new LoggerUtil(BulkUploadProcessDaoImpl.class);
  
  @Override
  public Response create(BulkUploadProcess bulkUploadProcess, RequestContext requestContext) {
    Map<String, Object> map = mapper.convertValue(bulkUploadProcess, Map.class);
    map.put(JsonKey.CREATED_ON, new Timestamp(Calendar.getInstance().getTimeInMillis()));
    Response response = cassandraOperation.insertRecord(requestContext, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.BULK_UPLOAD_PROCESS_TABLENAME, map);
    // need to send ID along with success msg
    response.put(JsonKey.ID, map.get(JsonKey.ID));
    return response;
  }

  @Override
  public Response update(RequestContext requestContext, BulkUploadProcess bulkUploadProcess) {
    Map<String, Object> map = mapper.convertValue(bulkUploadProcess, Map.class);
    if (map.containsKey(JsonKey.CREATED_ON)) {
      map.remove(JsonKey.CREATED_ON);
    }
    map.put(JsonKey.LAST_UPDATED_ON, new Timestamp(Calendar.getInstance().getTimeInMillis()));
    return cassandraOperation.updateRecord(requestContext, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.BULK_UPLOAD_PROCESS_TABLENAME, map);
  }

  @Override
  public BulkUploadProcess read(RequestContext requestContext, String id) {
    Response response = cassandraOperation.getRecordByIdentifier(requestContext, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.BULK_UPLOAD_PROCESS_TABLENAME, id, null);
    List<Map<String, Object>> list = (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(list)) {
      return null;
    }
    try {
      String jsonString = mapper.writeValueAsString((Map<String, Object>) list.get(0));
      return mapper.readValue(jsonString, BulkUploadProcess.class);
    } catch (IOException e) {
      logger.error(requestContext, "read : " + e.getMessage(), e);
    }
    return null;
  }
}
