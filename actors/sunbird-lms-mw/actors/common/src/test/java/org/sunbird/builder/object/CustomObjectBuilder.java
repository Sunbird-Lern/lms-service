package org.sunbird.builder.object;

import akka.dispatch.Futures;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import scala.concurrent.Future;
import scala.concurrent.Promise;

public class CustomObjectBuilder {

  public static CustomObjectWrapper<Map<String, Object>> getRandomCourseBatch() {
    return new CourseBatchBuilder().generateRandomFields().build();
  }

  public static CustomObjectWrapper<List<Map<String, Object>>> getRandomUserCoursesList(int size) {
    return new UserCoursesBuilder().generateRandomList(size).buildList();
  }

  public static UserCoursesBuilder getUserCoursesBuilder() {
    return new UserCoursesBuilder();
  }

  public static CourseBatchBuilder getCourseBatchBuilder() {
    return new CourseBatchBuilder();
  }

  public static CustomObjectWrapper<Map<String, Object>> getRandomUser() {
    Map<String, Object> userMap = new HashMap<>();
    userMap.put(JsonKey.ID, "randomUserId");
    userMap.put(JsonKey.FIRST_NAME, "Random");
    userMap.put(JsonKey.ROOT_ORG_ID, "randomRootOrgId");
    return new CustomObjectWrapper<Map<String, Object>>(userMap);
  }

  public static CustomObjectWrapper<Map<String, Object>> getRandomCourse() {
    Map<String, Object> courseMap = new HashMap<>();
    courseMap.put(JsonKey.IDENTIFIER, "randomCourseId");
    courseMap.put(JsonKey.COURSE_NAME, "Random");
    courseMap.put("leafNodes", Arrays.asList("contentId", "contentIdNext"));
    List<Map<String, Object>> courseMapList = new ArrayList<>();
    courseMapList.add(courseMap);
    Map<String, Object> courseMapContent = new HashMap<>();
    courseMapContent.put(JsonKey.CONTENTS, courseMapList);
    System.out.println("returning=>" + courseMapContent);
    return new CustomObjectWrapper<Map<String, Object>>(courseMapContent);
  }

  public static CustomObjectWrapper<Map<String, Object>> getEmptyMap() {
    return new CustomObjectWrapper<Map<String, Object>>(new HashMap<>());
  }

  public static void getHttpResponse() {}

