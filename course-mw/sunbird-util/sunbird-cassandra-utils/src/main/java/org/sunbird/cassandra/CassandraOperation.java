/** */
package org.sunbird.cassandra;

import com.datastax.driver.core.ResultSet;
import com.google.common.util.concurrent.FutureCallback;
import java.util.List;
import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.RequestContext;

/**
 * @desc this interface will hold functions for cassandra db interaction
 * @author Amit Kumar
 */
public interface CassandraOperation {

  /**
   * @desc This method is used to insert/update record in cassandra db (if primary key exist in
   *     request ,it will update else will insert the record in cassandra db. By default cassandra
   *     insert operation does upsert operation. Upsert means that Cassandra will insert a row if a
   *     primary key does not exist already otherwise if primary key already exists, it will update
   *     that row.)
   * @param keyspaceName String (data base keyspace name)
   * @param tableName String
   * @param request Map<String,Object>(i.e map of column name and their value)
   * @param requestContext
   * @return Response Response
   */
  public Response upsertRecord(String keyspaceName, String tableName, Map<String, Object> request, RequestContext requestContext);

  /**
   * @desc This method is used to insert record in cassandra db
   * @param requestContext
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param request Map<String,Object>(i.e map of column name and their value)
   * @return Response Response
   */
  public Response insertRecord(RequestContext requestContext, String keyspaceName, String tableName, Map<String, Object> request);

  /**
   * @desc This method is used to update record in cassandra db
   * @param requestContext
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param request Map<String,Object>(i.e map of column name and their value)
   * @return Response Response
   */
  public Response updateRecord(RequestContext requestContext, String keyspaceName, String tableName, Map<String, Object> request);

  /**
   * @desc This method is used to delete record in cassandra db by their primary key(identifier)
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param identifier String
   * @param requestContext
   * @return Response Response
   */
  public Response deleteRecord(String keyspaceName, String tableName, String identifier, RequestContext requestContext);

  /**
   * @desc This method is used to delete record in cassandra db by their primary composite key
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param compositeKeyMap Column map for composite primary key
   * @param requestContext
   */
  public void deleteRecord(
          String keyspaceName, String tableName, Map<String, String> compositeKeyMap, RequestContext requestContext);

  /**
   * @desc This method is used to delete one or more records from Cassandra DB corresponding to
   *     given list of primary keys
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param identifierList List of primary keys of records to be deleted
   * @param requestContext
   * @return Status of delete records operation
   */
  public boolean deleteRecords(String keyspaceName, String tableName, List<String> identifierList, RequestContext requestContext);

  /**
   * Fetch records with specified columns (select all if null) for given column name and value.
   *
   * @param requestContext
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param propertyName Column name
   * @param propertyValue Column value
   * @param fields List of columns to be returned in each record
   * @return Response consisting of fetched records
   */
  Response getRecordsByProperty(
          RequestContext requestContext, String keyspaceName,
          String tableName,
          String propertyName,
          Object propertyValue,
          List<String> fields);


  /**
   * Fetch records with specified indexed column
   *
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param propertyName Indexed Column name
   * @param propertyValue Value to be used for matching in select query
   * @param requestContext
   * @return Response consisting of fetched records
   */
  Response getRecordsByIndexedProperty(
          String keyspaceName, String tableName, String propertyName, Object propertyValue, RequestContext requestContext);

  /**
   * @desc This method is used to fetch record based on given parameter list and their values
   * @param requestContext
   * @param keyspaceName String (data base keyspace name)
   * @param tableName String
   * @param propertyMap Map<String,Object> propertyMap)(i.e map of column name and their value)
   * @return Response Response
   */
  public Response getRecordsByProperties(
          RequestContext requestContext, String keyspaceName, String tableName, Map<String, Object> propertyMap);

  /**
   * Fetch records with specified columns (select all if null) for given column map (name, value
   * pairs).
   *
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param propertyMap Map describing columns to be used in where clause of select query.
   * @param fields List of columns to be returned in each record
   * @param requestContext
   * @return Response consisting of fetched records
   */
  Response getRecordsByProperties(
          String keyspaceName, String tableName, Map<String, Object> propertyMap, List<String> fields, RequestContext requestContext);

