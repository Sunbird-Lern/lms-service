package org.sunbird.cassandraimpl;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import com.datastax.driver.core.querybuilder.*;
import com.datastax.driver.core.querybuilder.Select.Builder;
import com.datastax.driver.core.querybuilder.Select.Where;
import com.datastax.driver.core.querybuilder.Update.Assignments;
import com.google.common.util.concurrent.FutureCallback;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.Constants;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;

/**
 * @author Amit Kumar
 * @desc this class will hold functions for cassandra db interaction
 */
public abstract class CassandraOperationImpl implements CassandraOperation {

  protected CassandraConnectionManager connectionManager = CassandraConnectionMngrFactory.getInstance();;
  private LoggerUtil logger = LoggerUtil.getInstance(CassandraOperationImpl.class); 

  @Override
  public Response insertRecord(String keyspaceName, String tableName, Map<String, Object> request, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service insertRecord method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      String query = CassandraUtil.getPreparedStatement(keyspaceName, tableName, request);
      PreparedStatement statement = connectionManager.getSession(keyspaceName).prepare(query);
      BoundStatement boundStatement = new BoundStatement(statement);
      Iterator<Object> iterator = request.values().iterator();
      Object[] array = new Object[request.keySet().size()];
      int i = 0;
      while (iterator.hasNext()) {
        array[i++] = iterator.next();
      }
      if(null != statement) logger.debug(requestContext, statement.getQueryString(), null);
      connectionManager.getSession(keyspaceName).execute(boundStatement.bind(array));
      response.put(Constants.RESPONSE, Constants.SUCCESS);
    } catch (Exception e) {
      if (e.getMessage().contains(JsonKey.UNKNOWN_IDENTIFIER)
          || e.getMessage().contains(JsonKey.UNDEFINED_IDENTIFIER)) {
        ProjectLogger.log(
            "Exception occured while inserting record to " + tableName + " : " + e.getMessage(), e);
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            CassandraUtil.processExceptionForUnknownIdentifier(e),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      ProjectLogger.log(
          "Exception occured while inserting record to " + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.dbInsertionError.getErrorCode(),
          ResponseCode.dbInsertionError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("insertRecord", startTime);
    return response;
  }

  @Override
  public Response updateRecord(String keyspaceName, String tableName, Map<String, Object> request, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service updateRecord method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      String query = CassandraUtil.getUpdateQueryStatement(keyspaceName, tableName, request);
      PreparedStatement statement = connectionManager.getSession(keyspaceName).prepare(query);
      Object[] array = new Object[request.size()];
      int i = 0;
      String str = "";
      int index = query.lastIndexOf(Constants.SET.trim());
      str = query.substring(index + 4);
      str = str.replace(Constants.EQUAL_WITH_QUE_MARK, "");
      str = str.replace(Constants.WHERE_ID, "");
      str = str.replace(Constants.SEMICOLON, "");
      String[] arr = str.split(",");
      for (String key : arr) {
        array[i++] = request.get(key.trim());
      }
      array[i] = request.get(Constants.IDENTIFIER);
      BoundStatement boundStatement = statement.bind(array);
      connectionManager.getSession(keyspaceName).execute(boundStatement);
      response.put(Constants.RESPONSE, Constants.SUCCESS);
    } catch (Exception e) {
      e.printStackTrace();
      if (e.getMessage().contains(JsonKey.UNKNOWN_IDENTIFIER)) {
        ProjectLogger.log(
            Constants.EXCEPTION_MSG_UPDATE + tableName + " : " + e.getMessage(),
            e,
            LoggerEnum.ERROR.name());
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            CassandraUtil.processExceptionForUnknownIdentifier(e),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      ProjectLogger.log(
          Constants.EXCEPTION_MSG_UPDATE + tableName + " : " + e.getMessage(),
          e,
          LoggerEnum.ERROR.name());
      throw new ProjectCommonException(
          ResponseCode.dbUpdateError.getErrorCode(),
          ResponseCode.dbUpdateError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("updateRecord", startTime);
    return response;
  }

  @Override
  public Response deleteRecord(String keyspaceName, String tableName, String identifier, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service deleteRecord method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      Delete.Where delete =
          QueryBuilder.delete()
              .from(keyspaceName, tableName)
              .where(eq(Constants.IDENTIFIER, identifier));
      ProjectLogger.logQuery(delete.getQueryString(), requestContext);
      connectionManager.getSession(keyspaceName).execute(delete);
      response.put(Constants.RESPONSE, Constants.SUCCESS);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_DELETE + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("deleteRecord", startTime);
    return response;
  }

  @Override
  public Response getRecordsByProperty(
          String keyspaceName,
          String tableName,
          String propertyName,
          Object propertyValue,
          List<String> fields, RequestContext requestContext) {
    Response response = new Response();
    Session session = connectionManager.getSession(keyspaceName);
    try {
      Builder selectBuilder;
      if (CollectionUtils.isNotEmpty(fields)) {
        selectBuilder = QueryBuilder.select((String[]) fields.toArray());
      } else {
        selectBuilder = QueryBuilder.select().all();
      }
      Where selectStatement =
              selectBuilder.from(keyspaceName, tableName).where();
      if(propertyValue instanceof List) {
        selectStatement.and(QueryBuilder.in(propertyName, propertyValue));
      } else {
        selectStatement.and(QueryBuilder.eq(propertyName, propertyValue));
      }
      ResultSet results = null;
      if (null != selectStatement) ProjectLogger.logQuery(selectStatement.toString(), requestContext);
      results = session.execute(selectStatement);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    return response;
  }

  @Override
  public Response getRecordsByProperties(
          String keyspaceName, String tableName, Map<String, Object> propertyMap, RequestContext requestContext) {
    return getRecordsByProperties(keyspaceName, tableName, propertyMap, null, requestContext);
  }

  @Override
  public Response getRecordsByProperties(
          String keyspaceName, String tableName, Map<String, Object> propertyMap, List<String> fields, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service getRecordsByProperties method started at ==" + startTime,
        LoggerEnum.INFO);
    Response response = new Response();
    try {
      Builder selectBuilder;
      if (CollectionUtils.isNotEmpty(fields)) {
        String[] dbFields = fields.toArray(new String[fields.size()]);
        selectBuilder = QueryBuilder.select(dbFields);
      } else {
        selectBuilder = QueryBuilder.select().all();
      }
      Select selectQuery = selectBuilder.from(keyspaceName, tableName);
      if (MapUtils.isNotEmpty(propertyMap)) {
        Where selectWhere = selectQuery.where();
        for (Entry<String, Object> entry : propertyMap.entrySet()) {
          if (entry.getValue() instanceof List) {
            List<Object> list = (List) entry.getValue();
            if (null != list) {
              Object[] propertyValues = list.toArray(new Object[list.size()]);
              Clause clause = QueryBuilder.in(entry.getKey(), propertyValues);
              selectWhere.and(clause);
            }
          } else {
            Clause clause = eq(entry.getKey(), entry.getValue());
            selectWhere.and(clause);
          }
        }
      }
      selectQuery = selectQuery.allowFiltering();
      if (null != selectQuery) ProjectLogger.logQuery(selectQuery.getQueryString(), requestContext);
      ResultSet results = connectionManager.getSession(keyspaceName).execute(selectQuery);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getRecordsByProperties", startTime);
    return response;
  }

  @Override
  public Response getPropertiesValueById(
          String keyspaceName, String tableName, String id, RequestContext requestContext, String... properties) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service getPropertiesValueById method started at ==" + startTime,
        LoggerEnum.INFO);
    Response response = new Response();
    try {
      String selectQuery = CassandraUtil.getSelectStatement(keyspaceName, tableName, properties);
      PreparedStatement statement = connectionManager.getSession(keyspaceName).prepare(selectQuery);
      ProjectLogger.logQuery(statement.getQueryString(), requestContext);
      BoundStatement boundStatement = new BoundStatement(statement);
      ResultSet results =
          connectionManager.getSession(keyspaceName).execute(boundStatement.bind(id));
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getPropertiesValueById", startTime);
    return response;
  }

  @Override
  public Response getAllRecords(String keyspaceName, String tableName, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service getAllRecords method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      Select selectQuery = QueryBuilder.select().all().from(keyspaceName, tableName);
      ProjectLogger.logQuery(selectQuery.getQueryString(), requestContext);
      ResultSet results = connectionManager.getSession(keyspaceName).execute(selectQuery);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getAllRecords", startTime);
    return response;
  }

  @Override
  public Response upsertRecord(String keyspaceName, String tableName, Map<String, Object> request, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service upsertRecord method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      String query = CassandraUtil.getPreparedStatement(keyspaceName, tableName, request);
      PreparedStatement statement = connectionManager.getSession(keyspaceName).prepare(query);
      ProjectLogger.logQuery(query, requestContext);
      BoundStatement boundStatement = new BoundStatement(statement);
      Iterator<Object> iterator = request.values().iterator();
      Object[] array = new Object[request.keySet().size()];
      int i = 0;
      while (iterator.hasNext()) {
        array[i++] = iterator.next();
      }
      connectionManager.getSession(keyspaceName).execute(boundStatement.bind(array));
      response.put(Constants.RESPONSE, Constants.SUCCESS);

    } catch (Exception e) {
      if (e.getMessage().contains(JsonKey.UNKNOWN_IDENTIFIER)) {
        ProjectLogger.log(Constants.EXCEPTION_MSG_UPSERT + tableName + " : " + e.getMessage(), e);
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            CassandraUtil.processExceptionForUnknownIdentifier(e),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      ProjectLogger.log(Constants.EXCEPTION_MSG_UPSERT + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("upsertRecord", startTime);
    return response;
  }

  @Override
  public Response updateRecord(
      String keyspaceName,
      String tableName,
      Map<String, Object> request,
      Map<String, Object> compositeKey, RequestContext requestContext) {

    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service updateRecord method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      Session session = connectionManager.getSession(keyspaceName);
      Update update = QueryBuilder.update(keyspaceName, tableName);
      Assignments assignments = update.with();
      Update.Where where = update.where();
      request
          .entrySet()
          .stream()
          .forEach(
              x -> {
                assignments.and(QueryBuilder.set(x.getKey(), x.getValue()));
              });
      compositeKey
          .entrySet()
          .stream()
          .forEach(
              x -> {
                where.and(eq(x.getKey(), x.getValue()));
              });
      Statement updateQuery = where;
      ProjectLogger.logQuery(where.getQueryString() ,requestContext);
      session.execute(updateQuery);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_UPDATE + tableName + " : " + e.getMessage(), e);
      if (e.getMessage().contains(JsonKey.UNKNOWN_IDENTIFIER)) {
        throw new ProjectCommonException(
            ResponseCode.invalidPropertyError.getErrorCode(),
            CassandraUtil.processExceptionForUnknownIdentifier(e),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
      throw new ProjectCommonException(
          ResponseCode.dbUpdateError.getErrorCode(),
          ResponseCode.dbUpdateError.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("updateRecord", startTime);
    return response;
  }

  @Override
  public Response getRecordByIdentifier(
      String keyspaceName, String tableName, Object key, List<String> fields, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service getRecordBy key method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      Session session = connectionManager.getSession(keyspaceName);
      Builder selectBuilder;
      if (CollectionUtils.isNotEmpty(fields)) {
        selectBuilder = QueryBuilder.select(fields.toArray(new String[fields.size()]));
      } else {
        selectBuilder = QueryBuilder.select().all();
      }
      Select selectQuery = selectBuilder.from(keyspaceName, tableName);
      Where selectWhere = selectQuery.where();
      if (key instanceof String) {
        selectWhere.and(eq(Constants.IDENTIFIER, key));
      } else if (key instanceof Map) {
        Map<String, Object> compositeKey = (Map<String, Object>) key;
        compositeKey
            .entrySet()
            .stream()
            .forEach(
                x -> {
                  CassandraUtil.createQuery(x.getKey(), x.getValue(), selectWhere);
                });
      }
      ProjectLogger.logQuery(selectWhere.getQueryString(), requestContext);
      ResultSet results = session.execute(selectWhere);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + tableName + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getRecordByIdentifier", startTime);
    return response;
  }

  @Override
  public Response batchInsert(
          String keyspaceName, String tableName, List<Map<String, Object>> records, RequestContext requestContext) {

    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service batchInsert method started at ==" + startTime, LoggerEnum.INFO);

    Session session = connectionManager.getSession(keyspaceName);
    Response response = new Response();
    BatchStatement batchStatement = new BatchStatement();
    ResultSet resultSet = null;

    try {
      for (Map<String, Object> map : records) {
        Insert insert = QueryBuilder.insertInto(keyspaceName, tableName);
        map.entrySet()
            .stream()
            .forEach(
                x -> {
                  insert.value(x.getKey(), x.getValue());
                });
        batchStatement.add(insert);
      }
      resultSet = session.execute(batchStatement);
      response.put(Constants.RESPONSE, Constants.SUCCESS);
    } catch (QueryExecutionException
        | QueryValidationException
        | NoHostAvailableException
        | IllegalStateException e) {
      ProjectLogger.log("Cassandra Batch Insert Failed." + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("batchInsert", startTime);
    return response;
  }

  @Override
  public Response batchUpdate(
          String keyspaceName, String tableName, List<Map<String, Map<String, Object>>> list, RequestContext requestContext) {

    Session session = connectionManager.getSession(keyspaceName);
    BatchStatement batchStatement = new BatchStatement();
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "Cassandra Service batchUpdate method started at ==" + startTime, LoggerEnum.INFO);
    Response response = new Response();
    ResultSet resultSet = null;
    try {
      for (Map<String, Map<String, Object>> record : list) {
        Map<String, Object> primaryKey = record.get(JsonKey.PRIMARY_KEY);
        Map<String, Object> nonPKRecord = record.get(JsonKey.NON_PRIMARY_KEY);
        batchStatement.add(
            CassandraUtil.createUpdateQuery(primaryKey, nonPKRecord, keyspaceName, tableName));
      }
      resultSet = session.execute(batchStatement);
      response.put(Constants.RESPONSE, Constants.SUCCESS);
    } catch (Exception ex) {
      ProjectLogger.log("Cassandra Batch Update failed " + ex.getMessage(), ex);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("batchUpdate", startTime);
    return response;
  }

  private void logQueryElapseTime(String operation, long startTime) {

    long stopTime = System.currentTimeMillis();
    long elapsedTime = stopTime - startTime;
    String message =
        "Cassandra operation {0} started at {1} and completed at {2}. Total time elapsed is {3}.";
    MessageFormat mf = new MessageFormat(message);
    ProjectLogger.log(
        mf.format(new Object[] {operation, startTime, stopTime, elapsedTime}), LoggerEnum.PERF_LOG);
  }

  @Override
  public Response getRecordsByIndexedProperty(
          String keyspaceName, String tableName, String propertyName, Object propertyValue, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "CassandraOperationImpl:getRecordsByIndexedProperty called at " + startTime,
        LoggerEnum.INFO);
    Response response = new Response();
    try {
      Select selectQuery = QueryBuilder.select().all().from(keyspaceName, tableName);
      selectQuery.where().and(eq(propertyName, propertyValue));
      selectQuery.allowFiltering();
      if (null != selectQuery) ProjectLogger.logQuery(selectQuery.getQueryString(), requestContext);
      ResultSet results =
          connectionManager.getSession(keyspaceName).execute(selectQuery.allowFiltering());
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(
          "CassandraOperationImpl:getRecordsByIndexedProperty: "
              + Constants.EXCEPTION_MSG_FETCH
              + tableName
              + " : "
              + e.getMessage(),
          e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getRecordsByIndexedProperty", startTime);
    return response;
  }

  @Override
  public void deleteRecord(
          String keyspaceName, String tableName, Map<String, String> compositeKeyMap, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "CassandraOperationImpl: deleteRecord by composite key called at " + startTime,
        LoggerEnum.INFO);
    try {
      Delete delete = QueryBuilder.delete().from(keyspaceName, tableName);
      Delete.Where deleteWhere = delete.where();
      compositeKeyMap
          .entrySet()
          .stream()
          .forEach(
              x -> {
                Clause clause = eq(x.getKey(), x.getValue());
                deleteWhere.and(clause);
              });
      ProjectLogger.logQuery(deleteWhere.getQueryString(), requestContext);
      connectionManager.getSession(keyspaceName).execute(deleteWhere);
    } catch (Exception e) {
      ProjectLogger.log(
          "CassandraOperationImpl: deleteRecord by composite key. "
              + Constants.EXCEPTION_MSG_DELETE
              + tableName
              + " : "
              + e.getMessage(),
          e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("deleteRecordByCompositeKey", startTime);
  }

  @Override
  public boolean deleteRecords(String keyspaceName, String tableName, List<String> identifierList, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ResultSet resultSet;
    ProjectLogger.log(
        "CassandraOperationImpl: deleteRecords called at " + startTime, LoggerEnum.INFO);
    try {
      Delete delete = QueryBuilder.delete().from(keyspaceName, tableName);
      Delete.Where deleteWhere = delete.where();
      Clause clause = QueryBuilder.in(JsonKey.ID, identifierList);
      deleteWhere.and(clause);
      ProjectLogger.logQuery(deleteWhere.getQueryString(), requestContext);
      resultSet = connectionManager.getSession(keyspaceName).execute(deleteWhere);
    } catch (Exception e) {
      ProjectLogger.log(
          "CassandraOperationImpl: deleteRecords by list of primary key. "
              + Constants.EXCEPTION_MSG_DELETE
              + tableName
              + " : "
              + e.getMessage(),
          e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("deleteRecords", startTime);
    return resultSet.wasApplied();
  }

  @Override
  public Response getRecordsByCompositeKey(
          String keyspaceName, String tableName, Map<String, Object> compositeKeyMap, RequestContext requestContext) {
    long startTime = System.currentTimeMillis();
    ProjectLogger.log(
        "CassandraOperationImpl: getRecordsByCompositeKey called at " + startTime, LoggerEnum.INFO);
    Response response = new Response();
    try {
      Builder selectBuilder = QueryBuilder.select().all();
      Select selectQuery = selectBuilder.from(keyspaceName, tableName);
      Where selectWhere = selectQuery.where();
      for (Entry<String, Object> entry : compositeKeyMap.entrySet()) {
        Clause clause = eq(entry.getKey(), entry.getValue());
        selectWhere.and(clause);
      }
      ProjectLogger.logQuery(selectQuery.getQueryString(), requestContext);
      ResultSet results = connectionManager.getSession(keyspaceName).execute(selectQuery);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(
          "CassandraOperationImpl:getRecordsByCompositeKey: "
              + Constants.EXCEPTION_MSG_FETCH
              + tableName
              + " : "
              + e.getMessage());
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    logQueryElapseTime("getRecordsByCompositeKey", startTime);
    return response;
  }

  @Override
  public Response getRecords(
          String keyspace, String table, Map<String, Object> filters, List<String> fields, RequestContext requestContext) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void applyOperationOnRecordsAsync(
          String keySpace,
          String table,
          Map<String, Object> filters,
          List<String> fields,
          FutureCallback<ResultSet> callback, RequestContext requestContext) {
    // TODO Auto-generated method stub

  }

  @Override
  public Response searchValueInList(String keyspace, String tableName, String key, String value, RequestContext requestContext) {
    return searchValueInList(keyspace, tableName, key, value, null, requestContext);
  }

  @Override
  public Response searchValueInList(
          String keyspace,
          String tableName,
          String key,
          String value,
          Map<String, Object> propertyMap, RequestContext requestContext) {
    Select selectQuery = QueryBuilder.select().all().from(keyspace, tableName);
    Clause clause = QueryBuilder.contains(key, value);
    selectQuery.where(clause);
    if (MapUtils.isNotEmpty(propertyMap)) {
      for (Entry<String, Object> entry : propertyMap.entrySet()) {
        if (entry.getValue() instanceof List) {
          List<Object> list = (List) entry.getValue();
          if (null != list) {
            Object[] propertyValues = list.toArray(new Object[list.size()]);
            Clause clauseList = QueryBuilder.in(entry.getKey(), propertyValues);
            selectQuery.where(clauseList);
          }
        } else {
          Clause clauseMap = eq(entry.getKey(), entry.getValue());
          selectQuery.where(clauseMap);
        }
      }
    }
    ProjectLogger.logQuery(selectQuery.getQueryString(), requestContext);
    ResultSet resultSet = connectionManager.getSession(keyspace).execute(selectQuery);
    Response response = CassandraUtil.createResponse(resultSet);
    return response;
  }

}
