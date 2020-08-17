package util;

import org.sunbird.common.models.util.JsonKey;
import play.libs.typedmap.TypedKey;

public class Attrs {
    public static final TypedKey<String> USER_ID = TypedKey.<String>create(JsonKey.USER_ID);
    public static final TypedKey<String> AUTH_WITH_MASTER_KEY = TypedKey.<String>create(JsonKey.AUTH_WITH_MASTER_KEY);
    public static final TypedKey<String> REQUEST_ID = TypedKey.<String>create(JsonKey.REQUEST_ID);
    public static final TypedKey<String> CONTEXT = TypedKey.<String>create(JsonKey.CONTEXT);
    public static final TypedKey<String> REQUESTED_FOR = TypedKey.<String>create(JsonKey.REQUESTED_FOR);
    public static final TypedKey<String> IS_AUTH_REQ = TypedKey.<String>create(JsonKey.IS_AUTH_REQ);
}
