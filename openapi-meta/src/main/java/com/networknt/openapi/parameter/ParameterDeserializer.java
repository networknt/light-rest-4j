package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiOperation;

import io.undertow.server.HttpServerExchange;

public class ParameterDeserializer {
	private static final String COMMA=",";
	private static final String SPACE=" ";
	private static final String PIPE="|";
	private static final String DOT="|";
	private static final String SEMICOLON=";";
	
	
	public static void deserialize(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
		openApiOperation.getOperation().getParameters().forEach(p->deserializeParameter(exchange, p));
	}
	
	public static void deserializeParameter(HttpServerExchange exchange, Parameter parameter) {
		ParameterType type = ParameterType.of(parameter.getIn());
		
		switch (type) {
		case PATH:
			deserializePathParameter(exchange, parameter);
			break;
		case QUERY:
			deserializeQueryParameter(exchange, parameter);
			break;			
		}
	}
	
	public static void deserializeQueryParameter(HttpServerExchange exchange, Parameter parameter) {
		ValueType valueType = getValueType(parameter);
		
		if (null == valueType || ValueType.PRIMITIVE == valueType) {
			return;
		}
		
		if (ValueType.ARRAY == valueType && Boolean.TRUE.equals(parameter.getExplode())) {
			return;
		}
		
		QueryParameterStyle style = QueryParameterStyle.of(parameter.getStyle());
		
		if (null==style) {
			return;
		}
		
		switch(style){
		case FORM:
			Collection<String> values = exchange.getQueryParameters().get(parameter.getName());
			
			
		case SPACEDELIMITED:
		case PIPEDELIMITED:
		case DEEPOBJECT:
		}
	}
	
	public static void deserializePathParameter(HttpServerExchange exchange, Parameter parameter) {
		
	}
	
	public static ValueType getValueType(Parameter parameter) {
		Schema schema = parameter.getSchema();
		
		if (null!=schema) {
			return ValueType.of(schema.getType());
		}
		
		return null;
	}
	
}
