package org.sunbird.common.models.util;

import org.apache.commons.beanutils.PropertyUtilsBean;

import java.util.Arrays;
import java.util.List;

/**
 * This class will contains all the key related to request and response.
 *
 * @author Manzarul
 */
public final class JsonKey {
  public static final String ANONYMOUS = "Anonymous";
  public static final String UNAUTHORIZED = "Unauthorized";
  public static final String MW_SYSTEM_HOST = "sunbird_mw_system_host";
  public static final String MW_SYSTEM_PORT = "sunbird_mw_system_port";
  public static final String ACCOUNT_KEY = "sunbird_account_key";
  public static final String ACCOUNT_NAME = "sunbird_account_name";
  public static final String DOWNLOAD_LINK_EXPIRY_TIMEOUT = "download_link_expiry_timeout";
  public static final String SIGNED_URL = "signedUrl";
  public static final String ACTION_NAME = "actionName";
  public static final String ACTION_URL = "actionUrl";
  public static final String ACTIVE = "active";
  public static final String ACTOR_ID = "actorId";
  public static final String ACTOR_SERVICE = "Actor service";
  public static final String ACTOR_TYPE = "actorType";
  public static final String ADD_TYPE = "addType";
  public static final String ADDED_BY = "addedBy";
  public static final String ADDITIONAL_INFO = "ADDITIONAL_INFO";
  public static final String ADDRESS = "address";
  public static final String ADDRESS_LINE1 = "addressLine1";
  public static final String ALL = "all";
  public static final String ALLOWED_LOGIN = "allowedLogin";
  public static final String API_ACCESS = "api_access";
  public static final String API_ACTOR_PROVIDER = "api_actor_provider";
  public static final String API_CALL = "API_CALL";
  public static final String API_ID = "apiId";
  public static final String APP_ICON = "appIcon";
  public static final String APP_MAP = "appMap";
  public static final String APP_SECTIONS = "appSections";
  public static final String ASSESSMENT = "assessment";
  public static final String ASSESSMENT_EVENTS = "assessments";
  public static final String ASSESSMENT_TS = "assessmentTs";
  public static final String ASSESSMENT_EVAL_DB = "assessment_eval_db";
  public static final String ASSESSMENT_ITEM_DB = "assessment_item_db";
  public static final String ASSESSMENT_SCORE = "score";
  public static final String ATTEMPTS = "attempts";
  public static final String ATTEMPT_ID = "attemptId";
  public static final String ASSESSMENT_EVENTS_KEY = "events";
  public static final String ASSESSMENT_ACTOR = "actor";
  public static final String AUTH_WITH_MASTER_KEY = "authWithMasterKey";
  public static final String AUTHORIZATION = "Authorization";
  public static final String AREA_OF_INTEREST = "areaOfInterest";
  public static final String BACKGROUND_ACTOR_PROVIDER = "background_actor_provider";
  public static final String BATCH = "batch";
  public static final String BATCH_ID = "batchId";
  public static final String BEARER = "Bearer ";
  public static final String BODY = "body";
  public static final String BULK_OP_DB = "BulkOpDb";
  public static final String BULK_UPLOAD_BATCH_DATA_SIZE = "bulk_upload_batch_data_size";
  public static final String BULK_USER_UPLOAD = "bulkUserUpload";
  public static final String CASSANDRA_SERVICE = "Cassandra service";
  public static final String CHANNEL = "channel";
  public static final String CHECKS = "checks";
  public static final String CITY = "city";
  public static final String CLASS = "class";
  public static final String CLIENT_INFO_DB = "clientInfo_db";
  public static final String CLIENT_NAME = "clientName";
  public static final String COMPLETENESS = "completeness";
  public static final String CONSUMER = "consumer";
  public static final String CONTACT_DETAILS = "contactDetail";
  public static final String CONTAINER = "container";
  public static final String CONTENT = "content";
  public static final String CONTENT_ID = "contentId";
  public static final String CONTENT_IDS = "contentIds";
  public static final String CONTENT_LIST = "contentList";
  public static final String CONTENT_TYPE = "contentType";
  public static final String CONTENTS = "contents";
  public static final String CONTEXT = "context";
  public static final String CORRELATED_OBJECTS = "correlatedObjects";
  public static final String COUNT = "count";
  public static final String COUNTRY = "country";
  public static final String COUNTRY_CODE = "countryCode";
  public static final String COURSE = "course";
  public static final String COURSE_ADDITIONAL_INFO = "courseAdditionalInfo";
  public static final String COURSE_BATCH_DB = "courseBatchDB";
  public static final String COURSE_CREATED_FOR = "createdFor";
  public static final String COURSE_ENROLL_DATE = "enrolledDate";
  public static final String COURSE_ID = "courseId";
  public static final String COURSE_IDS = "courseIds";
  public static final String COURSE_LOGO_URL = "courseLogoUrl";
  public static final String COURSE_MANAGEMENT_DB = "courseManagement_db";
  public static final String COURSE_NAME = "courseName";
  public static final String COURSE_PROGRESS = "progress";
  public static final String COURSES = "courses";
  public static final String CREATE = "create";
  public static final String CREATED_BY = "createdBy";
  public static final String CREATED_DATE = "createdDate";
  public static final String CRITERIA = "criteria";
  public static final String CURRENT_LOGIN_TIME = "currentLoginTime";
  public static final String CURRENT_STATE = "CURRENT_STATE";