  public static CustomObjectWrapper<List<Map<String, Object>>> getRandomCourseBatchStats(int size) {
    List<Map<String, Object>> userList = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      Map<String, Object> map = new HashMap<>();
      map.put(JsonKey.USER_NAME, "randomUserName");
      map.put(JsonKey.MASKED_PHONE, "randomMaskedPhone");
      map.put(JsonKey.ORG_NAME, "randomOrgName");
      map.put(JsonKey.PROGRESS, "20%");
      map.put(JsonKey.ENROLLED_ON, "randomDate");
      userList.add(map);
    }
    return new CustomObjectWrapper<List<Map<String, Object>>>(userList);
  }

  public static class CustomObjectWrapper<T> {
    private T t;

    public CustomObjectWrapper(T t) {
      this.t = t;
    }

    public Future<T> asESIdentifierResult() {
      Promise<T> promise = Futures.promise();
      promise = promise.success(t);
      return promise.future();
    }

    public Future<Map<String, Object>> asESSearchResult() {
      Promise<Map<String, Object>> promise = Futures.promise();
      Map<String, Object> contents = new HashMap<>();
      contents.put(JsonKey.CONTENT, t);
      promise = promise.success(contents);
      return promise.future();
    }

    public Response asCassandraResponse() {
      Response response = new Response();
      response.put(JsonKey.RESPONSE, t);
      return response;
    }

    public T get() {
      return t;
    }
  }

  public static class CourseBatchBuilder {
    Map<String, Object> courseBatch = new HashMap<>();
    List<Map<String, Object>> courseBatchList = new ArrayList<>();

    public CourseBatchBuilder generateRandomFields() {
      courseBatch.put(JsonKey.COURSE_ID, "randomCourseId");
      courseBatch.put(JsonKey.BATCH_ID, "randomBatchId");
      courseBatch.put(JsonKey.NAME, "Test Batch");
      courseBatch.put(JsonKey.ENROLMENTTYPE, JsonKey.INVITE_ONLY);
      courseBatch.put(JsonKey.COURSE_ID, "someCourseId");
      courseBatch.put(JsonKey.COURSE_CREATED_FOR, Arrays.asList("randomOrgId"));
      courseBatch.put(JsonKey.MENTORS, Arrays.asList("randomMentorId"));
      withStatus(1);
      return this;
    }

    public CourseBatchBuilder generateRandomList(int size) {
      size = size > 10 ? 10 : size;
      for (int i = 0; i < size; i++) {
        generateRandomFields();
        courseBatch.put(JsonKey.BATCH_ID, "randomBatchId" + i);
        courseBatch.put(JsonKey.ENROLMENTTYPE, i % 2 == 0 ? JsonKey.INVITE_ONLY : JsonKey.OPEN);
        withStatus(i % 3);
        courseBatchList.add(courseBatch);
      }
      return this;
    }

    public CourseBatchBuilder addField(String key, Object value) {
      courseBatch.put(key, value);
      return this;
    }

    public CourseBatchBuilder removeField(String key) {
      courseBatch.remove(key);
      return this;
    }

    public CourseBatchBuilder withStatus(int status) {
      if (status == 0) {
        courseBatch.put(JsonKey.START_DATE, calculateDate(2));
        courseBatch.put(JsonKey.END_DATE, calculateDate(4));
      } else if (status == 1) {
        courseBatch.put(JsonKey.START_DATE, calculateDate(-2));
        courseBatch.put(JsonKey.END_DATE, calculateDate(4));
      } else if (status == 2) {
        courseBatch.put(JsonKey.START_DATE, calculateDate(-4));
        courseBatch.put(JsonKey.END_DATE, calculateDate(-2));
      }
      courseBatch.put(JsonKey.STATUS, status);
      return this;
    }

    public CustomObjectWrapper<Map<String, Object>> build() {
      return new CustomObjectWrapper<Map<String, Object>>(courseBatch);
    }

    public CustomObjectWrapper<List<Map<String, Object>>> buildList() {
      return new CustomObjectWrapper<List<Map<String, Object>>>(courseBatchList);
    }
  }

  public static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

  private static String calculateDate(int dayOffset) {
    Calendar calender = Calendar.getInstance();
    calender.add(Calendar.DAY_OF_MONTH, dayOffset);
    return format.format(calender.getTime());
  }

  public static class UserCoursesBuilder {
    Map<String, Object> userCourses = new HashMap<>();
    List<Map<String, Object>> userCoursesList = new ArrayList<>();

    public UserCoursesBuilder generateRandomFields() {
      userCourses.put(JsonKey.BATCH_ID, "randomBatchId");
      userCourses.put(JsonKey.USER_ID, "randomUserId");
      userCourses.put(JsonKey.COURSE_ID, "randomCourseId");
      userCourses.put(JsonKey.COURSE_ENROLL_DATE, ProjectUtil.getFormattedDate());
      userCourses.put(JsonKey.ACTIVE, ProjectUtil.ActiveStatus.ACTIVE.getValue());
      userCourses.put(JsonKey.STATUS, ProjectUtil.ProgressStatus.NOT_STARTED.getValue());
      userCourses.put(JsonKey.COURSE_PROGRESS, 0);
      userCourses.put(JsonKey.COMPLETED_PERCENT, 0);
      withStatus(1);
      return this;
    }

    public UserCoursesBuilder generateRandomList(int size) {
      size = size > 10 ? 10 : size;
      for (int i = 0; i < size; i++) {
        generateRandomFields();
        userCourses.put(JsonKey.USER_ID, "randomUserId" + i);
        userCourses.put(JsonKey.ENROLMENTTYPE, i % 2 == 0 ? JsonKey.INVITE_ONLY : JsonKey.OPEN);
        withStatus(i % 3);
        userCoursesList.add(userCourses);
      }
      return this;
    }

    public UserCoursesBuilder addField(String key, Object value) {
      userCourses.put(key, value);
      return this;
    }

    public UserCoursesBuilder removeField(String key) {
      userCourses.remove(key);
      return this;
    }

    public UserCoursesBuilder withStatus(int status) {
      if (status == 0) {
        userCourses.put(JsonKey.COURSE_PROGRESS, 0);
        userCourses.put(JsonKey.COMPLETED_PERCENT, 0);
      } else if (status == 1) {
        userCourses.put(JsonKey.COURSE_PROGRESS, 0);
        userCourses.put(JsonKey.COMPLETED_PERCENT, 0);
      } else if (status == 2) {
        userCourses.put(JsonKey.COURSE_PROGRESS, 1);
        userCourses.put(JsonKey.COMPLETED_PERCENT, 100);
      }
      userCourses.put(JsonKey.STATUS, status);
      return this;
    }

    public CustomObjectWrapper<Map<String, Object>> build() {
      return new CustomObjectWrapper<Map<String, Object>>(userCourses);
    }

    public CustomObjectWrapper<List<Map<String, Object>>> buildList() {
      return new CustomObjectWrapper<List<Map<String, Object>>>(userCoursesList);
    }
  }
}
