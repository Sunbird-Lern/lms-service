package org.sunbird.learner.util;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JsonUtilTest {

	@Test
	public void testSerialize() throws Exception {
		Map<String, Object> input = new HashMap<String, Object>(){{
			put("test-key", false);
		}};
		String result = JsonUtil.serialize(input);
		assertTrue(StringUtils.isNotBlank(result));
		assertTrue(StringUtils.contains(result, "test-key"));
	}

	@Test
	public void testDeserialize() throws Exception {
		String input = "{\"test-key\":false}";
		Map<String, Object> result = JsonUtil.deserialize(input, Map.class);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(BooleanUtils.isFalse((Boolean) result.get("test-key")));
	}

	@Test
	public void testDeserializeWithInputStream() throws Exception {
		String input = "{\"test-key\":false}";
		InputStream inputStream = new ByteArrayInputStream(input.getBytes(Charset.forName("UTF-8")));
		Map<String, Object> result = JsonUtil.deserialize(inputStream, Map.class);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(BooleanUtils.isFalse((Boolean) result.get("test-key")));
	}

	@Test
	public void testConvert() throws Exception {
		String input = "{\"test-key\":false}";
		Map<String, Object> result = JsonUtil.deserialize(input, Map.class);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(BooleanUtils.isFalse((Boolean) result.get("test-key")));
	}

	@Test
	public void testConvertString() throws Exception {
		String input = "{\"test-key\":false}";
		Map<String, Object> result = JsonUtil.convert(JsonUtil.deserialize(input,Map.class), Map.class);
		assertTrue(MapUtils.isNotEmpty(result));
		assertTrue(BooleanUtils.isFalse((Boolean) result.get("test-key")));
	}

}
