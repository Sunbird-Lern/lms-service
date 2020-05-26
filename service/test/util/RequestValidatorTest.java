/** */
package util;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** @author Manzarul */
public class RequestValidatorTest {

  @Test
  public void testValidateUpdateContentSuccess() {
    Request request = new Request();
    boolean response = false;
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CONTENT_ID, "do_1233343");
    requestObj.put(JsonKey.STATUS, "Completed");
    requestObj.put(JsonKey.COURSE_ID, "do_1233213123343");
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.CONTENTS, listOfMap);
    innerMap.put(JsonKey.USER_ID, "user123");
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    assertEquals(true, response);
  }

  @Test
  public void testValdateUpdateContentFailureWithNullContentId() {
    Request request = new Request();
    boolean response = false;
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CONTENT_ID, null);
    requestObj.put(JsonKey.STATUS, "Completed");
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.CONTENTS, listOfMap);
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNotNull(e);
    }
    assertEquals(false, response);
  }

  @Test
  public void testValidteUpdateContentFailureWithoutContentId() {
    Request request = new Request();
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.STATUS, "Completed");
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.CONTENTS, listOfMap);
    innerMap.put(JsonKey.USER_ID, "user123");
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.courseIdRequiredError.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidteUpdateContentFailureWithoutStatus() {
    Request request = new Request();
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CONTENT_ID, "do_1233343");
    requestObj.put(JsonKey.COURSE_ID, "do_123334123123");
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.CONTENTS, listOfMap);
    innerMap.put(JsonKey.USER_ID, "user123");
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.contentStatusRequired.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidteUpdateContentFailureWithEmptyContents() {
    Request request = new Request();
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.CONTENTS, listOfMap);
    innerMap.put(JsonKey.USER_ID, "user123");
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.contentIdRequiredError.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateRegisterClientFailureWithEmptyClientName() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CLIENT_NAME, "");
    request.setRequest(requestObj);
    try {
      RequestValidator.validateRegisterClient(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidClientName.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateRegisterClientSuccess() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CLIENT_NAME, "1234");
    request.setRequest(requestObj);
    try {
      RequestValidator.validateRegisterClient(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidClientName.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateUpdateClientKeyFailureWithEmptyToken() {
    try {
      RequestValidator.validateUpdateClientKey("1234", "");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidRequestData.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateUpdateClientKeySuccess() {
    try {
      RequestValidator.validateUpdateClientKey("1234", "test123");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidRequestData.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateClientIdFailureWithEmptyId() {
    try {
      RequestValidator.validateClientId("");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidClientId.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateFileUploadFailureWithoutContainerName() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.CONTAINER, "");
    request.setRequest(requestObj);
    try {
      RequestValidator.validateFileUpload(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.storageContainerNameMandatory.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateSendEmailSuccess() {
    boolean response = false;
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.SUBJECT, "test123");
    requestObj.put(JsonKey.BODY, "test");
    List<String> data = new ArrayList<>();
    data.add("test123@gmail.com");
    requestObj.put(JsonKey.RECIPIENT_EMAILS, data);
    requestObj.put(JsonKey.RECIPIENT_USERIDS, new ArrayList<>());
    request.setRequest(requestObj);
    try {
      RequestValidator.validateSendMail(request);
      response = true;
    } catch (ProjectCommonException e) {

    }
    assertTrue(response);
  }

  @Test
  public void testValidateSendMailFailureWithNullRecipients() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.SUBJECT, "test123");
    requestObj.put(JsonKey.BODY, "test");
    requestObj.put(JsonKey.RECIPIENT_EMAILS, null);
    request.setRequest(requestObj);
    try {
      RequestValidator.validateSendMail(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.mandatoryParamsMissing.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateSendMailFailureWithEmptyBody() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.SUBJECT, "test123");
    requestObj.put(JsonKey.BODY, "");
    request.setRequest(requestObj);
    try {
      RequestValidator.validateSendMail(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.emailBodyError.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateSendMailFailureWithEmptySubject() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.SUBJECT, "");
    request.setRequest(requestObj);
    try {
      RequestValidator.validateSendMail(request);
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.emailSubjectError.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateEnrolmentTypeFailureWithEmptyType() {
    try {
      RequestValidator.validateEnrolmentType("");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.enrolmentTypeRequired.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateEnrolmentTypeFailureWithWrongType() {
    try {
      RequestValidator.validateEnrolmentType("test");
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.enrolmentIncorrectValue.getErrorCode(), e.getCode());
    }
  }

  @Test
  public void testValidateEnrolmentTypeSuccessWithOpenType() {
    boolean response = false;
    try {
      RequestValidator.validateEnrolmentType(ProjectUtil.EnrolmentType.open.getVal());
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateEnrolmentTypeSuccessWithInviteType() {
    boolean response = false;
    try {
      RequestValidator.validateEnrolmentType(ProjectUtil.EnrolmentType.inviteOnly.getVal());
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateSyncRequestSuccess() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.OPERATION_FOR, "keycloak");
    requestObj.put(JsonKey.OBJECT_TYPE, JsonKey.USER);
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateSyncRequest(request);
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateSyncRequestFailureWithNullObjectType() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.OPERATION_FOR, "not keycloack");
    requestObj.put(JsonKey.OBJECT_TYPE, null);
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateSyncRequest(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.dataTypeError.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateSyncRequestFailureWithInvalidObjectType() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.OPERATION_FOR, "not keycloack");
    List<String> objectLsit = new ArrayList<>();
    objectLsit.add("testval");
    requestObj.put(JsonKey.OBJECT_TYPE, objectLsit);
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateSyncRequest(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidObjectType.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateUserOrgTypeSuccess() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.NAME, "orgtypeName");
    requestObj.put(JsonKey.ID, "orgtypeId");
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateUpdateOrgType(request);
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateUserOrgTypeFailureWithEmptyName() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.NAME, "");
    requestObj.put(JsonKey.ID, "orgtypeId");
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateUpdateOrgType(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.orgTypeMandatory.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateUserOrgTypeFailureWithEmptyId() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.NAME, "orgTypeName");
    requestObj.put(JsonKey.ID, "");
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateUpdateOrgType(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.orgTypeIdRequired.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateCreateOrgTypeSuccess() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.NAME, "OrgTypeName");
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateCreateOrgType(request);
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateCreateOrgTypeFailureWithNullName() {
    Request request = new Request();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.NAME, null);
    request.setRequest(requestObj);
    boolean response = false;
    try {
      RequestValidator.validateCreateOrgType(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.orgTypeMandatory.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateGetClientKeySuccess() {
    boolean response = false;
    try {
      RequestValidator.validateGetClientKey("clientId", "clientType");
      response = true;
    } catch (ProjectCommonException e) {
      Assert.assertNull(e);
    }
    Assert.assertTrue(response);
  }

  @Test
  public void testValidateGetClientKeyFailureWithEmptyClientId() {
    boolean response = false;
    try {
      RequestValidator.validateGetClientKey("", "clientType");
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidClientId.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }

  @Test
  public void testValidateGetClientKeyFailureWithEmptyClientType() {
    boolean response = false;
    try {
      RequestValidator.validateGetClientKey("clientId", "");
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.invalidRequestData.getErrorCode(), e.getCode());
    }
    Assert.assertFalse(response);
  }


  @Test
  public void testValdateUpdateContentWithAssessmentEvents() {
    Request request = new Request();
    boolean response = false;
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    requestObj.put(JsonKey.EVENTS, new ArrayList<Map<String, Object>>(){{
      add(new HashMap<String, Object>(){{
        put("ets", System.currentTimeMillis());
      }});
    }});
    requestObj.put(JsonKey.STATUS, "Completed");
    requestObj.put(JsonKey.USER_ID, "user123");
    requestObj.put(JsonKey.COURSE_ID, "do_123");
    requestObj.put(JsonKey.BATCH_ID, "01234");
    requestObj.put(JsonKey.ASSESSMENT_TS, System.currentTimeMillis());
    requestObj.put(JsonKey.CONTENT_ID, "do_1234");
    requestObj.put(JsonKey.ATTEMPT_ID, "attempt1");
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.ASSESSMENT_EVENTS, listOfMap);
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
      response = true;
    } catch (ProjectCommonException e) {
    }
    assertEquals(true, response);
  }

  @Test
  public void testValdateUpdateContentWithAllEmpty() {
    Request request = new Request();
    boolean response = false;
    List<Map<String, Object>> listOfMap = new ArrayList<>();
    Map<String, Object> requestObj = new HashMap<>();
    listOfMap.add(requestObj);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.ASSESSMENT_EVENTS, listOfMap);
    request.setRequest(innerMap);
    try {
      RequestValidator.validateUpdateContent(request);
      response = true;
    } catch (ProjectCommonException e) {
      assertEquals(ResponseCode.CLIENT_ERROR.getResponseCode(), e.getResponseCode());
      assertEquals(ResponseCode.userIdRequired.getErrorCode(), e.getCode());
    }
    assertEquals(false, response);
  }
}
