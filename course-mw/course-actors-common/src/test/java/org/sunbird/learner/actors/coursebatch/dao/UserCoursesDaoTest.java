package org.sunbird.learner.actors.coursebatch.dao;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.coursebatch.dao.impl.UserCoursesDaoImpl;
import org.sunbird.models.user.courses.UserCourses;

/** Created by rajatgupta on 08/04/19. */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ServiceFactory.class})
@PowerMockIgnore("javax.management.*")
public class UserCoursesDaoTest {
  private CassandraOperation cassandraOperation;
  private UserCoursesDao userCoursesDao;

  @BeforeClass
  public static void setUp() {}

  @Before
  public void beforeEachTest() {
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperation = mock(CassandraOperationImpl.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    userCoursesDao = new UserCoursesDaoImpl();
  }

  @Test
  public void readUserCoursesFailure() {
    Response readResponse = new Response();
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(readResponse);
    UserCourses response = userCoursesDao.read(null, JsonKey.BATCH_ID, JsonKey.USER_ID);
    Assert.assertEquals(null, response);
  }

  @Test
  public void readUserCoursesSuccess() {
    Response readResponse = new Response();
    readResponse.put(JsonKey.RESPONSE, Arrays.asList(new HashMap<>()));
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(readResponse);
    UserCourses response = userCoursesDao.read(null, JsonKey.BATCH_ID, JsonKey.USER_ID);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void readUserCoursesV2Success() {
    Response readResponse = new Response();
    readResponse.put(JsonKey.RESPONSE, Arrays.asList(new HashMap<>()));
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
            .thenReturn(readResponse);
    UserCourses response = userCoursesDao.read(null, JsonKey.USER_ID, JsonKey.COURSE_ID, JsonKey.BATCH_ID);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void readUserCoursesV2NullResponse() {
    Response readResponse = new Response();
    readResponse.put(JsonKey.RESPONSE, new ArrayList<>());
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
            .thenReturn(readResponse);
    UserCourses response = userCoursesDao.read(null, JsonKey.USER_ID, JsonKey.COURSE_ID, JsonKey.BATCH_ID);
    Assert.assertEquals(null, response);
  }

  @Test
  public void listUserCoursesNullResponse() {
    Response readResponse = new Response();
    readResponse.put(JsonKey.RESPONSE, new ArrayList<>());
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
            .thenReturn(readResponse);
    List<Map<String, Object>> response = userCoursesDao.listEnrolments(null, JsonKey.USER_ID, Collections.emptyList());
    Assert.assertEquals(null, response);
  }

  @Test
  public void listUserCoursesSuccess() {
    Response readResponse = new Response();
    readResponse.put(JsonKey.RESPONSE, Arrays.asList(new HashMap<String, Object>() {{
      put(JsonKey.USER_ID, JsonKey.USER_ID);
    }}));
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
            .thenReturn(readResponse);
    List<Map<String, Object>> response = userCoursesDao.listEnrolments(null, JsonKey.USER_ID, Collections.emptyList());
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void readUserCoursesExcpetionFailure() {
    Response readResponse = new Response();

    readResponse.put(JsonKey.RESPONSE, Arrays.asList("data"));
    when(cassandraOperation.getRecordByIdentifier(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(readResponse);
    UserCourses response = userCoursesDao.read(null, JsonKey.BATCH_ID, JsonKey.USER_ID);
    Assert.assertEquals(null, response);
  }

  @Test
  public void createUserCoursesSuccess() {
    Map<String, Object> userCourseMap = new HashMap<>();
    when(cassandraOperation.insertRecord(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
        .thenReturn(new Response());
    Response response = userCoursesDao.insert(null, userCourseMap);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void createUserCoursesV2Success() {
    Map<String, Object> userCourseMap = new HashMap<>();
    when(cassandraOperation.insertRecord(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap()))
            .thenReturn(new Response());
    Response response = userCoursesDao.insertV2(null, userCourseMap);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void updateUserCoursesSuccess() {
    Map<String, Object> userCourseMap = new HashMap<>();
    when(cassandraOperation.updateRecord(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyMap()))
        .thenReturn(new Response());
    Response response = userCoursesDao.update(null, JsonKey.BATCH_ID, JsonKey.USER_ID, userCourseMap);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void updateUserCoursesV2Success() {
    Map<String, Object> userCourseMap = new HashMap<>();
    when(cassandraOperation.updateRecord(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyMap()))
            .thenReturn(new Response());
    Response response = userCoursesDao.updateV2(null, JsonKey.BATCH_ID, JsonKey.COURSE_ID, JsonKey.USER_ID, userCourseMap);
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void batchInsertUserCoursesSuccess() {
    Map<String, Object> userCourseMap = new HashMap<>();
    when(cassandraOperation.batchInsert(
            Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyList()))
        .thenReturn(new Response());
    Response response = userCoursesDao.batchInsert(null, Arrays.asList(userCourseMap));
    Assert.assertNotEquals(null, response);
  }

  @Test
  public void getAllActiveUserWithoutParticipantsSuccess() {

    when(cassandraOperation.getRecordsByIndexedProperty(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(new Response());
    List<String> participants = userCoursesDao.getAllActiveUserOfBatch(null, JsonKey.BATCH_ID);
    Assert.assertEquals(null, participants);
  }

  @Test
  public void getAllActiveUserSuccess() {
    Response readResponse = new Response();
    Map<String, Object> userCoursesMap = new HashMap<>();
    userCoursesMap.put(JsonKey.USER_ID, JsonKey.USER_ID);
    userCoursesMap.put(JsonKey.ACTIVE, true);

    readResponse.put(JsonKey.RESPONSE, Arrays.asList(userCoursesMap));
    when(cassandraOperation.getRecordsByIndexedProperty(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(readResponse);
    List<String> participants = userCoursesDao.getAllActiveUserOfBatch(null, JsonKey.BATCH_ID);
    Assert.assertEquals(1, participants.size());
  }

  @Test
  public void getInstanceTest() {
    Assert.assertNotEquals(null, UserCoursesDaoImpl.getInstance());
  }

  @Test
  public void getBatchParticipantsSuccess() {
    Response readResponse = new Response();
    Map<String, Object> userCoursesMap = new HashMap<>();
    userCoursesMap.put(JsonKey.USER_ID, JsonKey.USER_ID);
    userCoursesMap.put(JsonKey.ACTIVE, true);

    readResponse.put(JsonKey.RESPONSE, Arrays.asList(userCoursesMap));
    when(cassandraOperation.getRecordsByIndexedProperty(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(readResponse);
    List<String> participants = userCoursesDao.getBatchParticipants(null, JsonKey.BATCH_ID, true);
    Assert.assertEquals(1, participants.size());
  }

  @Test
  public void getBatchParticipantsWithInactiveSuccess() {
    Response readResponse = new Response();
    Map<String, Object> userCoursesMap = new HashMap<>();
    userCoursesMap.put(JsonKey.USER_ID, JsonKey.USER_ID);
    userCoursesMap.put(JsonKey.ACTIVE, false);

    readResponse.put(JsonKey.RESPONSE, Arrays.asList(userCoursesMap));
    when(cassandraOperation.getRecordsByIndexedProperty(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(readResponse);
    List<String> participants = userCoursesDao.getBatchParticipants(null, JsonKey.BATCH_ID, false);
    Assert.assertEquals(1, participants.size());
  }

  @Test
  public void getBatchParticipantsWithEmptySuccess() {
    Response readResponse = new Response();
    Map<String, Object> userCoursesMap = new HashMap<>();
    userCoursesMap.put(JsonKey.USER_ID, JsonKey.USER_ID);
    userCoursesMap.put(JsonKey.ACTIVE, false);

    readResponse.put(JsonKey.RESPONSE, Arrays.asList(userCoursesMap));
    when(cassandraOperation.getRecordsByIndexedProperty(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(readResponse);
    List<String> participants = userCoursesDao.getBatchParticipants(null, JsonKey.BATCH_ID, true);
    Assert.assertEquals(0, participants.size());
  }
}
