package com.networknt.openapi.parameter;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiHandler;

import io.undertow.server.HttpServerExchange;

public class PathParameterDeserializerTest extends ParameterDeserializerTest{
	@Test
	public void test_simple_array() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.SIMPLE.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "3,4,5");
		
		checkArray(exchange, parameter);
	}
	
	@Test
	public void test_simple_object_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.SIMPLE.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "role=admin,firstName=Alex");
		
		checkMap(exchange, parameter, 2);
	}

	@Test
	public void test_simple_object_no_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.SIMPLE.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "role,admin,firstName,Alex");
		
		checkMap(exchange, parameter, 2);
	}
	
	@Test
	public void test_label_array_explode() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.LABEL.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, ".3.4.5");
		
		checkArray(exchange, parameter);
	}
	
	@Test
	public void test_label_array_no_explode() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.LABEL.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, ".3,4,5");
		
		checkArray(exchange, parameter);
	}	
	
	@Test
	public void test_label_object_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.LABEL.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, ".role=admin.firstName=Alex");
		
		checkMap(exchange, parameter, 2);
	}
	
	@Test
	public void test_label_object_no_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.LABEL.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, ".role,admin,firstName,Alex");
		
		checkMap(exchange, parameter, 2);
	}

/**
 * undertow consider path parts following ';' as path parameters and tries to parse them too.
 * the below code simulates the parsing results in light-4j handler chains.	
 */
	@Test
	public void test_matrix_primitive() {
		Schema schema = new PojoSchema();
		schema.setType("string");
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "5");
		exchange.addPathParam(PARAM_NAME, "");
		
		checkString(exchange, parameter, "5");
	}
	
	@Test
	public void test_matrix_array_no_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "3,4,5");
		exchange.addPathParam(PARAM_NAME, "");
		
		checkArray(exchange, parameter);
	}
	
	@Test
	public void test_matrix_array_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.ARRAY.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "3;id=4;id=5");
		exchange.addPathParam(PARAM_NAME, "");
		
		checkArray(exchange, parameter);
	}
	
	@Test
	public void test_matrix_object_no_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				false,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		exchange.addPathParam(PARAM_NAME, "");
		exchange.addPathParam(PARAM_NAME, "role,admin,firstName,Alex");
		
		checkMap(exchange, parameter, 2);
	}
	
	@Test
	public void test_matrix_object_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		schema.setProperties(PROPS);
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, "");
		
		// we cannot rely on the parsing result at call in this scenario
		// so, we use requestURI which has the original request path
		exchange.setRequestURI(";role=admin;firstName=Alex");
		
		checkMap(exchange, parameter, 3);
	}
	
	@Test
	public void test_sub_string() {
		String s = "/a/b/c";
		
		int pos = s.indexOf(Delimiters.SLASH);
		
		assertTrue(pos>=0);
	}
	
	protected void checkArray(HttpServerExchange exchange, Parameter parameter) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter, exchange.getPathParameters().keySet());
		
		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS));
	}
	
	protected void checkMap(HttpServerExchange exchange, Parameter parameter, int expectedSize) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter, exchange.getPathParameters().keySet());
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS), expectedSize);
	}
	
	protected void checkString(HttpServerExchange exchange, Parameter parameter, String expectedValue) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter, exchange.getPathParameters().keySet());
		
		checkString(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS), expectedValue);
	}
}
