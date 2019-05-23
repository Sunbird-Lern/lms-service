/** */
package controllers;

import play.mvc.Result;
import play.mvc.Results;

/**
 * This controller will handler all the request related to learner state.
 *
 * @author Manzarul
 */
public class LearnerController extends BaseController {


  public Result getHealth() {
    return Results.ok("ok");
  }

  /**
   * @param all
   * @return
   */
  public Result preflight(String all) {
    response().setHeader("Access-Control-Allow-Origin", "*");
    response().setHeader("Allow", "*");
    response().setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, DELETE, OPTIONS");
    response()
        .setHeader(
            "Access-Control-Allow-Headers",
            "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent,X-Consumer-ID,cid,ts,X-Device-ID,X-Authenticated-Userid,X-msgid,id,X-Access-TokenId");
    return ok();
  }
}
