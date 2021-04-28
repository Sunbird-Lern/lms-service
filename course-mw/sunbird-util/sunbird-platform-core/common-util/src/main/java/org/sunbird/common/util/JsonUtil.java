package org.sunbird.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.text.SimpleDateFormat;

public class JsonUtil {

	private static ObjectMapper mapper = new ObjectMapper();
	private static ObjectMapper mapperWithDateFormat = new ObjectMapper();
	static {
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public static String serialize(Object obj) throws Exception {
		mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS , false);
		return mapper.writeValueAsString(obj);
	}

	public static <T> T deserialize(String value, Class<T> clazz) throws Exception {
		return mapper.readValue(value, clazz);
	}

	public static <T> T deserialize(InputStream value, Class<T> clazz) throws Exception {
		return mapper.readValue(value, clazz);
	}

	public static <T> T convert(Object value, Class<T> clazz) throws Exception {
		return mapper.convertValue(value, clazz);
	}

	// pass @dateFormat with timezone for serialization of dateType variables
	public static <T> T convertWithDateFormat(Object value, Class<T> clazz, SimpleDateFormat dateFormat) throws Exception {
		mapperWithDateFormat.setDateFormat(dateFormat);
		return mapperWithDateFormat.convertValue(value, clazz);
	}
}
