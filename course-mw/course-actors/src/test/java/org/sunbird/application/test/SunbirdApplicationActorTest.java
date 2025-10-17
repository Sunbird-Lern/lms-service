/** */
package org.sunbird.application.test;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;
import java.time.Duration;
import org.sunbird.common.request.Request;

/** @author rahul */
public class SunbirdApplicationActorTest {

  private ActorSystem system;
  private Props props;

  public void init(Class clazz) {
    system = ActorSystem.create("system");
    props = Props.create(clazz);
  }

  protected <T> T executeInTenSeconds(Request request, Class<T> t) {
    return execute(request, t, 10);
  }

  protected <T> T execute(Request request, Class<T> t, int durationInSeconds) {
    if (props == null) {
      throw new RuntimeException(
          "props are not initiated, please invoke init in @before or constructor");
    }
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(request, probe.getRef());
    return probe.expectMsgClass(Duration.ofSeconds(durationInSeconds), t);
  }
}
