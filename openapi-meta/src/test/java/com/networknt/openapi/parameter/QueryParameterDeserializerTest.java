package com.networknt.openapi.parameter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiHandler;

import io.undertow.server.HttpServerExchange;

@SuppressWarnings("rawtypes")
public class QueryParameterDeserializerTest {
	private static final String PARAM_NAME="id";
	private static final String ROLE="role";
	private static final String FIRST_NAME="firstName";
	private static final String ADMIN="admin";
	private static final String ALEX="Alex";
	private static final String[] VALUES = {"3", "4", "5"};
	@SuppressWarnings("unused")
	private static final String LAST_NAME="lastName";
	private static final Map<String, Schema> PROPS=new HashMap<>();
	
	@BeforeClass
	public static void setup() {
		PROPS.put("role", new PojoSchema());
		PROPS.put("firstName", new PojoSchema());
		PROPS.put("lastName", new PojoSchema());
	}
	
	@Test
	public void test_form_array() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.FORM.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(PARAM_NAME, "3,4,5");
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS));
	}
	
	@Test
	public void test_form_object_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		schema.setProperties(PROPS);
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.FORM.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(ROLE, "admin");
		exchange.addQueryParam(FIRST_NAME, "Alex");
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS), 3);
	}
	
	@Test
	public void test_form_object_no_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		schema.setProperties(PROPS);
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.FORM.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(PARAM_NAME, "role,admin,firstName,Alex");
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS), 2);
	}
	
	@Test
	public void test_spacedelimited_array() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.SPACEDELIMITED.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(PARAM_NAME, "3 4 5");
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS));
	}
	
	@Test
	public void test_pipedelimited_array() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.PIPEDELIMITED.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(PARAM_NAME, "3|4|5");
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS));
	}
	
	@Test
	public void test_deepobject() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		schema.setProperties(PROPS);
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.QUERY.name().toLowerCase(),
				QueryParameterStyle.DEEPOBJECT.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addQueryParam(String.format("%s[%s]", PARAM_NAME, ROLE), ADMIN);
		exchange.addQueryParam(String.format("%s[%s]", PARAM_NAME, FIRST_NAME), ALEX);
		
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter);
		
		
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS), 3);
	}
	
	public void checkMap(Map<String, Object> result, int expectedSize) {
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof Map);
		
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof Map);
		
		Map valueMap = ((Map)result.get(PARAM_NAME));
		
		assertTrue(valueMap.size() == expectedSize);
		assertEquals(valueMap.get(ROLE), ADMIN);
		assertEquals(valueMap.get(FIRST_NAME), ALEX);		
	}
	
	public void checkArray(Map<String, Object> result) {
		assertTrue(null!=result && !result.isEmpty());
		assertTrue(result.containsKey(PARAM_NAME));
		assertTrue(result.get(PARAM_NAME) instanceof List);
		
		List valueList = ((List)result.get(PARAM_NAME));
		
		assertTrue(valueList.size() == 3);
		
		for (String v: VALUES) {
			assertTrue(valueList.contains(v));
		}
	}
}
