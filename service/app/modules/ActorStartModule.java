package modules;

import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RouterConfig;
import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;
import util.ACTOR_NAMES;

public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {

  @Override
  protected void configure() {
    System.out.println("binding actors for dependency injection");
    final RouterConfig config = new FromConfig();
    for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
      bindActor(
          actor.getActorClass(),
          actor.getActorName(),
          (props) -> {
            return props.withRouter(config);
          });
    }
    System.out.println("binding completed");
  }
}
