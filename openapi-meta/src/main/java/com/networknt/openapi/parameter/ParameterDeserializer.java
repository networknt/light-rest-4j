package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiOperation;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public interface ParameterDeserializer {
	static final String COMMA=",";
	static final String SPACE=" ";
	static final String PIPE="|";
	static final String DOT=".";
	static final String SEMICOLON=";";
	
	void deserialize(HttpServerExchange exchange, Parameter parameter);
	
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
	
	default ValueType getValueType(Parameter parameter) {
		Schema schema = parameter.getSchema();
		
		if (null!=schema) {
			return ValueType.of(schema.getType());
		}
		
		return null;
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
