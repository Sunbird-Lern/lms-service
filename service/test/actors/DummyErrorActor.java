package actors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.AbstractActor;
import org.sunbird.common.exception.ProjectCommonException;

public class DummyErrorActor extends AbstractActor {

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchAny(
                message -> {
                    ProjectCommonException e= new ProjectCommonException("INTERNAL_ERROR","Process failed,please try again later.",500);
                    sender().tell(e, ActorRef.noSender());
                })
            .build();
    }
}
