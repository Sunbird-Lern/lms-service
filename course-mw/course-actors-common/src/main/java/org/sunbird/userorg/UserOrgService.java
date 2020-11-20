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
}
