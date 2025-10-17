package actors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.AbstractActor;
import org.sunbird.common.models.response.Response;

/** Created by arvind on 30/11/17. */
public class DummyActor extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .matchAny(
            message -> {
              Response response = new Response();
              sender().tell(response, ActorRef.noSender());
            })
        .build();
  }
}
