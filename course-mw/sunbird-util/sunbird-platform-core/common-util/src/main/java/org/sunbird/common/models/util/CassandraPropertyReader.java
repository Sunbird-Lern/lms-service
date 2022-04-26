package org.sunbird.common.models.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * This class will be used to read cassandratablecolumn properties file.
 *
 * @author Amit Kumar
 */
public class CassandraPropertyReader {

  private final Properties properties = new Properties();
  private static final String file = "cassandratablecolumn.properties";
  private static CassandraPropertyReader cassandraPropertyReader = null;
  public LoggerUtil logger = new LoggerUtil(this.getClass());

  /** private default constructor */
  private CassandraPropertyReader() {
    InputStream in = this.getClass().getClassLoader().getResourceAsStream(file);
    try {
      properties.load(in);
    } catch (IOException e) {
      logger.error(null, "Error in properties cache", e);
    }
  }

  public static CassandraPropertyReader getInstance() {
    if (null == cassandraPropertyReader) {
      synchronized (CassandraPropertyReader.class) {
        if (null == cassandraPropertyReader) {
          cassandraPropertyReader = new CassandraPropertyReader();
        }
      }
    }
    return cassandraPropertyReader;
  }

  /**
   * Method to read value from resource file .
   *
   * @param key property value to read
   * @return value corresponding to given key if found else will return key itself.
   */
  public String readProperty(String key) {
    return properties.getProperty(key) != null ? properties.getProperty(key) : key;
  }

  /**
   * Method to read value from resource file .
   *
   * @param key to read property key
   * @return key corresponding to given value if found else will return value itself.
   */
  public String readPropertyValue(String key) {
    List<Map.Entry<Object, Object>> s = properties.entrySet()
            .stream()
            .filter(entry -> key.equals(entry.getValue()))
            .collect(Collectors.toList());
    return s.isEmpty() ? key : (String) s.get(0).getKey();
  }
}
