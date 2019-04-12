package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiOperation;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public interface ParameterDeserializer {
	default void deserialize(HttpServerExchange exchange, Parameter parameter) {
		StyleParameterDeserializer deserializer = getStyleDeserializer(parameter.getStyle());
		
		if (null==deserializer) {
			return;
		}
		
		Object valueObj = deserializer.deserialize(exchange, parameter);	
		
		if (null!=valueObj) {
			attach(exchange, parameter.getName(), valueObj);
		}
	}
	
	StyleParameterDeserializer getStyleDeserializer(String style);
	
	default AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return null;
	}
	
	static void deserialize(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
		openApiOperation.getOperation().getParameters().forEach(p->{
			ParameterType type = ParameterType.of(p.getIn());
			
			if (null!=type) {
				type.getDeserializer().deserialize(exchange, p);
			}
		});
	}
	
	default void attach(HttpServerExchange exchange, String key, Object value) {
		AttachmentKey<Map<String, Object>> paramType = getAttachmentKey();
		
		if (null!=paramType) {
			attach(exchange, paramType, key, value);
		}
	}
	
	default void attach(HttpServerExchange exchange, AttachmentKey<Map<String, Object>> paramType, String key, Object value) {
		Map<String, Object> paramMap = exchange.getAttachment(paramType);
		
		if (null == paramMap) {
			exchange.putAttachment(paramType, paramMap=new HashMap<>());
		}
		
		paramMap.put(key, value);
	}
}
