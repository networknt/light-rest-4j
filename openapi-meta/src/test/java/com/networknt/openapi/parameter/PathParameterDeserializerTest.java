package com.networknt.openapi.parameter;

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
		
		exchange.addPathParam(PARAM_NAME, ";id=5");
		
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
		
		exchange.addPathParam(PARAM_NAME, ";id=3,4,5");
		
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
		
		exchange.addPathParam(PARAM_NAME, ";id=3;id=4;id=5");
		
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
		
		exchange.addPathParam(PARAM_NAME, ";id=role,admin,firstName,Alex");
		
		checkMap(exchange, parameter, 2);
	}
	
	@Test
	public void test_matrix_object_exploade() {
		Schema schema = new PojoSchema();
		schema.setType(ValueType.OBJECT.name().toLowerCase());
		
		Parameter parameter = new PoJoParameter(PARAM_NAME,
				ParameterType.PATH.name().toLowerCase(),
				PathParameterStyle.MATRIX.name().toLowerCase(),
				true,
				schema);
		
		HttpServerExchange exchange = new HttpServerExchange(null);
		
		exchange.addPathParam(PARAM_NAME, ";role=admin;firstName=Alex");
		
		checkMap(exchange, parameter, 2);
	}
	
	protected void checkArray(HttpServerExchange exchange, Parameter parameter) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter);
		
		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS));
	}
	
	protected void checkMap(HttpServerExchange exchange, Parameter parameter, int expectedSize) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter);
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS), expectedSize);
	}
	
	protected void checkString(HttpServerExchange exchange, Parameter parameter, String expectedValue) {
		ParameterType.PATH.getDeserializer().deserialize(exchange, parameter);
		
		checkString(exchange.getAttachment(OpenApiHandler.DESERIALIZED_PATH_PARAMETERS), expectedValue);
	}
}