  public static final String COMPASS_API_BASE_URL="compass_api_base_url";

  public static final String CONTENT_SEARCH_URL="content_search_url";
  public static final String COMPOSITE_SEARCH_URL = "composite_search_url";
  public static final String COMPETENCY = "competency";

  public static final String DASHBOARD = "dashboard";
  public static final String DATA = "data";
  public static final String DATE_HISTOGRAM = "DATE_HISTOGRAM";
  public static final String DATE_TIME = "dateTime";
  public static final String DB_IP = "db.ip";
  public static final String DB_KEYSPACE = "db.keyspace";
  public static final String DB_PASSWORD = "db.password";
  public static final String DB_PORT = "db.port";
  public static final String DB_USERNAME = "db.username";
  public static final String DEFAULT_CONSUMER_ID = "internal";
  public static final String DEFAULT_ROOT_ORG_ID = "ORG_001";
  public static final String DEGREE = "degree";
  public static final String DESCRIPTION = "description";
  public static final String DESIGNATION = "designation";
  public static final String DOB = "dob";
  public static final String EDUCATION = "education";
  public static final String EKS = "eks";
  public static final String SEARCH_SERVICE_API_BASE_URL = "sunbird_search_service_api_base_url";
  public static final String ANALYTICS_API_BASE_URL = "sunbird_analytics_api_base_url";
  public static final String EKSTEP_AUTHORIZATION = "ekstep_authorization";
  public static final String EKSTEP_BASE_URL = "ekstep_api_base_url";
  public static final String EKSTEP_CONTENT_SEARCH_URL = "ekstep_content_search_url";
  public static final String EKSTEP_CONTENT_UPDATE_URL = "ekstep.content.update.url";
  public static final String EKSTEP_SERVICE = "Content service";
  public static final String EKSTEP_TAG_API_URL = "ekstep.tag.api.url";
  public static final String EMAIL = "email";
  public static final String EMAIL_REQUEST = "emailReq";
  public static final String EMAIL_SERVER_FROM = "sunbird_mail_server_from_email";
  public static final String EMAIL_SERVER_HOST = "sunbird_mail_server_host";
  public static final String EMAIL_SERVER_PASSWORD = "sunbird_mail_server_password";
  public static final String EMAIL_SERVER_PORT = "sunbird_mail_server_port";
  public static final String EMAIL_SERVER_USERNAME = "sunbird_mail_server_username";
  public static final String EMAIL_TEMPLATE_TYPE = "emailTemplateType";
  public static final String EMAIL_UNIQUE = "emailUnique";
  public static final String EMAIL_VERIFIED = "emailVerified";
  public static final String EMAIL_VERIFIED_UPDATED = "emailVerifiedUpdated";
  public static final String EMBEDDED = "embedded";
  public static final String EMBEDDED_MODE = "embedded";
  public static final String ENC_EMAIL = "encEmail";
  public static final String ENC_PHONE = "encPhone";
  public static final String ENCRYPTION_KEY = "sunbird_encryption_key";
  public static final String END_DATE = "endDate";
  public static final String ENROLLMENT_END_DATE = "enrollmentEndDate";
  public static final String ENROLLMENT_TYPE = "enrollmentType";
  public static final String ENROLMENTTYPE = "enrolmentType";
  public static final String ENV = "env";
  public static final String ERR_TYPE = "errtype";
  public static final String ERROR = "err";
  public static final String ERROR_MSG = "err_msg";
  public static final String ERRORMSG = "errmsg";
  public static final String ES_METRICS_PORT = "es_metrics_port";
  public static final String ES_SERVICE = "Elastic search service";
  public static final String ES_URL = "es_search_url";
  public static final String ESTIMATED_COUNT_REQ = "estimatedCountReq";
  public static final String EVENTS = "events";
  public static final String EXISTS = "exists";
  public static final String EXTERNAL_ID = "externalId";
  public static final String FACETS = "facets";
  public static final String FAILURE = "failure";
  public static final String FAILURE_RESULT = "failureResult";
  public static final String FCM = "fcm";
  public static final String FCM_URL = "fcm.url";
  public static final String FIELD = "field";
  public static final String FIELDS = "fields";
  public static final String FILE = "file";
  public static final String FILTER = "filter";
  public static final String FILTERS = "filters";
  public static final String FIRST_NAME = "firstName";
  public static final String FRAMEWORK = "framework";
  public static final String FROM_EMAIL = "fromEmail";
  public static final String GENDER = "gender";
  public static final String GRADE = "grade";
  public static final String GROUP = "group";
  public static final String GROUPID = "groupId";
  public static final String GROUP_QUERY = "groupQuery";
  public static final String HASH_TAG_ID = "hashtagid";
  public static final String HEADER = "header";
  public static final String Healthy = "healthy";
  public static final String ID = "id";
  public static final String IDENTIFIER = "identifier";
  public static final String INDEX = "index";
  public static final String INFO = "info";
  public static final String INVITE_ONLY = "invite-only";
  public static final String IS_AUTH_REQ = "isAuthReq";
  public static final String IS_DELETED = "isDeleted";
  public static final String IS_ROOT_ORG = "isRootOrg";
  public static final String IS_SSO_ENABLED = "sso.enabled";
  public static final String JOB_NAME = "jobName";
  public static final String JOB_PROFILE = "jobProfile";
  public static final String JOINING_DATE = "joiningDate";
  public static final String KEYWORDS = "keywords";
  public static final String LANGUAGE = "language";
  public static final String LAST_ACCESS_TIME = "lastAccessTime";
  public static final String LAST_COMPLETED_TIME = "lastCompletedTime";
  public static final String LAST_LOGIN_TIME = "lastLoginTime";
  public static final String LAST_LOGOUT_TIME = "lastLogoutTime";
  public static final String LAST_NAME = "lastName";
  public static final String LAST_READ_CONTENT_STATUS = "lastReadContentStatus";
  public static final String LAST_READ_CONTENT_VERSION = "lastReadContentVersion";
  public static final String LAST_READ_CONTENTID = "lastReadContentId";
  public static final String LAST_UPDATED_TIME = "lastUpdatedTime";
  public static final String LEAF_NODE_COUNT = "leafNodesCount";
  public static final String LEARNER_CONTENT_DB = "learnerContent_db";
  public static final String LEARNER_COURSE_DB = "learnerCourse_db";
  public static final String LEARNER_SERVICE = "Learner service";
  public static final String LEVEL = "level";
  public static final String LIMIT = "limit";
  public static final String LIST = "List";
  public static final String LIVE = "Live";
  public static final String LOCATION = "location";
  public static final String LOCATION_IDS = "locationIds";
  public static final String LOG_LEVEL = "logLevel";
  public static final String LOG_TYPE = "logType";
  public static final String LOGIN_ID = "loginId";
  public static final String MAP = "map";
  public static final String MASKED_PHONE = "maskedPhone";
  public static final String MASTER_KEY = "masterKey";
  public static final String MENTORS = "mentors";
  public static final String MESSAGE = "message";
  public static final String MESSAGE_Id = "message_id";
  public static final String MESSAGE_ID = "X-msgId";
  public static final String METHOD = "method";
  public static final String MISSING_FIELDS = "missingFields";
  public static final String MOBILE = "mobile";
  public static final String NAME = "name";
  public static final String NEW_PASSWORD = "newPassword";
  public static final String NOT_EXISTS = "not_exists";
  public static final String NOTE = "note";
  public static final String NULL = "null";
  public static final String OBJECT_IDS = "objectIds";
  public static final String OBJECT_TYPE = "objectType";
  public static final String OFFSET = "offset";
  public static final String ON = "ON";
  public static final String OPEN = "open";
  public static final String OPERATION = "operation";
  public static final String OPERATION_FOR = "operationFor";
  public static final String OPERATION_TYPE = "operationType";
  public static final String ORDER = "order";
  public static final String ORG_CODE = "orgCode";
  public static final String ORG_IMAGE_URL = "orgImageUrl";
  public static final String ORG_NAME = "orgName";
  public static final String ORGANISATION = "organisation";
  public static final String ORGANISATION_ID = "organisationId";
  public static final String ORGANISATION_NAME = "orgName";
  public static final String ORGANISATIONS = "organisations";
  public static final String PAGE = "page";
  public static final String PAGE_ID = "pageId";
  public static final String PAGE_MGMT_DB = "page_mgmt_db";
  public static final String PAGE_NAME = "name";
  public static final String PAGE_SECTION = "page_section";
  public static final String PAGE_SECTION_DB = "page_section_db";
  public static final String PARAMS = "params";
  public static final String PARTICIPANT = "participant";
  public static final String PARTICIPANTS = "participants";
  public static final String PASSWORD = "password";
  public static final String PDATA = "pdata";
  public static final String  PRIMARYCATEGORY = "primaryCategory";
  public static final String PERCENTAGE = "percentage";
  public static final String PHONE = "phone";
  public static final String PHONE_VERIFIED = "phoneVerified";
  public static final String PORTAL_MAP = "portalMap";
  public static final String PORTAL_SECTIONS = "portalSections";
  public static final String PREV_STATE = "PREV_STATE";
  public static final String PRIMARY_KEY_DELIMETER = "##";
  public static final String PRIVATE = "private";
  public static final String PROCESS_END_TIME = "processEndTime";
  public static final String PROCESS_ID = "processId";
  public static final String PROCESS_START_TIME = "processStartTime";
  public static final String PDATA_ID = "telemetry_pdata_id";
  public static final String PROFILE_SUMMARY = "profileSummary";
  public static final String  PROFILE_DETAILS = "profileDetails";
  public static final String PROFILE_VISIBILITY = "profileVisibility";
  public static final String PROFESSIONAL_DETAILS = "professionalDetails";

