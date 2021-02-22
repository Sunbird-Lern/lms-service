package org.sunbird.common.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestContext {

    private String uid;
    private String did;
    private String sid;
    private String debugEnabled;
    private String actorId;
    private String actorType;
    private String loggerLevel;
    private String requestId;
    private Map<String, Object> contextMap = new HashMap<>();
    private String channel;
    private Map<String, Object> pdata = new HashMap<>();

    public RequestContext(String channel, String pdataId, String env, String did, String sid, String pid, String pver, List<Object> cdata) {
        this.did = did;
        this.sid = sid;
        this.channel = channel;
        this.pdata.put("id", pdataId);
        this.pdata.put("pid", pid);
        this.pdata.put("ver", pver);
        this.contextMap.putAll(new HashMap<String, Object>() {{
            put("did", did);
            put("sid", sid);
            put("channel", channel);
            put("env", env);
            put("pdata", pdata);
            if (cdata != null)
                put("cdata", cdata);
        }});
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public String getLoggerLevel() {
        return loggerLevel;
    }

    public void setLoggerLevel(String loggerLevel) {
        this.loggerLevel = loggerLevel;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDebugEnabled() {
        return debugEnabled;
    }

    public Map<String, Object> getContextMap() {
        return contextMap;
    }
}
