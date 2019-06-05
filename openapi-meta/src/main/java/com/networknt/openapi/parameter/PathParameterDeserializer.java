package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
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
			
			values.forEach(v->valueList.addAll(asList(v, Delimiters.COMMA)));
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			values.forEach(v->valueMap.putAll(exploade?asExploadeMap(v, Delimiters.COMMA):asMap(v, Delimiters.COMMA)));
			
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
		String delimiter = exploade?Delimiters.DOT:Delimiters.COMMA;
		
		if (ValueType.ARRAY == valueType) {
			List<String> valueList = new ArrayList<>();
			
			values.forEach(v->valueList.addAll(asList(trimStart(v, Delimiters.DOT), delimiter)));
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			values.forEach(v->valueMap.putAll(exploade?asExploadeMap(trimStart(v, Delimiters.DOT), delimiter):asMap(trimStart(v, Delimiters.DOT), delimiter)));
			
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
		String delimiter = exploade?Delimiters.SEMICOLON:Delimiters.COMMA;
		String start = String.format("%s%s=", Delimiters.SEMICOLON, parameter.getName());
		
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
				values.forEach(v->valueList.addAll(asList(replace(trimStart(v, Delimiters.SEMICOLON), prefix,StringUtils.EMPTY), delimiter)));
			}
			
			if (StringUtils.isBlank(valueList.get(valueList.size()-1))) {
				// this is a undertow-specific trick.
				// undertow parses matrix style path parameters and removes path parameters from request path
				// as a result, a space is added by com.networknt.handler.Handler.start()
				
				valueList.remove(valueList.size()-1);
			}
			
			return valueList;			
		}else if (ValueType.OBJECT == valueType) {
			Map<String, String> valueMap = new HashMap<>();
			
			if (!exploade) {
				values.forEach(v->valueMap.putAll(asMap(v, delimiter)));
			}else {
				Schema schema = parameter.getSchema();
				String requestURI = exchange.getRequestURI();
				
				schema.getProperties().keySet().forEach(k->valueMap.put(k, getValue(k, requestURI)));
			}
			
			return valueMap;
		}
		
		return null;
	}
	
	private String getValue(String prop, String uri) {
		String key = String.format(";%s=", prop);
		
		if (StringUtils.containsIgnoreCase(uri,  key)) {
			String value = uri.substring(uri.indexOf(key) + key.length());
			int nextSemiColon = value.indexOf(Delimiters.SEMICOLON);
			int nextSlash = value.indexOf(Delimiters.SLASH);
			int end = Math.min(nextSemiColon, nextSlash);
			
			if (nextSemiColon>=0 && nextSlash>=0) {
				value = value.substring(0, end);
			}else if (nextSemiColon>=0) {
				value = value.substring(0, nextSemiColon);
			}else if (nextSlash>=0) {
				value = value.substring(0, nextSlash);
			}
			
			return value;
		}
		
		return StringUtils.EMPTY;
	}
	
	@Override
	public boolean isApplicable(ValueType valueType, boolean exploade) {
		return null!=valueType;
	}
}