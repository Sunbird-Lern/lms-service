package actors;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.AbstractActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;

import java.util.HashMap;
import java.util.Map;

public class DummyHealthActor extends AbstractActor {


    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .matchAny(
                message -> {
                    Response response = new Response();
                    Map<String,Object> innerMap= new HashMap<>();
                    innerMap.put(JsonKey.Healthy,true);
                    response.getResult().put(JsonKey.RESPONSE,innerMap);
                    sender().tell(response, ActorRef.noSender());
                })
            .build();
    }
}
