package org.sunbird.cassandraimpl;

import com.datastax.driver.core.*;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Update;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.CassandraUtil;
import org.sunbird.common.Constants;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;

public class CassandraDACImpl extends CassandraOperationImpl {
    
      @Override
  public Response getBlobAsText(RequestContext requestContext, String keySpace, String table, Map<String, Object> filters, List<String> properties) {
    Response response = new Response();
    Session session = connectionManager.getSession(keySpace);

    StringBuilder sb = new StringBuilder();
    if (null != properties && !properties.isEmpty()) {
      sb.append("select contentid, ");
      StringBuilder selectFields = new StringBuilder();
      for (String property : properties) {
        selectFields.append("blobAsText(").append(property).append(") as " ).append(property);
        selectFields.append(", ");
      }
      sb.append(StringUtils.removeEnd(selectFields.toString(), ", "));
      sb.append(" from " + keySpace + "." + table );
    }

    StringBuilder where = new StringBuilder();
    if (null!= filters && !filters.isEmpty()) {
      where.append(" where ");
      for (Map.Entry<String, Object> filter : filters.entrySet()) {
        Object value = filter.getValue();
        if (value instanceof List)
          where.append(filter.getKey()).append(" in ").append("?");
        else
          where.append(filter.getKey()).append(" = ").append("?");

        where.append(" and ");
      }
      sb.append(StringUtils.removeEnd(where.toString(), " and "));
    }

    PreparedStatement ps = session.prepare(sb.toString());
    BoundStatement bound = ps.bind(filters.values().toArray());

    logger.debug(requestContext, ps.getQueryString());

        try {
      ResultSet results = session.execute(bound);
      response = CassandraUtil.createResponse(results);

    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + table + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
              ResponseCode.SERVER_ERROR.getErrorCode(),
              ResponseCode.SERVER_ERROR.getErrorMessage(),
              ResponseCode.SERVER_ERROR.getResponseCode());
    }
    return response;
  }
    
    @Override
  public Response getRecords(
            RequestContext requestContext, String keySpace, String table, Map<String, Object> filters, List<String> fields) {
    Response response = new Response();
    Session session = connectionManager.getSession(keySpace);
    try {
      Select select;
      if (CollectionUtils.isNotEmpty(fields)) {
        select = QueryBuilder.select((String[]) fields.toArray()).from(keySpace, table);
      } else {
        select = QueryBuilder.select().all().from(keySpace, table);
      }

      if (MapUtils.isNotEmpty(filters)) {
        Select.Where where = select.where();
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
          Object value = filter.getValue();
          if (value instanceof List) {
            where = where.and(QueryBuilder.in(filter.getKey(), ((List) filter.getValue())));
          } else {
            where = where.and(QueryBuilder.eq(filter.getKey(), filter.getValue()));
          }
        }
      }

      ResultSet results = null;
      logger.debug(requestContext, select.getQueryString());
      results = session.execute(select);
      response = CassandraUtil.createResponse(results);
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + table + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    return response;
  }

  public void applyOperationOnRecordsAsync(
          RequestContext requestContext, String keySpace,
          String table,
          Map<String, Object> filters,
          List<String> fields,
          FutureCallback<ResultSet> callback) {
    Session session = connectionManager.getSession(keySpace);
    try {
      Select select;
      if (CollectionUtils.isNotEmpty(fields)) {
        select = QueryBuilder.select((String[]) fields.toArray()).from(keySpace, table);
      } else {
        select = QueryBuilder.select().all().from(keySpace, table);
      }

      if (MapUtils.isNotEmpty(filters)) {
        Select.Where where = select.where();
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
          Object value = filter.getValue();
          if (value instanceof List) {
            where = where.and(QueryBuilder.in(filter.getKey(), ((List) filter.getValue())));
          } else {
            where = where.and(QueryBuilder.eq(filter.getKey(), filter.getValue()));
          }
        }
      }
      logger.debug(requestContext, select.getQueryString());
      ResultSetFuture future = session.executeAsync(select);
      Futures.addCallback(future, callback, Executors.newFixedThreadPool(1));
    } catch (Exception e) {
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + table + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
  }

  public Response updateAddMapRecord(
          RequestContext requestContext, String keySpace,
          String table,
          Map<String, Object> primaryKey,
          String column,
          String key,
          Object value) {
    return updateMapRecord(requestContext, keySpace, table, primaryKey, column, key, value, true);
  }

  public Response updateRemoveMapRecord(
          RequestContext requestContext, String keySpace, String table, Map<String, Object> primaryKey, String column, String key) {
    return updateMapRecord(requestContext, keySpace, table, primaryKey, column, key, null, false);
  }

  public Response updateMapRecord(
          RequestContext requestContext, String keySpace,
          String table,
          Map<String, Object> primaryKey,
          String column,
          String key,
          Object value,
          boolean add) {
    Update update = QueryBuilder.update(keySpace, table);
    if (add) {
      update.with(QueryBuilder.put(column, key, value));
    } else {
      update.with(QueryBuilder.remove(column, key));
    }
    if (MapUtils.isEmpty(primaryKey)) {
      ProjectLogger.log(
          Constants.EXCEPTION_MSG_FETCH + table + " : primary key is a must for update call",
          LoggerEnum.ERROR.name());
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    Update.Where where = update.where();
    for (Map.Entry<String, Object> filter : primaryKey.entrySet()) {
      Object filterValue = filter.getValue();
      if (filterValue instanceof List) {
        where = where.and(QueryBuilder.in(filter.getKey(), ((List) filter.getValue())));
      } else {
        where = where.and(QueryBuilder.eq(filter.getKey(), filter.getValue()));
      }
    }
    try {
      Response response = new Response();
      ProjectLogger.log("Remove Map-Key Query: " + update.toString(), LoggerEnum.INFO);
      logger.debug(requestContext, update.getQueryString());
      connectionManager.getSession(keySpace).execute(update);
      response.put(Constants.RESPONSE, Constants.SUCCESS);
      return response;
    } catch (Exception e) {
      e.printStackTrace();
      ProjectLogger.log(Constants.EXCEPTION_MSG_FETCH + table + " : " + e.getMessage(), e);
      throw new ProjectCommonException(
          ResponseCode.SERVER_ERROR.getErrorCode(),
          ResponseCode.SERVER_ERROR.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
  }
}
