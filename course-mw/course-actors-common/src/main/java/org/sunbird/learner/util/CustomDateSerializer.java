package org.sunbird.learner.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.sunbird.models.course.batch.CourseBatch;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomDateSerializer extends JsonSerializer<Date> {
    private SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        TokenBuffer buffer = (TokenBuffer) jgen;
        ObjectCodec codec = buffer.getCodec();
        buffer.setCodec(null);
        if (((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString() != null && ((CourseBatch) jgen.getCurrentValue()).getConvertDateAsString())
            buffer.writeObject(sd.format(value));
        else
            buffer.writeObject(value);
        buffer.setCodec(codec);
    }
}
