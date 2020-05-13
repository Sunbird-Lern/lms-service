package org.sunbird.learner.actors.course;

import org.sunbird.actor.base.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.learner.util.Util;

public class CourseManagementActor extends BaseActor {
    @Override
    public void onReceive(Request request) throws Throwable {
        Util.initializeContext(request, "COURSE_CREATE");
        ExecutionContext.setRequestId(request.getRequestId());
        String requestedOperation = request.getOperation();
        switch (requestedOperation) {
            case "createCourse":
                createCourse(request);
                break;

            default:
                onReceiveUnsupportedOperation(requestedOperation);
                break;
        }
    }

    private void createCourse(Request request) {
        Response response = new Response();
        if(request.getRequest().containsKey("source")){
            response.put("do_11301654129459200011", "do_1234");
        } else {
            response.put("course_id", "do_1234");
            response.put("versionKey", "1589374765530");
        }
        sender().tell(response, self());
    }
}