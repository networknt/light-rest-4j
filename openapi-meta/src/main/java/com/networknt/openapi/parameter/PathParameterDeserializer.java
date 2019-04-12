package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public class PathParameterDeserializer implements ParameterDeserializer{
	@Override
	public AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return OpenApiHandler.DESERIALIZED_PATH_PARAMETERS;
	}

	@Override
	public StyleParameterDeserializer getStyleDeserializer(String style) {
		PathParameterStyle styleDef = PathParameterStyle.of(style);
		
		if (null==styleDef) {
			return null;
		}
		
		return styleDef.getDeserializer();
	}
}

class SimpleStyleDeserializer implements StyleParameterDeserializer{

	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
		
		Collection<String> values = exchange.getPathParameters().get(parameter.getName());
		
		if (ValueType.ARRAY == valueType) {
			List<String> valueList = new ArrayList<>();
			
			values.forEach(v->valueList.addAll(asList(v, COMMA)));
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			values.forEach(v->valueMap.putAll(exploade?asExploadeMap(v, COMMA):asMap(v, COMMA)));
			
			return valueMap;
		}
		
		return null;
	}
}

class LabelStyleDeserializer implements StyleParameterDeserializer{

	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
		Collection<String> values = exchange.getPathParameters().get(parameter.getName());
		String delimiter = exploade?DOT:COMMA;
		
		if (ValueType.ARRAY == valueType) {
			List<String> valueList = new ArrayList<>();
			
			values.forEach(v->valueList.addAll(asList(trimStart(v, DOT), delimiter)));
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			values.forEach(v->valueMap.putAll(exploade?asExploadeMap(trimStart(v, DOT), delimiter):asMap(trimStart(v, DOT), delimiter)));
			
			return valueMap;
		}
		
		return null;
	}
}

class MatrixStyleDeserializer implements StyleParameterDeserializer{
	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
		Collection<String> values = exchange.getPathParameters().get(parameter.getName());
		String delimiter = exploade?SEMICOLON:COMMA;
		String start = String.format("%s%s=", SEMICOLON, parameter.getName());
		
		if (ValueType.PRIMITIVE == valueType) {
			StringBuilder builder = new StringBuilder();
			values.forEach(v->builder.append(trimStart(v, start)));
			return builder.toString();
		}else if (ValueType.ARRAY == valueType) {
			List<String> valueList = new ArrayList<>();
			
			if (!exploade) {
				values.forEach(v->valueList.addAll(asList(trimStart(v, start), delimiter)));
			}else {
				String prefix = String.format("%s=", parameter.getName());
				values.forEach(v->valueList.addAll(asList(replace(trimStart(v, SEMICOLON), prefix,StringUtils.EMPTY), delimiter)));
			}
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			values.forEach(v->valueMap.putAll(exploade?asExploadeMap(trimStart(v, SEMICOLON), delimiter):asMap(trimStart(v, start), delimiter)));
			
			return valueMap;
		}
		
		return null;
	}
	
	@Override
	public boolean isApplicable(ValueType valueType, boolean exploade) {
		return null!=valueType;
	}
}