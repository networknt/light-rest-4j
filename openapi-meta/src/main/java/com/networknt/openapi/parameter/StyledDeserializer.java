package com.networknt.openapi.parameter;

import com.networknt.oas.model.Parameter;

import io.undertow.server.HttpServerExchange;

abstract class StyledDeserializer extends QueryParameterDeserializer{
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter) {
		ValueType valueType = getValueType(parameter);
		boolean exploade = parameter.isExplode();
		
		if (!isApplicable(valueType, exploade)) {
			return;
		}
		
		deserialize(exchange, parameter, valueType, exploade);
	}
	
	protected abstract void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade);
}
