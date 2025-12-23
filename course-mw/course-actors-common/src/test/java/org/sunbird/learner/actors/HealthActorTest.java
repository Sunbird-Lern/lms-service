package org.sunbird.learner.actors;


import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.learner.actors.health.HealthActor;
import org.sunbird.learner.util.Util;

@Ignore
public class HealthActorTest {

  private static ActorSystem system;
  private static final Props props = Props.create(HealthActor.class);

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
    Util.checkCassandraDbConnections();
  }

  @Test
  public void checkTelemetryKeyFailure() throws Exception {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    String telemetryEnvKey = "user";
    PowerMockito.mockStatic(Util.class);
    PowerMockito.doNothing()
        .when(
            Util.class,
            "initializeContext",
            Mockito.any(Request.class),
            Mockito.eq(telemetryEnvKey));

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.HEALTH_CHECK.getValue());
    subject.tell(reqObj, probe.getRef());
    probe.expectMsgClass(Response.class);
    Assert.assertTrue(!(telemetryEnvKey.charAt(0) >= 65 && telemetryEnvKey.charAt(0) <= 90));
  }

  @Test
  public void getHealthCheck() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.HEALTH_CHECK.getValue());
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(java.time.Duration.ofSeconds(200), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void getACTORHealthCheck() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ACTOR.getValue());
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(java.time.Duration.ofSeconds(200), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void getESHealthCheck() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.ES.getValue());
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(java.time.Duration.ofSeconds(200), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }

  @Test
  public void getCASSANDRAHealthCheck() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.CASSANDRA.getValue());
    subject.tell(reqObj, probe.getRef());
    Response res = probe.expectMsgClass(java.time.Duration.ofSeconds(200), Response.class);
    Assert.assertTrue(null != res.get(JsonKey.RESPONSE));
  }
}
