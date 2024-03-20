package actors;

import akka.actor.ActorRef;
import akka.actor.UntypedAbstractActor;
import org.sunbird.common.exception.ProjectCommonException;

public class DummyErrorActor extends UntypedAbstractActor {

    @Override
    public void onReceive(Object message) throws Throwable {
        ProjectCommonException e= new ProjectCommonException("INTERNAL_ERROR","Process failed,please try again later.",500);
        sender().tell(e, ActorRef.noSender());
    }
}
