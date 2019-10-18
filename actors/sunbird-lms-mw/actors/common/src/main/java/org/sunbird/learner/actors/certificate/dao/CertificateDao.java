package org.sunbird.learner.actors.certificate.dao;

import org.sunbird.common.models.response.Response;

import java.util.List;
import java.util.Map;

public interface CertificateDao {

    void add(Map<String,Object> certificate);

    List<Map<String, Object>> readById(String courseId, String batchId);
}
