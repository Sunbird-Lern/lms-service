package util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Common {

    public static Map<String, String[]> getRequestHeadersInArray(Map<String, List<String>> requestHeaders) {
        Map<String, String[]> requestHeadersArray = new HashMap();
        requestHeaders.entrySet().forEach(entry -> {
            requestHeadersArray.put(entry.getKey(), (String[]) entry.getValue().toArray(new String[0]));
        });
        return requestHeadersArray;
    }
}
