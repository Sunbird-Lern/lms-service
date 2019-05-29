package org.sunbird.learner.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sunbird.common.ElasticSearchUtil;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.HttpUtil;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.ProjectUtil.OrgStatus;
import org.sunbird.common.models.util.PropertiesCache;
import org.sunbird.common.quartz.scheduler.SchedulerManager;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.CassandraConnectionManager;
import org.sunbird.helper.CassandraConnectionMngrFactory;

/**
 * Utility class for actors
 *
 * @author arvind .
 */
public final class Util {

  public static final Map<String, DbInfo> dbInfoMap = new HashMap<>();
  public static final int RECOMENDED_LIST_SIZE = 10;
  public static final int DEFAULT_ELASTIC_DATA_LIMIT = 10000;
  public static final String KEY_SPACE_NAME = "sunbird";
  private static Properties prop = new Properties();
  private static Map<String, String> headers = new HashMap<>();
  private static Map<Integer, List<Integer>> orgStatusTransition = new HashMap<>();
  private static ObjectMapper mapper = new ObjectMapper();

  static {
    loadPropertiesFile();
    initializeOrgStatusTransition();
    initializeDBProperty();
    // EkStep HttpClient headers init
    headers.put("content-type", "application/json");
    headers.put("accept", "application/json");
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                SchedulerManager.getInstance();
              }
            })
        .start();
  }

  private Util() {}

  /**
   * This method will a map of organization state transaction. which will help us to move the
   * organization status from one Valid state to another state.
   */
  private static void initializeOrgStatusTransition() {
    orgStatusTransition.put(
        OrgStatus.ACTIVE.getValue(),
        Arrays.asList(
            OrgStatus.ACTIVE.getValue(),
            OrgStatus.INACTIVE.getValue(),
            OrgStatus.BLOCKED.getValue(),
            OrgStatus.RETIRED.getValue()));
    orgStatusTransition.put(
        OrgStatus.INACTIVE.getValue(),
        Arrays.asList(OrgStatus.ACTIVE.getValue(), OrgStatus.INACTIVE.getValue()));
    orgStatusTransition.put(
        OrgStatus.BLOCKED.getValue(),
        Arrays.asList(
            OrgStatus.ACTIVE.getValue(),
            OrgStatus.BLOCKED.getValue(),
            OrgStatus.RETIRED.getValue()));
    orgStatusTransition.put(
        OrgStatus.RETIRED.getValue(), Arrays.asList(OrgStatus.RETIRED.getValue()));
    orgStatusTransition.put(
        null,
        Arrays.asList(
            OrgStatus.ACTIVE.getValue(),
            OrgStatus.INACTIVE.getValue(),
            OrgStatus.BLOCKED.getValue(),
            OrgStatus.RETIRED.getValue()));
  }

  /** This method will initialize the cassandra data base property */
  private static void initializeDBProperty() {
    // setting db info (keyspace , table) into static map
    // this map will be used during cassandra data base interaction.
    // this map will have each DB name and it's corresponding keyspace and table
    // name.
    dbInfoMap.put(JsonKey.LEARNER_COURSE_DB, getDbInfoObject(KEY_SPACE_NAME, "user_courses"));
    dbInfoMap.put(
        JsonKey.LEARNER_CONTENT_DB, getDbInfoObject(KEY_SPACE_NAME, "content_consumption"));
    dbInfoMap.put(
        JsonKey.COURSE_MANAGEMENT_DB, getDbInfoObject(KEY_SPACE_NAME, "course_management"));
    dbInfoMap.put(JsonKey.USER_DB, getDbInfoObject(KEY_SPACE_NAME, "user"));
    dbInfoMap.put(JsonKey.USER_AUTH_DB, getDbInfoObject(KEY_SPACE_NAME, "user_auth"));
    dbInfoMap.put(JsonKey.PAGE_MGMT_DB, getDbInfoObject(KEY_SPACE_NAME, "page_management"));
    dbInfoMap.put(JsonKey.PAGE_SECTION_DB, getDbInfoObject(KEY_SPACE_NAME, "page_section"));
    dbInfoMap.put(JsonKey.SECTION_MGMT_DB, getDbInfoObject(KEY_SPACE_NAME, "page_section"));

    dbInfoMap.put(JsonKey.COURSE_BATCH_DB, getDbInfoObject(KEY_SPACE_NAME, "course_batch"));
    dbInfoMap.put(
        JsonKey.COURSE_PUBLISHED_STATUS, getDbInfoObject(KEY_SPACE_NAME, "course_publish_status"));
    dbInfoMap.put(JsonKey.REPORT_TRACKING_DB, getDbInfoObject(KEY_SPACE_NAME, "report_tracking"));

    dbInfoMap.put(JsonKey.CLIENT_INFO_DB, getDbInfoObject(KEY_SPACE_NAME, "client_info"));
    dbInfoMap.put(JsonKey.SYSTEM_SETTINGS_DB, getDbInfoObject(KEY_SPACE_NAME, "system_settings"));
  }

  /**
   * This method will take org current state and next state and check is it possible to move
   * organization from current state to next state if possible to move then return true else false.
   *
   * @param currentState String
   * @param nextState String
   * @return boolean
   */
  @SuppressWarnings("rawtypes")
  public static boolean checkOrgStatusTransition(Integer currentState, Integer nextState) {
    List list = orgStatusTransition.get(currentState);
    if (null == list) {
      return false;
    }
    return list.contains(nextState);
  }

  /**
   * This method will check the cassandra data base connection. first it will try to established the
   * data base connection from provided environment variable , if environment variable values are
   * not set then connection will be established from property file.
   */
  public static void checkCassandraDbConnections(String keySpace) {

    PropertiesCache propertiesCache = PropertiesCache.getInstance();

    String cassandraMode = propertiesCache.getProperty(JsonKey.SUNBIRD_CASSANDRA_MODE);
    if (StringUtils.isBlank(cassandraMode)
        || cassandraMode.equalsIgnoreCase(JsonKey.EMBEDDED_MODE)) {

      // configure the Embedded mode and return true here ....
      CassandraConnectionManager cassandraConnectionManager =
          CassandraConnectionMngrFactory.getObject(cassandraMode);
      boolean result =
          cassandraConnectionManager.createConnection(null, null, null, null, keySpace);
      if (result) {
        ProjectLogger.log(
            "CONNECTION CREATED SUCCESSFULLY FOR IP:" + " : KEYSPACE :" + keySpace,
            LoggerEnum.INFO.name());
      } else {
        ProjectLogger.log("CONNECTION CREATION FAILED FOR IP: " + " : KEYSPACE :" + keySpace);
      }

    } else if (cassandraMode.equalsIgnoreCase(JsonKey.STANDALONE_MODE)) {
      if (readConfigFromEnv(keySpace)) {
        ProjectLogger.log("db connection is created from System env variable.");
        return;
      }
      CassandraConnectionManager cassandraConnectionManager =
          CassandraConnectionMngrFactory.getObject(JsonKey.STANDALONE_MODE);
      String[] ipList = prop.getProperty(JsonKey.DB_IP).split(",");
      String[] portList = prop.getProperty(JsonKey.DB_PORT).split(",");
      // String[] keyspaceList = prop.getProperty(JsonKey.DB_KEYSPACE).split(",");

      String userName = prop.getProperty(JsonKey.DB_USERNAME);
      String password = prop.getProperty(JsonKey.DB_PASSWORD);
      for (int i = 0; i < ipList.length; i++) {
        String ip = ipList[i];
        String port = portList[i];
        // Reading the same keyspace which is passed in the method
        // String keyspace = keyspaceList[i];

        try {

          boolean result =
              cassandraConnectionManager.createConnection(ip, port, userName, password, keySpace);
          if (result) {
            ProjectLogger.log(
                "CONNECTION CREATED SUCCESSFULLY FOR IP: " + ip + " : KEYSPACE :" + keySpace,
                LoggerEnum.INFO.name());
          } else {
            ProjectLogger.log(
                "CONNECTION CREATION FAILED FOR IP: " + ip + " : KEYSPACE :" + keySpace);
          }

        } catch (ProjectCommonException ex) {
          ProjectLogger.log(ex.getMessage(), ex);
        }
      }
    }
  }

  /**
   * This method will read the configuration from System variable.
   *
   * @return boolean
   */
  public static boolean readConfigFromEnv(String keyspace) {
    boolean response = false;
    String ips = System.getenv(JsonKey.SUNBIRD_CASSANDRA_IP);
    String envPort = System.getenv(JsonKey.SUNBIRD_CASSANDRA_PORT);
    CassandraConnectionManager cassandraConnectionManager =
        CassandraConnectionMngrFactory.getObject(JsonKey.STANDALONE_MODE);

    if (StringUtils.isBlank(ips) || StringUtils.isBlank(envPort)) {
      ProjectLogger.log("Configuration value is not coming form System variable.");
      return response;
    }
    String[] ipList = ips.split(",");
    String[] portList = envPort.split(",");
    String userName = System.getenv(JsonKey.SUNBIRD_CASSANDRA_USER_NAME);
    String password = System.getenv(JsonKey.SUNBIRD_CASSANDRA_PASSWORD);
    for (int i = 0; i < ipList.length; i++) {
      String ip = ipList[i];
      String port = portList[i];
      try {
        boolean result =
            cassandraConnectionManager.createConnection(ip, port, userName, password, keyspace);
        if (result) {
          response = true;
          ProjectLogger.log(
              "CONNECTION CREATED SUCCESSFULLY FOR IP: " + ip + " : KEYSPACE :" + keyspace,
              LoggerEnum.INFO.name());
        } else {
          ProjectLogger.log(
              "CONNECTION CREATION FAILED FOR IP: " + ip + " : KEYSPACE :" + keyspace,
              LoggerEnum.INFO.name());
        }
      } catch (ProjectCommonException ex) {
        ProjectLogger.log(
            "Util:readConfigFromEnv: Exception occurred with message = " + ex.getMessage(),
            LoggerEnum.ERROR);
      }
    }
    if (!response) {
      throw new ProjectCommonException(
          ResponseCode.invaidConfiguration.getErrorCode(),
          ResponseCode.invaidConfiguration.getErrorCode(),
          ResponseCode.SERVER_ERROR.hashCode());
    }
    return response;
  }

  /** This method will load the db config properties file. */
  private static void loadPropertiesFile() {

    InputStream input = null;

    try {
      input = Util.class.getClassLoader().getResourceAsStream("dbconfig.properties");
      // load a properties file
      prop.load(input);
    } catch (IOException ex) {
      ProjectLogger.log(ex.getMessage(), ex);
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          ProjectLogger.log(e.getMessage(), e);
        }
      }
    }
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
   * This method will take the map<String,Object> and list<Stirng> of keys. it will remove all the
   * keys from the source map.
   *
   * @param map Map<String, Object>
   * @param keys List<String>
   */
  public static void removeAttributes(Map<String, Object> map, List<String> keys) {

    if (null != map && null != keys) {
      for (String key : keys) {
        map.remove(key);
      }
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
   * This method will make a call to EKStep content search api and final response will be appended
   * with same requested map, with key "contents". Requester can read this key to collect the
   * response.
   *
   * @param section String, Object>
   */
  public static void getContentData(Map<String, Object> section) {
    String response = "";
    JSONObject data;
    JSONObject jObject;
    try {
      String baseSearchUrl = ProjectUtil.getConfigValue(JsonKey.SEARCH_SERVICE_API_BASE_URL);
      headers.put(
          JsonKey.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.EKSTEP_AUTHORIZATION));
      if (StringUtils.isBlank(headers.get(JsonKey.AUTHORIZATION))) {
        headers.put(
            JsonKey.AUTHORIZATION,
            PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_AUTHORIZATION));
      }
      response =
          HttpUtil.sendPostRequest(
              baseSearchUrl
                  + PropertiesCache.getInstance().getProperty(JsonKey.EKSTEP_CONTENT_SEARCH_URL),
              (String) section.get(JsonKey.SEARCH_QUERY),
              headers);
      jObject = new JSONObject(response);
      data = jObject.getJSONObject(JsonKey.RESULT);
      JSONArray contentArray = data.getJSONArray(JsonKey.CONTENT);
      section.put(JsonKey.CONTENTS, mapper.readValue(contentArray.toString(), Object[].class));
    } catch (IOException | JSONException e) {
      ProjectLogger.log(e.getMessage(), e);
    }
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

  private static Map<String, Object> elasticSearchComplexSearch(
      Map<String, Object> filters, String index, String type) {

    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filters);

    return ElasticSearchUtil.complexSearch(searchDTO, index, type);
  }

  public static void initializeContext(Request actorMessage, String env) {

    ExecutionContext context = ExecutionContext.getCurrent();
    Map<String, Object> requestContext = null;
    if (actorMessage.getContext().get(JsonKey.TELEMETRY_CONTEXT) != null) {
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
      env = StringUtils.isNotBlank(env) ? env : "N/A";
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

      if (JsonKey.USER.equalsIgnoreCase(
          (String) actorMessage.getContext().get(JsonKey.ACTOR_TYPE))) {
        // assign rollup of user ...
        Map<String, Object> result =
            ElasticSearchUtil.getDataByIdentifier(
                ProjectUtil.EsIndex.sunbird.getIndexName(),
                EsType.user.getTypeName(),
                (String) actorMessage.getContext().get(JsonKey.REQUESTED_BY));
        if (result != null) {
          String rootOrgId = (String) result.get(JsonKey.ROOT_ORG_ID);

          if (StringUtils.isNotBlank(rootOrgId)) {
            Map<String, String> rollup = new HashMap<>();

            rollup.put("l1", rootOrgId);
            requestContext.put(JsonKey.ROLLUP, rollup);
          }
        }
      }
      context.setRequestContext(requestContext);
      // and global context will be set at the time of creation of thread local
      // automatically ...
    }
  }

  public static String getKeyFromContext(String key, Request actorMessage) {
    return actorMessage.getContext() != null && actorMessage.getContext().containsKey(key)
        ? (String) actorMessage.getContext().get(key)
        : "N/A";
  }

  public static void initializeContextForSchedulerJob(
      String actorType, String actorId, String environment) {

    ExecutionContext context = ExecutionContext.getCurrent();
    Map<String, Object> requestContext = new HashMap<>();
    requestContext.put(JsonKey.CHANNEL, JsonKey.DEFAULT_ROOT_ORG_ID);
    requestContext.put(JsonKey.ACTOR_ID, actorId);
    requestContext.put(JsonKey.ACTOR_TYPE, actorType);
    requestContext.put(JsonKey.ENV, environment);
    context.setRequestContext(requestContext);
  }
}
