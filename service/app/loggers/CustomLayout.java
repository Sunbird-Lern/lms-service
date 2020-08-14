package loggers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;

import java.util.LinkedHashMap;
import java.util.Map;

public class CustomLayout extends JsonLayout {

    @Override
    protected Map toJsonMap(ILoggingEvent event) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        addTimestamp(TIMESTAMP_ATTR_NAME, this.includeTimestamp, event.getTimeStamp(), map);
        add(LEVEL_ATTR_NAME, this.includeLevel, String.valueOf(event.getLevel()), map);
        add("tname", this.includeThreadName, event.getThreadName(), map);
        add("lname", this.includeLoggerName, event.getLoggerName(), map);
        add("msg", this.includeFormattedMessage, event.getFormattedMessage(), map);
        addThrowableInfo(EXCEPTION_ATTR_NAME, this.includeException, event, map);
        addCustomDataToJsonMap(map, event);
        return map;
    }
}
