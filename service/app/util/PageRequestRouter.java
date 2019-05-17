package util;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.FromConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import controllers.pagemanagement.PageSearchActor;

public class PageRequestRouter {
    static ActorSystem system;
    static ActorRef actorRef;

    public static void init(){
        if(null== system) {
            Config config = ConfigFactory.systemEnvironment().withFallback(ConfigFactory.load());
            Config conf = config.getConfig("SunbirdMWSystem");
            system = ActorSystem.create("SunbirdMWSystem", conf);
        }
        actorRef = system.actorOf(FromConfig.getInstance().props(Props.create(PageSearchActor.class).withDispatcher("page-search-actor-dispatcher")),
                PageSearchActor.class.getSimpleName());
    }

    public static ActorRef getPageActor() {
        if(null != actorRef)
            return actorRef;
        else {
            init();
            return actorRef;
        }
    }
}
