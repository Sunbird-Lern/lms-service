package org.sunbird.keys;

import java.util.Arrays;
import java.util.List;

public final class SunbirdKey {

    public static final String REQUEST = "request";
    public static final String CHANNEL = "channel";
    public static final String IDENTIFIER = "identifier";
    public static final String COURSE = "course";
    public static final String SOURCE = "source";
    public static final String COPY_SCHEME = "copyScheme";
    public static final String TEXT_BOOK_TO_COURSE = "TextBookToCourse";
    public static final String COURSE_ID = "course_id";
    public static final String NODE_ID = "node_id";
    public static final String CONTENT = "content";
    public static final String VERSION_KEY = "versionKey";
    public static final String TB_MESSAGES = "messages";
    public static final String CONTENT_TYPE = "contentType";
    public static final String X_CHANNEL_ID = "X-Channel-Id";
    public static final String APPLICATION_JSON = "application/json";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String MIME_TYPE = "mimeType";
    public static final String CODE = "code";
    public static final String CREATED_BY = "createdBy";
    public static final String COURSE_CREATED_FOR = "createdFor";
    public static final String FRAMEWORK = "framework";
    public static final String ORGANISATION = "organisation";
    public static final String CONTENT_MIME_TYPE_COLLECTION = "application/vnd.ekstep.content-collection";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String HIERARCHY = "hierarchy";
    public static final String NODE_MODIFIED = "nodesModified";
    public static final String DATA = "data";
    public static final String VISIBILITY = "visibility";
    public static final String VISIBILITY_PARENT = "Parent";
    public static final String VISIBILITY_DEFAULT = "Default";
    public static final String METADATA = "metadata";
    public static final String CHILDREN = "children";
    public static final String ROOT = "root";
    public static final List<String> HIERARCHY_PROPS_TO_REMOVE = Arrays.asList("identifier", "parent", "index", "depth", "children");
    public static final String LAST_PUBLISHED_ON = "lastPublishedOn";
    public static final String REQUESTED_FOR = "requestedFor";
    // Content Status Update API Specific - START
    public static final String ACTUAL_USER_ID = "actualUserId";
    public static final String ALL_USER_IDS = "allUserIds";
    // Content Status Update API Specific - END

    private SunbirdKey() {}
}