  public static final String PROGRESS = "progress";
  public static final String PROPS = "props";
  public static final String PROVIDER = "provider";
  public static final String PUBLIC = "public";
  public static final String QUERY = "query";
  public static final String QUERY_FIELDS = "queryFields";
  public static final String RECEIVER_ID = "receiverId";
  public static final String RECIPIENT_EMAILS = "recipientEmails";
  public static final String RECIPIENT_USERIDS = "recipientUserIds";
  public static final String REGISTERED_ORG = "registeredOrg";
  public static final String REGISTERED_ORG_ID = "regOrgId";
  public static final String RELATION = "relation";
  public static final String REPLACE_WITH_ASTERISK = "*";
  public static final String REQUEST = "request";
  public static final String REQUEST_ID = "requestId";
  public static final String REQUEST_TYPE = "requestType";
  public static final String REQUESTED_BY = "requestedBy";
  public static final String RES_MSG_ID = "resmsgId";
  public static final String RESPONSE = "response";
  public static final String RESULT = "result";
  public static final String ROLE = "role";
  public static final String ROLES = "roles";
  public static final String ROLLUP = "rollup";
  public static final String ROOT_ORG_ID = "rootOrgId";
  public static final String SEARCH_QUERY = "searchQuery";
  public static final String SEARCH_TOP_N = "searchTopN";
  public static final String SECTION = "section";
  public static final String SECTION_DATA_TYPE = "sectionDataType";
  public static final String SECTION_DISPLAY = "display";
  public static final String SECTION_ID = "sectionId";
  public static final String SECTION_MGMT_DB = "section_mgmt_db";
  public static final String SECTION_NAME = "name";
  public static final String SECTIONS = "sections";
  public static final String SIZE = "size";
  public static final String SNAPSHOT = "snapshot";
  public static final String SORT = "sort";
  public static final String SORT_BY = "sort_by";
  public static final String SOURCE = "source";
  public static final String SKILLS = "skills";
  public static final String SSO_CLIENT_ID = "sso.client.id";
  public static final String SSO_CLIENT_SECRET = "sso.client.secret";
  public static final String SSO_PASSWORD = "sso.password";
  public static final String SSO_POOL_SIZE = "sso.connection.pool.size";
  public static final String SSO_PUBLIC_KEY = "sunbird_sso_publickey";
  public static final String SSO_REALM = "sso.realm";
  public static final String SSO_URL = "sso.url";
  public static final String SSO_USERNAME = "sso.username";
  public static final String STACKTRACE = "stacktrace";
  public static final String START_DATE = "startDate";
  public static final String START_TIME = "startTime";
  public static final String STATE = "state";
  public static final String STATUS = "status";
  public static final String SUBJECT = "subject";
  public static final String SUCCESS = "SUCCESS";
  public static final String SUCCESS_RESULT = "successResult";
  public static final String SUNBIRD_ALLOWED_LOGIN = "sunbird_allowed_login";
  public static final String SUNBIRD_CASSANDRA_IP = "sunbird_cassandra_host";
  public static final String SUNBIRD_CASSANDRA_MODE = "sunbird_cassandra_mode";
  public static final String SUNBIRD_CASSANDRA_PASSWORD = "sunbird_cassandra_password";
  public static final String SUNBIRD_CASSANDRA_PORT = "sunbird_cassandra_port";
  public static final String SUNBIRD_CASSANDRA_USER_NAME = "sunbird_cassandra_username";
  public static final String SUNBIRD_ENCRYPTION = "sunbird_encryption";
  public static final String SUNBIRD_ENV_LOGO_URL = "sunbird_env_logo_url";
  public static final String SUNBIRD_ES_CHANNEL = "es.channel.name";
  public static final String SUNBIRD_ES_CLUSTER = "sunbird_es_cluster";
  public static final String SUNBIRD_ES_IP = "sunbird_es_host";
  public static final String SUNBIRD_ES_PORT = "sunbird_es_port";
  public static final String SUNBIRD_FCM_ACCOUNT_KEY = "sunbird_fcm_account_key";
  public static final String SUNBIRD_INSTALLATION = "sunbird_installation";
  public static final String SUNBIRD_SSO_CLIENT_ID = "sunbird_sso_client_id";
  public static final String SUNBIRD_SSO_CLIENT_SECRET = "sunbird_sso_client_secret";
  public static final String SUNBIRD_SSO_PASSWORD = "sunbird_sso_password";
  public static final String SUNBIRD_SSO_RELAM = "sunbird_sso_realm";
  public static final String SUNBIRD_SSO_URL = "sunbird_sso_url";
  public static final String SUNBIRD_SSO_USERNAME = "sunbird_sso_username";
  public static final String SUNBIRD_WEB_URL = "sunbird_web_url";
  public static final String SUNBIRD_GET_ORGANISATION_API = "sunbird_search_organisation_api";
  public static final String SUNBIRD_GET_SINGLE_USER_API = "sunbird_read_user_api";
  public static final String SUNBIRD_GET_MULTIPLE_USER_API = "sunbird_search_user_api";
  public static final String TAGS = "tags";
  public static final String TARGET_OBJECT = "targetObject";
  public static final String TELEMETRY_CONTEXT = "TELEMETRY_CONTEXT";
  public static final String TELEMETRY_EVENT_TYPE = "telemetryEventType";
  public static final String TEMPORARY_PASSWORD = "tempPassword";
  public static final String TARGET_TAXONOMY_CATEGORY_4IDS= "targetTaxonomyCategory4Ids";
  public static final String TARGET_TAXONOMY_CATEGORY_3IDS= "targetTaxonomyCategory3Ids";
  public static final String TITLE = "title";
  public static final String TO = "to";
  public static final String TOPN = "topn";
  public static final String TYPE = "type";
  public static final String TOTAL_NO_OF_RATINGS="totalNoOfRating";
  public static final String UNDEFINED_IDENTIFIER = "Undefined column name ";
  public static final String UNKNOWN_IDENTIFIER = "Unknown identifier ";
  public static final String UPDATE = "update";
  public static final String UPDATED_BY = "updatedBy";
  public static final String UPDATED_DATE = "updatedDate";
  public static final String UPLOADED_BY = "uploadedBy";
  public static final String UPLOADED_DATE = "uploadedDate";
  public static final String URL = "url";
  public static final String URL_ACTION = "url_action";
  public static final String URL_ACTION_ID = "url_action_ids";
  public static final String USER = "user";
  public static final String USER_ACTION_ROLE = "user_action_role";
  public static final String USER_AUTH_DB = "userAuth_db";
  public static final String USER_COUNT = "userCount";
  public static final String USER_COUNT_TTL = "userCountTTL";
  public static final String USER_COURSE = "user_course";
  public static final String USER_COURSES = "userCourses";
  public static final String USER_DB = "user_db";
  public static final String USER_FOUND = "user exist with this login Id.";
  public static final String USER_ID = "userId";
  public static final String USER_IDs = "userIds";
  public static final String USER_LIST_REQ = "userListReq";
  public static final String USER_NAME = "username";
  public static final String USERNAME = "userName";
  public static final String VER = "ver";
  public static final String VERSION = "version";
  public static final String WEB_PAGES = "webPages";
  public static final String SUNBIRD_HEALTH_CHECK_ENABLE = "sunbird_health_check_enable";
  public static final String HEALTH = "health";
  public static final String SOFT_CONSTRAINTS = "softConstraints";
  public static final String SUNBIRD_USER_ORG_API_BASE_URL = "sunbird_user_org_api_base_url";
  public static final String SUNBIRD_API_MGR_BASE_URL = "sunbird_api_mgr_base_url";
  public static final String SUNBIRD_AUTHORIZATION = "sunbird_authorization";

