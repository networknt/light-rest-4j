package com.networknt.openapi.parameter;

import org.junit.Test;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiHandler;

import io.undertow.server.HttpServerExchange;

public class QueryParameterDeserializerTest extends ParameterDeserializerTest{
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

		checkArray(exchange, parameter);
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

		checkMap(exchange, parameter, 3);
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

		checkMap(exchange, parameter, 2);
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

		checkArray(exchange, parameter);
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

		checkArray(exchange, parameter);
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

		checkMap(exchange, parameter, 3);
	}

	protected void checkArray(HttpServerExchange exchange, Parameter parameter) {
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter, ParameterDeserializer.getCandidateQueryParams(exchange));

		checkArray(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS));
	}

	protected void checkMap(HttpServerExchange exchange, Parameter parameter, int expectedSize) {
		ParameterType.QUERY.getDeserializer().deserialize(exchange, parameter, ParameterDeserializer.getCandidateQueryParams(exchange));
		checkMap(exchange.getAttachment(OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS), expectedSize);
	}
}
