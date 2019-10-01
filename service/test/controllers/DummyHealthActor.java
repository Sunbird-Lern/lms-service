package controllers;

import akka.actor.ActorRef;
import akka.actor.UntypedAbstractActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;

import java.util.HashMap;
import java.util.Map;

public class DummyHealthActor extends UntypedAbstractActor {


    @Override
    public void onReceive(Object message) throws Throwable {
        Response response = new Response();
        Map<String,Object> innerMap= new HashMap<>();
        innerMap.put(JsonKey.Healthy,true);
        response.getResult().put(JsonKey.RESPONSE,innerMap);
        sender().tell(response, ActorRef.noSender());
    }
}
