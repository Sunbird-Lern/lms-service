/** */
package mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.common.ProjectUtil;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;
import play.libs.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class will map the requested json data into custom class.
 *
 * @author Manzarul
 */
public class RequestMapper {
  public static LoggerUtil logger = new LoggerUtil(RequestMapper.class);

  /**
   * Method to map request
   *
   * @param requestData JsonNode
   * @param obj Class<T>
   * @exception RuntimeException
   * @return <T>
   */
  public static Object mapRequest(JsonNode requestData, Class obj) throws Exception {
    // First convert the JsonNode through our Scala collection conversion
    Map<String, Object> convertedMap = mapRequest(requestData);
    
    // Now convert the cleaned map to the target object type
    ObjectMapper mapper = new ObjectMapper();
    Object result = mapper.convertValue(convertedMap, obj);
    
    // For Request objects, ensure the internal request map also uses converted collections
    if (result instanceof Request) {
      Request requestObj = (Request) result;
      Map<String, Object> internalRequest = requestObj.getRequest();
      if (internalRequest != null) {
        Map<String, Object> convertedInternalRequest = new HashMap<>();
        for (Map.Entry<String, Object> entry : internalRequest.entrySet()) {
          convertedInternalRequest.put(entry.getKey(), convertScalaCollections(entry.getValue()));
        }
        requestObj.setRequest(convertedInternalRequest);
      }
    }
    
    return result;
  }

  /**
   * Method to map request with Scala collection conversion
   *
   * @param requestData JsonNode
   * @exception RuntimeException
   * @return Map<String, Object>
   */
  public static Map<String, Object> mapRequest(JsonNode requestData) throws Exception {
    if (requestData == null) {
      return new HashMap<>();
    }
    
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = mapper.convertValue(requestData, new TypeReference<Map<String, Object>>() {});
    
    // Convert any Scala collections (Maps, Lists, etc.) to Java collections
    Map<String, Object> convertedMap = new HashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object convertedValue = convertScalaCollections(entry.getValue());
      convertedMap.put(entry.getKey(), convertedValue);
    }
    
    return convertedMap;
  }

  /**
   * Helper method to convert Scala Map to Java Map recursively, including List conversions
   * @param obj The Scala Map to convert
   * @return Java Map
   */
  private static Object convertToJavaMap(Object obj) {
    if (obj instanceof Map && !(obj instanceof java.util.Map)) {
      try {
        // Convert Scala Map to Java Map
        scala.collection.Map<?, ?> scalaMap = (scala.collection.Map<?, ?>) obj;
        Map<Object, Object> javaMap = new LinkedHashMap<>();
        
        // Iterate through Scala map and convert each entry
        scala.collection.Iterator<?> iterator = scalaMap.iterator();
        while (iterator.hasNext()) {
          scala.Tuple2<?, ?> entry = (scala.Tuple2<?, ?>) iterator.next();
          Object key = entry._1();
          Object value = entry._2();
          
          // Recursively convert nested maps and handle Scala collections
          Object convertedValue = convertScalaCollections(value);
          javaMap.put(key, convertedValue);
        }
        
        return javaMap;
      } catch (Exception e) {
        logger.debug(null, "Failed to convert Scala Map to Java Map: " + e.getMessage() + 
                     ". Object type: " + obj.getClass().getName() + 
                     ". Returning original object. Exception: " + e.toString());
        return obj;
      }
    } else {
      return obj;
    }
  }
  
  /**
   * Convert Scala collections to Java collections recursively
   */
  private static Object convertScalaCollections(Object obj) {
    if (obj == null) {
      return null;
    }
    
    // Handle Scala Lists (including :: cons lists)
    if (obj instanceof scala.collection.Seq && !(obj instanceof java.util.List)) {
      try {
        scala.collection.Seq<?> scalaSeq = (scala.collection.Seq<?>) obj;
        List<Object> javaList = new ArrayList<>();
        
        scala.collection.Iterator<?> iterator = scalaSeq.iterator();
        while (iterator.hasNext()) {
          Object element = iterator.next();
          // Recursively convert nested collections
          Object convertedElement = convertScalaCollections(element);
          javaList.add(convertedElement);
        }
        
        return javaList;
      } catch (Exception e) {
        logger.debug(null, "Failed to convert Scala Seq to Java List: " + e.getMessage() + 
                     ". Object type: " + obj.getClass().getName() + 
                     ". Returning original object. Exception: " + e.toString());
        return obj;
      }
    }
    
    // Handle Scala Maps
    if (obj instanceof scala.collection.Map && !(obj instanceof java.util.Map)) {
      return convertToJavaMap(obj);
    }
    
    // Handle Java Lists (process recursively for nested collections)
    if (obj instanceof java.util.List) {
      List<?> javaList = (List<?>) obj;
      List<Object> convertedList = new ArrayList<>();
      for (Object element : javaList) {
        convertedList.add(convertScalaCollections(element));
      }
      return convertedList;
    }
    
    // Handle Java Maps (process recursively for nested collections)
    if (obj instanceof java.util.Map) {
      Map<?, ?> javaMap = (Map<?, ?>) obj;
      Map<Object, Object> convertedMap = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
        Object convertedValue = convertScalaCollections(entry.getValue());
        convertedMap.put(entry.getKey(), convertedValue);
      }
      return convertedMap;
    }
    
    // Return other objects as-is
    return obj;
  }

  /**
   * Helper method to convert Scala collections to Java collections
   * @param value The value to potentially convert
   * @param fieldName The field name for logging
   * @return Java collection or original value
   */
  private static Object convertScalaCollectionToJava(Object value, String fieldName) {
    if (value == null) {
      return value;
    }
    
    String className = value.getClass().getName();
    
    // Handle all Scala collection types
    if (className.startsWith("scala.collection")) {
      // Check if it's a Scala List (including :: cons lists)
      if (className.contains("List") || className.contains("$colon$colon") || 
          className.contains("immutable.Nil") || value instanceof Iterable) {
        List<Object> javaList = new ArrayList<>();
        
        try {
          // Handle Scala collections that are Iterable
          if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
              // Recursively convert nested structures
              if (item instanceof scala.collection.Map && !(item instanceof java.util.Map)) {
                item = convertToJavaMap(item);
              } else {
                item = convertScalaCollectionToJava(item, fieldName + "[element]");
              }
              javaList.add(item);
            }
          }
          
          return javaList;
          
        } catch (Exception e) {
          logger.error(null, "Failed to convert Scala collection for field " + fieldName + ": " + e.getMessage(), e);
          return value; // Return original value if conversion fails
        }
      }
    }
    
    return value;
  }
}
