package org.sunbird.learner.util;

import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.TableNameUtil;
import org.sunbird.common.request.Request;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 * Utility class for actors
 *
 * @author arvind .
 */
public final class Util {

  public static final Map<String, DbInfo> dbInfoMap = new HashMap<>();
  public static final int RECOMENDED_LIST_SIZE = 10;
  public static final int DEFAULT_ELASTIC_DATA_LIMIT = 10000;
  private static Properties prop = new Properties();
  private static LoggerUtil logger = new LoggerUtil(Util.class);

  static {
    loadPropertiesFile();
    initializeDBProperty();
  }

  private Util() {}

  /** This method will initialize the cassandra data base property */
  private static void initializeDBProperty() {
    dbInfoMap.put(
        JsonKey.LEARNER_COURSE_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.USER_ENROLLMENTS_TABLENAME));
    dbInfoMap.put(
        JsonKey.LEARNER_CONTENT_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.USER_CONTENT_CONSUMPTION_TABLENAME));
    dbInfoMap.put(
        JsonKey.COURSE_MANAGEMENT_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.COURSE_MANAGEMENT_TABLENAME));
    dbInfoMap.put(JsonKey.PAGE_MGMT_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.PAGE_MANAGEMENT_TABLENAME));
    dbInfoMap.put(JsonKey.PAGE_SECTION_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.PAGE_SECTION_TABLENAME));
    dbInfoMap.put(JsonKey.SECTION_MGMT_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.PAGE_SECTION_TABLENAME));
    dbInfoMap.put(JsonKey.ASSESSMENT_EVAL_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.ASSESSMENT_EVAL_TABLENAME));
    dbInfoMap.put(JsonKey.ASSESSMENT_ITEM_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.ASSESSMENT_ITEM_TABLENAME));

    dbInfoMap.put(
        JsonKey.BULK_OP_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.BULK_UPLOAD_PROCESS_TABLENAME));
    dbInfoMap.put(JsonKey.COURSE_BATCH_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.COURSE_BATCH_TABLENAME));
    dbInfoMap.put(JsonKey.CLIENT_INFO_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.CLIENT_INFO_TABLENAME));
    dbInfoMap.put(JsonKey.USER_AUTH_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_KEYSPACE), TableNameUtil.USER_AUTH_TABLENAME));
    dbInfoMap.put(
        JsonKey.SUNBIRD_COURSE_DIALCODES_DB,
        getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.DIALCODE_KEYSPACE), TableNameUtil.DIALCODE_IMAGES_TABLENAME));
    dbInfoMap.put(
            JsonKey.GROUP_ACTIVITY_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.USER_ACTIVITY_AGG_TABLENAME));
    dbInfoMap.put(
            JsonKey.ASSESSMENT_AGGREGATOR_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.ASSESSMENT_AGGREGATOR_TABLENAME));
    dbInfoMap.put(JsonKey.USER_ENROLMENTS_DB, getDbInfoObject(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_COURSE_KEYSPACE), TableNameUtil.USER_ENROLMENTS_TABLENAME));
  }

  /**
   * This method will check the cassandra data base connection. first it will try to established the
   * data base connection from provided environment variable , if environment variable values are
   * not set then connection will be established from property file.
   */
  public static void checkCassandraDbConnections() {
    if (readConfigFromEnv()) {
      logger.debug(null, "db connection is created from System env variable.");
      return;
    }
    CassandraConnectionManager cassandraConnectionManager =
            CassandraConnectionMngrFactory.getInstance();
    String[] ipList = prop.getProperty(JsonKey.DB_IP).split(",");
    cassandraConnectionManager.createConnection(ipList);
  }

  /** This method will load the db config properties file. */
  private static void loadPropertiesFile() {

    InputStream input = null;

    try {
      input = Util.class.getClassLoader().getResourceAsStream("dbconfig.properties");
      // load a properties file
      prop.load(input);
    } catch (IOException ex) {
      logger.error(null, ex.getMessage(), ex);
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          logger.error(null, e.getMessage(), e);
        }
      }
    }
  }

  /**
   * This method will read the configuration from System variable.
   *
   * @return boolean
   */
  public static boolean readConfigFromEnv() {
    String ips = System.getenv(JsonKey.SUNBIRD_CASSANDRA_IP);
    String envPort = System.getenv(JsonKey.SUNBIRD_CASSANDRA_PORT);
    CassandraConnectionManager cassandraConnectionManager =
            CassandraConnectionMngrFactory.getInstance();

    if (StringUtils.isBlank(ips) || StringUtils.isBlank(envPort)) {
      logger.debug(null, "Configuration value is not coming form System variable.");
      return false;
    }
    String[] ipList = ips.split(",");
    cassandraConnectionManager.createConnection(ipList);
    return true;
  }

  public static String getProperty(String key) {
    return prop.getProperty(key);
  }

  private static DbInfo getDbInfoObject(String keySpace, String table) {

    DbInfo dbInfo = new DbInfo();

    dbInfo.setKeySpace(keySpace);
    dbInfo.setTableName(table);

    return dbInfo;
  }

  /** class to hold cassandra db info. */
  public static class DbInfo {
    private String keySpace;
    private String tableName;
    private String userName;
    private String password;
    private String ip;
    private String port;

    /**
     * @param keySpace
     * @param tableName
     * @param userName
     * @param password
     */
    DbInfo(
        String keySpace,
        String tableName,
        String userName,
        String password,
        String ip,
        String port) {
      this.keySpace = keySpace;
      this.tableName = tableName;
      this.userName = userName;
      this.password = password;
      this.ip = ip;
      this.port = port;
    }

    /** No-arg constructor */
    DbInfo() {}

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof DbInfo) {
        DbInfo ob = (DbInfo) obj;
        if (this.ip.equals(ob.getIp())
            && this.port.equals(ob.getPort())
            && this.keySpace.equals(ob.getKeySpace())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 1;
    }

    public String getKeySpace() {
      return keySpace;
    }

    public void setKeySpace(String keySpace) {
      this.keySpace = keySpace;
    }

    public String getTableName() {
      return tableName;
    }

    public void setTableName(String tableName) {
      this.tableName = tableName;
    }

    public String getUserName() {
      return userName;
    }

    public void setUserName(String userName) {
      this.userName = userName;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public String getPort() {
      return port;
    }

    public void setPort(String port) {
      this.port = port;
    }
  }

  /**
   * This method will take searchQuery map and internally it will convert map to SearchDto object.
   *
   * @param searchQueryMap Map<String , Object>
   * @return SearchDTO
   */
  @SuppressWarnings("unchecked")
  public static SearchDTO createSearchDto(Map<String, Object> searchQueryMap) {
    SearchDTO search = new SearchDTO();
    if (searchQueryMap.containsKey(JsonKey.QUERY)) {
      search.setQuery((String) searchQueryMap.get(JsonKey.QUERY));
    }
    if (searchQueryMap.containsKey(JsonKey.QUERY_FIELDS)) {
      search.setQueryFields((List<String>) searchQueryMap.get(JsonKey.QUERY_FIELDS));
    }
    if (searchQueryMap.containsKey(JsonKey.FACETS)) {
      search.setFacets((List<Map<String, String>>) searchQueryMap.get(JsonKey.FACETS));
    }
    if (searchQueryMap.containsKey(JsonKey.FIELDS)) {
      search.setFields((List<String>) searchQueryMap.get(JsonKey.FIELDS));
    }
    if (searchQueryMap.containsKey(JsonKey.FILTERS)) {
      search.getAdditionalProperties().put(JsonKey.FILTERS, searchQueryMap.get(JsonKey.FILTERS));
    }
    if (searchQueryMap.containsKey(JsonKey.EXISTS)) {
      search.getAdditionalProperties().put(JsonKey.EXISTS, searchQueryMap.get(JsonKey.EXISTS));
    }
    if (searchQueryMap.containsKey(JsonKey.NOT_EXISTS)) {
      search
          .getAdditionalProperties()
          .put(JsonKey.NOT_EXISTS, searchQueryMap.get(JsonKey.NOT_EXISTS));
    }
    if (searchQueryMap.containsKey(JsonKey.SORT_BY)) {
      search
          .getSortBy()
          .putAll((Map<? extends String, ? extends String>) searchQueryMap.get(JsonKey.SORT_BY));
    }
    if (searchQueryMap.containsKey(JsonKey.OFFSET)) {
      if ((searchQueryMap.get(JsonKey.OFFSET)) instanceof Integer) {
        search.setOffset((int) searchQueryMap.get(JsonKey.OFFSET));
      } else {
        search.setOffset(((BigInteger) searchQueryMap.get(JsonKey.OFFSET)).intValue());
      }
    }
    if (searchQueryMap.containsKey(JsonKey.LIMIT)) {
      if ((searchQueryMap.get(JsonKey.LIMIT)) instanceof Integer) {
        search.setLimit((int) searchQueryMap.get(JsonKey.LIMIT));
      } else {
        search.setLimit(((BigInteger) searchQueryMap.get(JsonKey.LIMIT)).intValue());
      }
    }
    if (search.getLimit() > DEFAULT_ELASTIC_DATA_LIMIT) {
      search.setLimit(DEFAULT_ELASTIC_DATA_LIMIT);
    }
    if (search.getLimit() + search.getOffset() > DEFAULT_ELASTIC_DATA_LIMIT) {
      search.setLimit(DEFAULT_ELASTIC_DATA_LIMIT - search.getOffset());
    }
    if (searchQueryMap.containsKey(JsonKey.GROUP_QUERY)) {
      search
          .getGroupQuery()
          .addAll(
              (Collection<? extends Map<String, Object>>) searchQueryMap.get(JsonKey.GROUP_QUERY));
    }
    if (searchQueryMap.containsKey(JsonKey.SOFT_CONSTRAINTS)) {
      // Play is converting int value to bigInt so need to cnvert back those data to iny
      // SearchDto soft constraints expect Map<String, Integer>
      Map<String, Integer> constraintsMap = new HashMap<>();
      Set<Entry<String, BigInteger>> entrySet =
          ((Map<String, BigInteger>) searchQueryMap.get(JsonKey.SOFT_CONSTRAINTS)).entrySet();
      Iterator<Entry<String, BigInteger>> itr = entrySet.iterator();
      while (itr.hasNext()) {
        Entry<String, BigInteger> entry = itr.next();
        constraintsMap.put(entry.getKey(), entry.getValue().intValue());
      }
      search.setSoftConstraints(constraintsMap);
    }
    return search;
  }

  /**
   * if Object is null then it will return true else false.
   *
   * @param obj Object
   * @return boolean
   */
  public static boolean isNull(Object obj) {
    return null == obj ? true : false;
  }

  /**
   * if Object is not null then it will return true else false.
   *
   * @param obj Object
   * @return boolean
   */
  public static boolean isNotNull(Object obj) {
    return null != obj ? true : false;
  }

  public static void initializeContext(Request actorMessage, String env, String log_env) {
    Map<String, Object> requestContext = null;
    if ((actorMessage.getContext().get(JsonKey.TELEMETRY_CONTEXT) != null)) {
      // means request context is already set by some other actor ...
      requestContext =
          (Map<String, Object>) actorMessage.getContext().get(JsonKey.TELEMETRY_CONTEXT);
    } else {
      requestContext = new HashMap<>();
      // request level info ...
      Map<String, Object> req = actorMessage.getRequest();
      String requestedBy = (String) req.get(JsonKey.REQUESTED_BY);
      String actorId = getKeyFromContext(JsonKey.ACTOR_ID, actorMessage);
      String actorType = getKeyFromContext(JsonKey.ACTOR_TYPE, actorMessage);
      String appId = getKeyFromContext(JsonKey.APP_ID, actorMessage);
      env = StringUtils.isNotBlank(env) ? env : "";
      String deviceId = getKeyFromContext(JsonKey.DEVICE_ID, actorMessage);
      String channel = getKeyFromContext(JsonKey.CHANNEL, actorMessage);
      requestContext.put(JsonKey.CHANNEL, channel);
      requestContext.put(JsonKey.ACTOR_ID, actorId);
      requestContext.put(JsonKey.ACTOR_TYPE, actorType);
      requestContext.put(JsonKey.APP_ID, appId);
      requestContext.put(JsonKey.ENV, env);
      requestContext.put(JsonKey.REQUEST_TYPE, JsonKey.API_CALL);
      requestContext.put(JsonKey.REQUEST_ID, actorMessage.getRequestId());
      requestContext.put(JsonKey.DEVICE_ID, deviceId);
      requestContext.put(JsonKey.X_AUTH_TOKEN, getKeyFromContext(JsonKey.X_AUTH_TOKEN, actorMessage));

        actorMessage.getContext().putAll(requestContext);
      if (actorMessage.getRequestContext() != null)
        actorMessage.getRequestContext().setEnv(log_env);

      // and global context will be set at the time of creation of thread local
      // automatically ...
    }
  }

  public static String getKeyFromContext(String key, Request actorMessage) {
    return actorMessage.getContext() != null && actorMessage.getContext().containsKey(key)
        ? (String) actorMessage.getContext().get(key)
        : "";
  }

}

@FunctionalInterface
interface ConvertValuesToLowerCase {
  Map<String, String> convertToLowerCase(Map<String, String> map);
}
