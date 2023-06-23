package org.sunbird.common.models.util.url;

import org.apache.commons.lang.StringUtils;

import static org.sunbird.common.models.util.ProjectUtil.propertiesCache;

public class EsConfigUtil {

    public static String getConfigValue(String key) {
        if (StringUtils.isNotBlank(System.getenv(key))) {
            return System.getenv(key);
        }
        return propertiesCache.readProperty(key);
    }
}
