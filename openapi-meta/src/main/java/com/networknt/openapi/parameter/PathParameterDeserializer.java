package com.networknt.openapi.parameter;

import com.networknt.oas.model.Parameter;

import io.undertow.server.HttpServerExchange;

public class PathParameterDeserializer implements ParameterDeserializer{

	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter) {
		
	}
}
