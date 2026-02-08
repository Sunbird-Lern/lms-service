package org.sunbird.actorutil.impl;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import java.util.concurrent.TimeUnit;
import org.sunbird.actorutil.InterServiceCommunication;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.logging.LoggerEnum;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.logging.ProjectLogger;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

public class InterServiceCommunicationImpl implements InterServiceCommunication {

  private Timeout t = new Timeout(Duration.create(10, TimeUnit.SECONDS));
  private LoggerUtil logger = new LoggerUtil(this.getClass());

  @Override
  public Object getResponse(ActorRef actorRef, Request request) {
    try {
      return Await.result(getFuture(actorRef, request), t.duration());
    } catch (Exception e) {
      logger.error(request.getRequestContext(),
          "InterServiceCommunicationImpl:getResponse: Exception occurred with error message = "
              + e.getMessage(),
          e);
      ProjectCommonException.throwServerErrorException(
          ResponseCode.unableToCommunicateWithActor,
          ResponseCode.unableToCommunicateWithActor.getErrorMessage());
    }
    return null;
  }

  @Override
  public Future<Object> getFuture(ActorRef actorRef, Request request) {
    if (null == actorRef) {
      logger.info(request.getRequestContext(),
          "InterServiceCommunicationImpl:getFuture: actorRef is null");
      ProjectCommonException.throwServerErrorException(
          ResponseCode.unableToCommunicateWithActor,
          ResponseCode.unableToCommunicateWithActor.getErrorMessage());
    }
    try {
      return Patterns.ask(actorRef, request, t);
    } catch (Exception e) {
      logger.error(request.getRequestContext(),
          "InterServiceCommunicationImpl:getFuture: Exception occured with error message = "
              + e.getMessage(),
          e);
      ProjectCommonException.throwServerErrorException(
          ResponseCode.unableToCommunicateWithActor,
          ResponseCode.unableToCommunicateWithActor.getErrorMessage());
    }
    return null;
  }
}
