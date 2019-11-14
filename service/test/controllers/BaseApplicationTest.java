package controllers;

import static play.inject.Bindings.bind;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import java.io.File;
import java.util.List;
import modules.StartModule;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;
import util.ACTOR_NAMES;
import util.RequestInterceptor;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({RequestInterceptor.class})
public abstract class BaseApplicationTest {
  protected Application application;
  private ActorSystem system;
  private Props props;

  public <T> void setup(Class<T> actorClass) {
    application =
        new GuiceApplicationBuilder()
            .in(new File("path/to/app"))
            .in(Mode.TEST)
            .disable(StartModule.class)
            .build();
    Helpers.start(application);
    system = ActorSystem.create("system");
    props = Props.create(actorClass);
    ActorRef subject = system.actorOf(props);
    BaseController.setActorRef(subject);
    PowerMockito.mockStatic(RequestInterceptor.class);
    PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any())).thenReturn("userId");
  }

  public <T> void setup(ACTOR_NAMES actor, Class actorClass) {
    application =
        new GuiceApplicationBuilder()
            .in(new File("path/to/app"))
            .in(Mode.TEST)
            .disable(StartModule.class)
            //                        .disable(ActorStartModule.class)
            //
            // .bindings(bind(actorClass).qualifiedWith(actor.getActorName()).toInstance(subject))
            .overrides(bind(actor.getActorClass()).to(actorClass))
            .build();
    Helpers.start(application);
    PowerMockito.mockStatic(RequestInterceptor.class);
    PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any())).thenReturn("userId");
  }

  public <T> void setup(List<ACTOR_NAMES> actors, Class actorClass) {
    GuiceApplicationBuilder applicationBuilder =
        new GuiceApplicationBuilder()
            .in(new File("path/to/app"))
            .in(Mode.TEST)
            .disable(StartModule.class);
    for (ACTOR_NAMES actor : actors) {
      applicationBuilder = applicationBuilder.overrides(bind(actor.getActorClass()).to(actorClass));
    }
    application = applicationBuilder.build();
    Helpers.start(application);
    PowerMockito.mockStatic(RequestInterceptor.class);
    PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.any())).thenReturn("userId");
  }
}