  public static final String SUNBIRD_CS_SEARCH_PATH = "sunbird_cs_search_path";

  public static final String SUNBIRD_USER_SEARCH_URL = "sunbird_user_search_url";
  public static final String DURATION = "duration";
  public static final String LOCATION_CODE = "locationCode";
  public static final String UPLOAD_FILE_MAX_SIZE = "file_upload_max_size";
  public static final String PRIMARY_KEY = "PK";
  public static final String NON_PRIMARY_KEY = "NonPK";
  public static final String PARENT_ID = "parentId";
  public static final String CREATED_ON = "createdOn";
  public static final String LAST_UPDATED_ON = "lastUpdatedOn";
  public static final String SUNBIRD_DEFAULT_CHANNEL = "sunbird_default_channel";
  public static final String CASSANDRA_WRITE_BATCH_SIZE = "cassandra_write_batch_size";
  public static final String ORG_EXTERNAL_ID = "orgExternalId";
  public static final String ORG_PROVIDER = "orgProvider";
  public static final String EXTERNAL_IDS = "externalIds";
  public static final String EXTERNAL_ID_TYPE = "externalIdType";
  public static final String ID_TYPE = "idType";
  public static final String ADD = "add";
  public static final String REMOVE = "remove";
  public static final String EDIT = "edit";
  public static final String DEFAULT_FRAMEWORK = "defaultFramework";
  public static final String EXTERNAL_ID_PROVIDER = "externalIdProvider";
  public static final String OK = "ok";
  public static final String RECIPIENT_SEARCH_QUERY = "recipientSearchQuery";
  public static final String SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL =
          "sunbird_cassandra_consistency_level";
  public static final String VERSION_2 = "v2";
  public static final String APP_ID = "appId";
  public static final String SUNBIRD_URL_SHORTNER_ENABLE = "sunbird_url_shortner_enable";

