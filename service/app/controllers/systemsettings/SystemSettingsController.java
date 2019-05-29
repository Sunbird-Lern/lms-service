package controllers.systemsettings;

import controllers.BaseController;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.Request;
import org.sunbird.validator.systemsettings.SystemSettingsRequestValidator;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Result;

public class SystemSettingsController extends BaseController {

  private static final SystemSettingsRequestValidator systemSettingsRequestValidator =
      new SystemSettingsRequestValidator();

  @SuppressWarnings("unchecked")
  public Promise<Result> getSystemSetting(String field) {
    try {
      return handleRequest(ActorOperations.GET_SYSTEM_SETTING.getValue(), field, JsonKey.FIELD);
    } catch (Exception e) {
      return Promise.<Result>pure(createCommonExceptionResponse(e, request()));
    }
  }

  @SuppressWarnings("unchecked")
  public Promise<Result> setSystemSetting() {
    try {
      return handleRequest(
          ActorOperations.SET_SYSTEM_SETTING.getValue(),
          request().body().asJson(),
          (request) -> {
            systemSettingsRequestValidator.validateSetSystemSetting((Request) request);
            return null;
          });
    } catch (Exception e) {
      return Promise.<Result>pure(createCommonExceptionResponse(e, request()));
    }
  }

  @SuppressWarnings("unchecked")
  public Promise<Result> getAllSystemSettings() {
    try {
      ProjectLogger.log("getAllSystemSettings called", LoggerEnum.INFO);
      Promise<Result> result = handleRequest(ActorOperations.GET_ALL_SYSTEM_SETTINGS.getValue());
      result.onRedeem(
          new F.Callback<Result>() {

            @Override
            public void invoke(Result a) throws Throwable {
              ProjectLogger.log("result " + a, LoggerEnum.INFO);
            }
          });

      return result;
    } catch (Exception e) {
      ProjectLogger.log("controller ", e);
      return Promise.<Result>pure(createCommonExceptionResponse(e, request()));
    }
  }
}
