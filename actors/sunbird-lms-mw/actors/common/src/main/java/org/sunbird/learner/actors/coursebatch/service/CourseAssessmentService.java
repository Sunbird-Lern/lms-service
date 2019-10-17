/** */
package org.sunbird.learner.actors.coursebatch.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.learner.actors.coursebatch.dao.CourseAssessmentDao;
import org.sunbird.learner.actors.coursebatch.dao.impl.CourseAssessmentDaoImpl;

/** @author rahul */
public class CourseAssessmentService {

  private CourseAssessmentDao CourseAssessmentDao = new CourseAssessmentDaoImpl();

  public Set<String> fetchFilteredAssessmentUser(
      String courseId,
      String batchId,
      final Map<String, Object> filter,
      final List<Map<String, Object>> userCourses) {
    Set<String> userCoursesUserIds =
        userCourses
            .stream()
            .map(userCourse -> (String) userCourse.get(JsonKey.USER_ID))
            .collect(Collectors.toSet());
    CourseAssessmentDao.fetchFilteredAssessmentsCourseBatchUsers(
        courseId,
        batchId,
        new Predicate<Map<String, Object>>() {

          @Override
          public boolean test(Map<String, Object> userAssessment) {
            double userAssessmentCompletionPercentage =
                (double) userAssessment.get(JsonKey.ASSESSMENT_SCORE)
                    / (double) userAssessment.get(JsonKey.ASSESSMENT_MAX_SCORE);
            userAssessment.put(JsonKey.COMPLETED_PERCENT, userAssessmentCompletionPercentage);
            String userId = (String) userAssessment.get(JsonKey.USER_ID);
            if (userCoursesUserIds.contains(userId)) {
              for (Map.Entry<String, Object> entry : filter.entrySet()) {
                boolean isValid =
                    userAssessment.containsKey(entry.getKey())
                        && validateUserAssessmentFilter(
                            entry.getValue(), userAssessment.get(entry.getKey()));
                if (!isValid) {
                  return false;
                }
              }
              return true;
            }
            return false;
          }

          private boolean validateUserAssessmentFilter(Object filterValue, Object value) {
            if (filterValue instanceof Map) {
              for (Map.Entry<String, Object> entry : filter.entrySet()) {
                boolean cond = false;
                switch (entry.getKey()) {
                  case ">":
                    cond = (double) value > (double) entry.getValue();
                    break;
                  case ">=":
                    cond = (double) value >= (double) entry.getValue();
                    break;
                  case "<":
                    cond = (double) value < (double) entry.getValue();
                    break;
                  case "<=":
                    cond = (double) value <= (double) entry.getValue();
                    break;
                  case "=":
                    cond = (double) value == (double) entry.getValue();
                    break;
                  default:
                    cond = false;
                }
                if (!cond) {
                  return false;
                }
              }
              return true;
            } else if (filterValue instanceof List) {
              return ((List) filterValue).contains(value);
            } else {
              return filterValue.equals(value);
            }
          }
        });
    return null;
  }
}