  public static final String SUNBIRD_COURSE_BATCH_NOTIFICATIONS_ENABLED =
          "sunbird_course_batch_notification_enabled";

  public static final String BATCH_START_DATE = "batchStartDate";
  public static final String BATCH_END_DATE = "batchEndDate";
  public static final String BATCH_NAME = "batchName";
  public static final String BATCH_MENTOR_ENROL = "batchMentorEnrol";
  public static final String BATCH_LEARNER_ENROL = "batchLearnerEnrol";
  public static final String COURSE_INVITATION = "Course Invitation";
  public static final String BATCH_LEARNER_UNENROL = "batchLearnerUnenrol";
  public static final String BATCH_MENTOR_UNENROL = "batchMentorUnenrol";
  public static final String UNENROLL_FROM_COURSE_BATCH = "Unenrolled from Training";
  public static final String OPEN_BATCH_LEARNER_UNENROL = "openBatchLearnerUnenrol";

  public static final String COURSE_BATCH = "courseBatch";
  public static final String ADDED_MENTORS = "addedMentors";
  public static final String REMOVED_MENTORS = "removedMentors";
  public static final String ADDED_PARTICIPANTS = "addedParticipants";
  public static final String REMOVED_PARTICIPANTS = "removedParticipants";
  public static final String URL_QUERY_STRING = "urlQueryString";
  public static final String SUNBIRD_API_REQUEST_LOWER_CASE_FIELDS =
          "sunbird_api_request_lower_case_fields";
  public static final String COMPLETED_ON = "completedOn";
  public static final String CALLER_ID = "callerId";
  public static final String USER_TYPE = "userType";

