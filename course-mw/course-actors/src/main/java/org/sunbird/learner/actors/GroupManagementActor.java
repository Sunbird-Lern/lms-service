package org.sunbird.learner.actors;

import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.request.Request;

import java.util.Arrays;
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
        response.getResult().put("groupId", request.get("groupId"));
        response.getResult().put("activity", new HashMap<String, Object>(){{
            put("id", "do_12312312");
            put("type", "Course");
            put("agg", Arrays.asList(new HashMap<String, Object>(){{
                put("metric", "completedCount");
                put("value", 12);
                put("lastUpdatedOn", System.currentTimeMillis());
            }}));
        }});
        response.getResult().put("members", Arrays.asList(new HashMap<String, Object>(){{
            put("id", "userid");
            put("agg", Arrays.asList(new HashMap<String, Object>(){{
                put("metric", "completedCount");
                put("value", 4);
                put("lastUpdatedOn", System.currentTimeMillis());
            }}));
        }}));
        sender().tell(response, self());
    }
}
