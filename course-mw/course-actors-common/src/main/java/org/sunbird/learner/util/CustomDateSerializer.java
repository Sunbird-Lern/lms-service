package org.sunbird.learner.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.models.course.batch.CourseBatch;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CustomDateSerializer extends JsonSerializer<Date> {
    private SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ");
    private SimpleDateFormat sd2 = new SimpleDateFormat("yyyy-MM-dd");
    private List<String> dateOnlyFormat = JsonKey.CHANGE_IN_DATE_FORMAT;

    public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        TokenBuffer buffer = (TokenBuffer) jgen;
        ObjectCodec codec = buffer.getCodec();
        buffer.setCodec(null);
        if (((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString() != null && ((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString()) {
            if (dateOnlyFormat.contains(jgen.getOutputContext().getCurrentName()))
                buffer.writeObject(sd2.format(value));
            else
                buffer.writeObject(sd.format(value));
        } else
            buffer.writeObject(value);
        buffer.setCodec(codec);
    }
}