  public static final String COURSE_BATCH_URL = "courseBatchUrl";
  public static final String SUNBIRD_COURSE_BATCH_NOTIFICATION_SIGNATURE =
          "sunbird_course_batch_notification_signature";
  public static final String SIGNATURE = "signature";
  public static final String OPEN_BATCH_LEARNER_ENROL = "openBatchLearnerEnrol";
  public static final String CONTENT_CLOUD_STORAGE_TYPE = "sunbird_cloud_service_provider";
  public static final String CONTENT_CLOUD_STORAGE_CONTAINER =
          "sunbird_content_cloud_storage_container";
  public static final String AZURE_STR = "azure";
  public static final String AWS_STR = "aws";
  public static final String GCLOUD_STR = "gcloud";

  public static final String CLOUD_FOLDER_CONTENT = "sunbird_cloud_content_folder";
  public static final String CLOUD_STORE_BASE_PATH = "cloud_storage_base_url";
  public static final String CLOUD_STORAGE_CNAME_URL= "cloud_storage_cname_url";
  public static final String CLOUD_STORE_BASE_PATH_PLACEHOLDER = "cloud_store_base_path_placeholder";
  public static final String TTL = "ttl";

  public static final String MIME_TYPE = "mimeType";
  public static final String COLLECTION_MIME_TYPE = "application/vnd.ekstep.content-collection";
  public static final String BULK_ORG_UPLOAD = "bulkOrgUpload";
  public static final String LOCATION_CODES = "locationCodes";
  public static final String BATCH_DETAILS = "batchDetails";
  public static final String DIAL_CODES = "dialcodes";
  public static final String NO = "No";
  public static final String YES = "Yes";