  /**
   * @desc This method is used to fetch properties value based on id
   * @param keyspaceName String (data base keyspace name)
   * @param tableName String
   * @param id String
   * @param requestContext
   * @param properties String varargs
   * @return Response.
   */
  public Response getPropertiesValueById(
          String keyspaceName, String tableName, String id, RequestContext requestContext, String... properties);

  /**
   * @desc This method is used to fetch all records for table(i.e Select * from tableName)
   * @param requestContext
   * @param keyspaceName String (data base keyspace name)
   * @param tableName String
   * @return Response Response
   */
  public Response getAllRecords(RequestContext requestContext, String keyspaceName, String tableName);

  /**
   * Method to update the record on basis of composite primary key.
   *
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param updateAttributes Column map to be used in set clause of update query
   * @param compositeKey Column map for composite primary key
   * @return Response consisting of update query status
   */
  Response updateRecord(
          RequestContext requestContext, String keyspaceName,
          String tableName,
          Map<String, Object> updateAttributes,
          Map<String, Object> compositeKey);

  Response getRecordByIdentifier(RequestContext requestContext, String keyspaceName, String tableName, Object key, List<String> fields);
  
  /**
   * Method to perform batch insert operation.
   *
   * @param requestContext
   * @param keyspaceName Keyspace name
   * @param tableName Table name
   * @param records List of records in the batch insert operation
   * @return Response indicating status of operation
   */
  Response batchInsert(RequestContext requestContext, String keyspaceName, String tableName, List<Map<String, Object>> records);

  Response getBlobAsText(RequestContext requestContext, String keySpace, String table, Map<String, Object> filters, List<String> properties);

  public Response getRecords(
          RequestContext requestContext, String keyspace, String table, Map<String, Object> filters, List<String> fields);

  public Response getRecordsByCompositeKey(String keyspaceName, String tableName, Map<String, Object> compositeKeyMap, RequestContext requestContext);

  public Response batchUpdate(String keyspaceName, String tableName, List<Map<String, Map<String, Object>>> list, RequestContext requestContext);

  /**
   * Apply callback on cassandra async read call.
   * @param requestContext
   * @param keySpace Keyspace name
   * @param table Table name
   * @param filters Column and value map for filtering
   * @param fields List of columns to be returned in each record
   * @param callback action callback to be applied on resultset when it is returned.
   */
  public void applyOperationOnRecordsAsync(
          RequestContext requestContext, String keySpace,
          String table,
          Map<String, Object> filters,
          List<String> fields,
          FutureCallback<ResultSet> callback);

  /**
   * this method will be used to do CONTAINS query in list
   *
   * @param keyspace
   * @param tableName
   * @param key
   * @param Value
   * @param requestContext
   * @return
   */
  Response searchValueInList(String keyspace, String tableName, String key, String Value, RequestContext requestContext);

  /**
   * this method will be used to do CONTAINS query in list with the AND operations
   *
   * @param keyspace
   * @param tableName
   * @param key
   * @param Value
   * @param propertyMap
   * @param requestContext
   * @return
   */
  Response searchValueInList(
          String keyspace, String tableName, String key, String Value, Map<String, Object> propertyMap, RequestContext requestContext);

  /**
   * @param requestContext
   * @param keySpace
   * @param table
   * @param primaryKey
   * @param column
   * @param key
   * @param value
   * @return
   */
  public Response updateAddMapRecord(
          RequestContext requestContext, String keySpace,
          String table,
          Map<String, Object> primaryKey,
          String column,
          String key,
          Object value);

  /**
   * @param requestContext
   * @param keySpace
   * @param table
   * @param primaryKey
   * @param column
   * @param key
   * @return
   */
  public Response updateRemoveMapRecord(
          RequestContext requestContext, String keySpace, String table, Map<String, Object> primaryKey, String column, String key);

    /**
     * Using QueryBuilder to update the records for seletion and using ifNotExists.
     * @param keyspace
     * @param table
     * @param selectMap
     * @param updateMap
     * @param ifExists
     * @return
     */
  public Response updateRecordV2(RequestContext requestContext, String keyspace, String table, Map<String, Object> selectMap, Map<String, Object> updateMap, boolean ifExists);
}
