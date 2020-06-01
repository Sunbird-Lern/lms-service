package actors;

import akka.routing.FromConfig;
import akka.routing.RouterConfig;
import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;
import util.ACTOR_NAMES;

public class TestModule extends AbstractModule implements AkkaGuiceSupport {

    @Override
    protected void configure() {
        System.out.println("binding test actors for dependency injection");
        final RouterConfig config = new FromConfig();
        for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
            bindActor(
                    DummyActor.class,
                    actor.getActorName(),
                    (props) -> {
                        return props.withRouter(config);
                    });
        }
        System.out.println("binding test completed");
    }
}
