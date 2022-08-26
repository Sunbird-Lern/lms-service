package org.sunbird.common.models.util.cloud;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.models.util.aws.AwsCloudService;
import org.sunbird.common.models.util.azure.AzureCloudService;
import org.sunbird.common.models.util.gcloud.GcpCloudService;

import static org.sunbird.common.models.util.JsonKey.*;

/**
 * Factory class to store the various upload download services like Azure , Amazon S3 etc... Created
 * by arvind on 24/8/17.
 */
public class CloudServiceFactory {

  private static Map<String, CloudService> factory = new HashMap<>();
  private static List<String> allowedServiceNames = Arrays.asList(AZURE_STR, AWS_STR, GCLOUD_STR);

  private CloudServiceFactory() {}

  /**
   * @param serviceName
   * @return
   */
  public static Object get(String serviceName) {

    if (ProjectUtil.isNotNull(factory.get(serviceName))) {
      return factory.get(serviceName);
    } else {
      // create the service with the given name
      return createService(serviceName);
    }
  }

  /**
   * @param serviceName
   * @return
   */
  private static CloudService createService(String serviceName) {

    if (!(allowedServiceNames.contains(serviceName))) {
      return null;
    }

    synchronized (CloudServiceFactory.class) {
      if (ProjectUtil.isNull(factory.get(serviceName)) && AZURE_STR.equalsIgnoreCase(serviceName)) {
        CloudService service = new AzureCloudService();
        factory.put(AZURE_STR, service);
      } else if (ProjectUtil.isNull(factory.get(serviceName)) && AWS_STR.equalsIgnoreCase(serviceName)) {
        CloudService service = new AwsCloudService();
        factory.put(AWS_STR, service);
      } else if (ProjectUtil.isNull(factory.get(serviceName)) && GCLOUD_STR.equalsIgnoreCase(serviceName)) {
        CloudService service = new GcpCloudService();
        factory.put(GCLOUD_STR, service);
      }
    }
    return factory.get(serviceName);
  }
}