  public static final String BATCHES = "batches";
  public static final String ENROLLED_ON = "enrolledOn";
  public static final String OTHER = "OTHER";
  public static final String TEACHER = "TEACHER";
  public static final String USER_EXTERNAL_ID = "userExternalId";
  public static final String USER_ID_TYPE = "userIdType";
  public static final String USER_PROVIDER = "userProvider";
  public static final String TERM = "term";
  public static final String DESC = "desc";
  public static final String SUNBIRD_TIMEZONE = "sunbird_time_zone";
  public static final String DATA_SOURCE = "dataSource";
  public static final String SUNBIRD_KEYCLOAK_USER_FEDERATION_PROVIDER_ID =
          "sunbird_keycloak_user_federation_provider_id";
  public static final String DEVICE_ID = "did";
  public static final String COMPLETED_PERCENT = "completedPercent";
  public static final String SUNBIRD_GZIP_ENABLE = "sunbird_gzip_enable";
  public static final String SUNBIRD_SYNC_READ_WAIT_TIME = "sunbird_sync_read_wait_time";
  public static final String SUNBIRD_GZIP_SIZE_THRESHOLD = "sunbird_gzip_size_threshold";
  public static final String PAGE_MANAGEMENT = "page_management";
  public static final String MAP_NAME = "mapName";
  public static final String SIGNUP_TYPE = "signupType";
  public static final String REQUEST_SOURCE = "source";

  public static final String SUNBIRD_REDIS_CONN_POOL_SIZE = "sunbird_redis_connection_pool_size";
  public static final String RECIPIENT_PHONES = "recipientPhones";
  public static final String REST = "rest";
  public static final String ES_OR_OPERATION = "$or";
  public static final String FROM_ACCOUNT_ID = "fromAccountId";
  public static final String TO_ACCOUNT_ID = "toAccountId";
  public static final String CERT_ID = "certId";
  public static final String ACCESS_CODE = "accessCode";
  public static final String JSON_DATA = "jsonData";
  public static final String PDF_URL = "pdfURL";
  public static final String SIGN_KEYS = "signKeys";
  public static final String ENC_KEYS = "encKeys";
  public static final String SUNBIRD_STATE_IMG_URL = "sunbird_state_img_url";
  public static final String SUNBIRD_DIKSHA_IMG_URL = "sunbird_diksha_img_url";
  public static final String SUNBIRD_CERT_COMPLETION_IMG_URL = "sunbird_cert_completion_img_url";
  public static final String stateImgUrl = "stateImgUrl";
  public static final String dikshaImgUrl = "dikshaImgUrl";
  public static final String certificateImgUrl = "certificateImgUrl";
  public static final String X_AUTHENTICATED_USER_TOKEN = "x-authenticated-user-token";
  public static final String X_SOURCE_USER_TOKEN = "x-source-user-token";
  public static final String X_CHANNEL_ID = "x-channel-id";
  public static final String X_AUTHENTICATED_USERID = "x-authenticated-userid";
  public static final String SUNBIRD_COURSE_DIALCODES_DB = "sunbird_course_dialcodes_db";
  public static final String RECOVERY_EMAIL = "recoveryEmail";
  public static final String RECOVERY_PHONE = "recoveryPhone";
  public static final String NESTED_KEY_FILTER = "nestedFilters";
  public static final String LICENSE = "license";
  public static final String DEFAULT_LICENSE = "defaultLicense";
  public static final String SUNBIRD_PASS_REGEX = "sunbird_pass_regex";
  public static final String NESTED_EXISTS = "nested_exists";
  public static final String NESTED_NOT_EXISTS = "nested_not_exists";
  public static final String LEARNING_SERVICE_BASE_URL = "learning_service_base_url";
  public static final String CREATOR_DETAILS_FIELDS = "sunbird_user_search_cretordetails_fields";
  public static final String SUNBIRD_QRCODE_COURSES_LIMIT ="sunbird_user_qrcode_courses_limit";
  public static final String ACCESS_TOKEN_PUBLICKEY_BASEPATH = "accesstoken.publickey.basepath";
  public static final String ACCESS_TOKEN_PUBLICKEY_KEYPREFIX = "accesstoken.publickey.keyprefix";
  public static final String ACCESS_TOKEN_PUBLICKEY_KEYCOUNT = "accesstoken.publickey.keycount";
  public static final String SHA_256_WITH_RSA = "SHA256withRSA";
  public static final String SUB = "sub";
  public static final String DOT_SEPARATOR = ".";
  public static final String REQUESTED_FOR = "requestedFor";
  public static final String CONTENT_PROPS_TO_ADD ="learning.content.props.to.add";
  public static final String GROUP_ACTIVITY_DB = "groupActivityDB";
  public static final String ACTIVITYID = "activityId";
  public static final String ACTIVITYTYPE = "activityType";
  public static final String GROUP_SERVICE_API_BASE_URL ="sunbird_group_service_api_base_url";
  public static final String COLLECTION_ID = "collectionId";
  public static final String TRACKABLE_ENABLED = "trackable.enabled";
  public static final String GROUPBY = "groupBy";
  public static final String X_AUTH_TOKEN = "X_AUTH_TOKEN";
  public static final String TEMPLATE = "template";
  public static final String ASSESSMENT_AGGREGATOR_DB = "assessment_aggregator_db";
  public static final String SERVICE_NAME = "course-service";
  public static final String PRODUCER_NAME = "org.sunbird.course-service";
  public static final String PID = "course-service";
  public static final String P_VERSION = "1.0";
  public static final String X_DEVICE_ID = "x-device-id";
  public static final String X_SESSION_ID = "x-session-id";
  public static final String USER_ENROLMENTS_DB = "user_enrolments";
  public static final List<String> CHANGE_IN_SIMPLE_DATE_FORMAT = Arrays.asList("startDate", "endDate", "enrollmentEndDate");
  public static final List<String> CHANGE_IN_DATE_FORMAT = Arrays.asList("createdDate", "updatedDate");
  public static final List<String> CHANGE_IN_DATE_FORMAT_ALL = Arrays.asList("startDate", "endDate", "enrollmentEndDate", "createdDate", "updatedDate");
  public static final String OLD_START_DATE = "oldStartDate";
  public static final String OLD_END_DATE = "oldEndDate";
  public static final String OLD_ENROLLMENT_END_DATE = "oldEnrollmentEndDate";
  public static final String OLD_LAST_ACCESS_TIME = "oldLastAccessTime";
  public static final String OLD_LAST_COMPLETED_TIME = "oldLastCompletedTime";
  public static final String OLD_LAST_UPDATED_TIME = "oldLastUpdatedTime";
  public static final String COURSE_ID_KEY = "courseid";
  public static final String CONTENT_ID_KEY = "contentid";
  public static final String LAST_ACCESS_TIME_KEY = "last_access_time";
  public static final List<String> SET_END_OF_DAY = Arrays.asList("endDate", "enrollmentEndDate");
  public static final String BATCH_ID_KEY = "batchid";
  public static final String USER_ID_KEY = "userid";
  public static final String OLD_CREATED_DATE = "oldCreatedDate";
  public static final String X_LOGGING_HEADERS = "X_LOGGING_HEADERS";
  public static final String LAST_CONTENT_ACCESS_TIME = "lastcontentaccesstime";
  public static final String GCP="gcloud";
  public static final String SUNBIRD_DIAL_SERVICE_BASE_URL = "sunbird_dial_service_base_url";
  public static final String SUNBIRD_DIAL_SERVICE_SEARCH_URL = "sunbird_dial_service_search_url";
  public static final String CONTENT_SERVICE_MOCK_ENABLED = "content_service_mock_enabled";
  public static final String AUTH_ENABLED = "AuthenticationEnabled";
  public static final String CONTENT_READ_URL = "content_read_url";
  public static final String TAG = "tag";
  public static final String EXHAUST_API_BASE_URL = "exhaust_api_base_url";
  public static final String EXHAUST_API_SUBMIT_ENDPOINT = "exhaust_api_submit_endpoint";
  public static final String EXHAUST_API_LIST_ENDPOINT = "exhaust_api_list_endpoint";
  public static final String ENCRYPTIONKEY = "encryptionKey";
  public static final String DATASET = "dataset";
  public static final String DATASETCONFIG = "datasetConfig";
  public static final String OUTPUT_FORMAT = "output_format";

