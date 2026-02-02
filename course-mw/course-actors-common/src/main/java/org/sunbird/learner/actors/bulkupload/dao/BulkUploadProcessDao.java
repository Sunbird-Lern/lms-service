package org.sunbird.learner.actors.bulkupload.dao;

import org.sunbird.response.Response;
import org.sunbird.request.RequestContext;
import org.sunbird.learner.actors.bulkupload.model.BulkUploadProcess;

/** Created by arvind on 24/4/18. */
public interface BulkUploadProcessDao {

  /**
   * @param bulkUploadProcess
   * @param requestContext
   * @return response Response
   */
  Response create(BulkUploadProcess bulkUploadProcess, RequestContext requestContext);

  /**
   * @param requestContext
   * @param bulkUploadProcess
   * @return response Response
   */
  Response update(RequestContext requestContext, BulkUploadProcess bulkUploadProcess);

  /**
   * @param requestContext
   * @param id
   * @return response Response
   */
  BulkUploadProcess read(RequestContext requestContext, String id);
}
