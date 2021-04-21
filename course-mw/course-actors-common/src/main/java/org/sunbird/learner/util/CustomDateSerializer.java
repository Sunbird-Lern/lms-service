package org.sunbird.learner.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.models.course.batch.CourseBatch;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class CustomDateSerializer extends JsonSerializer<Date> {
    private SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ");
    private SimpleDateFormat sd2 = new SimpleDateFormat("yyyy-MM-dd");
    private List<String> dateOnlyFormat = JsonKey.CHANGE_IN_DATE_FORMAT;
    private List<String> setEndOfDay = JsonKey.SET_END_OF_DAY;
    public LoggerUtil logger = new LoggerUtil(this.getClass());

    public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        TokenBuffer buffer = (TokenBuffer) jgen;
        ObjectCodec codec = buffer.getCodec();
        buffer.setCodec(null);
        if (((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString() != null && ((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString()) {
            if (dateOnlyFormat.contains(jgen.getOutputContext().getCurrentName()))
                buffer.writeObject(sd2.format(value));
            else
                buffer.writeObject(sd.format(value));
        } else {
            value = setEndOfDay(value, jgen);
            buffer.writeObject(value);
        }
        buffer.setCodec(codec);
    }

    public Date setEndOfDay(Date value, JsonGenerator jgen) {
        try {
            if (setEndOfDay.contains(jgen.getOutputContext().getCurrentName())) {
                Calendar cal =
                        Calendar.getInstance(
                                TimeZone.getTimeZone(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_TIMEZONE)));
                cal.setTime(sd2.parse(sd2.format(value)));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                return cal.getTime();
            }
        } catch (ParseException e) {
            logger.error(null, "CustomDateSerializer:setEndOfDay: Exception occurred with message = " + e.getMessage(), e);
        }
        return value;
    }
}
