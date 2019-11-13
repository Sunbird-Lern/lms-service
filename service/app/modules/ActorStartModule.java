package modules;

import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;
import util.ACTOR_NAMES;

public class ActorStartModule extends AbstractModule implements AkkaGuiceSupport {

  @Override
  protected void configure() {
    super.configure();
    System.out.println("binding actors for dependency injection");
    for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
      System.out.println("binding " + actor.getActorClass() + "=>" + actor.getActorName());
      bindActor(actor.getActorClass(), actor.getActorName());
    }
    System.out.println("binding completed");
  }
}
