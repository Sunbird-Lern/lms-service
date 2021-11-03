package org.sunbird.userorg;

import java.util.List;
import java.util.Map;

public interface UserOrgService {

  Map<String, Object> getOrganisationById(String id);

  List<Map<String, Object>> getOrganisationsByIds(List<String> ids);

  Map<String, Object> getUserById(String id, String authToken);

  List<Map<String, Object>> getUsersByIds(List<String> ids, String authToken);

  List<Map<String, Object>> getUsers(Map<String, Object> request, String authToken);

  void sendEmailNotification(Map<String, Object> request, String authToken);

  /**
   * Gets the users' data containing name, email and userId
   *
   * @param userIds the list of user ids
   * @return The users' data containing name, email and userId
   */
  List<Map<String, Object>> getUsersByIds(List<String> userIds);
}