  public static final String CONTENT_LENGTH = "Content-Length";

  //#Release-5.4.0 - LR-511
  public static final String SUNBIRD_KEYSPACE = "sunbird_keyspace";
  public static final String SUNBIRD_COURSE_KEYSPACE ="sunbird_course_keyspace";
  public static final String DIALCODE_KEYSPACE = "dialcode_keyspace";
  public static final String REDIS_HOST_VALUE = "sunbird_redis_host";
  public static final String REDIS_PORT_VALUE = "sunbird_redis_port";
  public static final String REDIS_INDEX_VALUE = "redis.dbIndex";
  public static final String SUNBIRD_REDIS_SCAN_INTERVAL = "sunbird_redis_scan_interval";
  public static final String ES_COURSE_INDEX = "es_course_index";
  public static final String ES_COURSE_BATCH_INDEX = "es_course_batch_index";
  public static final String ES_USER_INDEX = "es_user_index";
  public static final String ES_ORGANISATION_INDEX = "es_organisation_index";
  public static final String ES_USER_COURSES_INDEX = "es_user_courses_index";
  public static final String EVALUABLE_FLAG_TAG="serverEvaluable";
  public static final String ASSESS_REQ_BDY ="ASSESS_REQ_BODY";
  public static final String INQUIRY_BASE_URL = "inquiry_api_base_url";
  public static final String INQUIRY_ASSESS_SCORE_URL = "inquiry_api_assess_score_url";
  public static final String QUESTIONS = "questions";
  public static final String ASSESSMENTS="assessments";

  private JsonKey() {}
}
