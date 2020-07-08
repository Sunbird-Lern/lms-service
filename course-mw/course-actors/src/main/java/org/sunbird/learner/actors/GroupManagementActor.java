package org.sunbird.learner.actors;

import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class GroupManagementActor extends BaseActor {

    @Override
    public void onReceive(Request request) throws Throwable {
        String requestedOperation = request.getOperation();
        switch (requestedOperation) {
            case "aggregateGroupActivity":
                getAggregateGroupActivity(request);
                break;
            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void getAggregateGroupActivity(Request request) throws Exception {
        Response response = new Response();
        response.getResult().put("groupId", "get value from request");
        response.getResult().put("activity", new HashMap<String, Object>(){{
            put("id", "get activity id from request");
            put("type", "get activity type from request");
            put("agg", Arrays.asList(new HashMap<String, Object>(){{
                put("metric", "completedCount");
                put("value", 12);
                put("lastUpdatedOn", ProjectUtil.formatDate(new Timestamp(new Date().getTime())));
            }}));
        }});
        response.getResult().put("members", Arrays.asList(new HashMap<String, Object>(){{
            put("id", "userid");
            put("agg", Arrays.asList(new HashMap<String, Object>(){{
                put("metric", "completedCount");
                put("value", 4);
                put("lastUpdatedOn", ProjectUtil.formatDate(new Timestamp(new Date().getTime())));
            }}));
        }}));
        sender().tell(response, self());
    }
}
