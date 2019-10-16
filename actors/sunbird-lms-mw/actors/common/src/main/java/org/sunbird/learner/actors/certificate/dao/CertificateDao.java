package org.sunbird.learner.actors.certificate.dao;

import org.sunbird.common.models.response.Response;

import java.util.Map;

public interface CertificateDao {

    Response add(Map<String,Object> certificate);
}
