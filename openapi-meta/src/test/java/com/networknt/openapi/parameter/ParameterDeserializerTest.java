package com.networknt.openapi.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;

import com.networknt.oas.model.Schema;

@SuppressWarnings("rawtypes")
public abstract class ParameterDeserializerTest {
	protected static final String PARAM_NAME="id";
	protected static final String ROLE="role";
	protected static final String FIRST_NAME="firstName";
	protected static final String ADMIN="admin";
	protected static final String ALEX="Alex";
	protected static final String[] VALUES = {"3", "4", "5"};
	protected static final String LAST_NAME="lastName";
	protected static final Map<String, Schema> PROPS=new HashMap<>();

	@BeforeAll
	public static void setup() {
		PROPS.put("role", new PojoSchema());
		PROPS.put("firstName", new PojoSchema());
		PROPS.put("lastName", new PojoSchema());
	}

	protected void checkMap(Map<String, Object> result, int expectedSize) {
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof Map);

		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof Map);

		Map valueMap = ((Map)result.get(PARAM_NAME));

		assertTrue(valueMap.size() == expectedSize);
		assertEquals(ADMIN, valueMap.get(ROLE));
		assertEquals(ALEX, valueMap.get(FIRST_NAME));
	}

	protected void checkArray(Map<String, Object> result) {
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof List);

		List valueList = ((List)result.get(PARAM_NAME));

		assertTrue(valueList.size() == 3);

		for (String v: VALUES) {
			assertTrue(valueList.contains(v));
		}
	}

	protected void checkString(Map<String, Object> result, String expectedValue) {
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof String);

		String value = ((String)result.get(PARAM_NAME));

		assertEquals(expectedValue, value);
	}
}
