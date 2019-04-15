package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiOperation;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public interface ParameterDeserializer {
	static Set<String> getCandidateQueryParams(HttpServerExchange exchange){
		Set<String> candidateQueryParams = new HashSet<>();
		
		exchange.getQueryParameters().keySet().forEach(key->{
			if (!key.contains(Delimiters.LEFT_BRACKET)) {
				candidateQueryParams.add(key);
			}else {// for deepObject serialization
				candidateQueryParams.add(key.substring(0, key.indexOf(Delimiters.LEFT_BRACKET)));
			}
		});
		
		return candidateQueryParams;
	}
	
	static Set<String> getCandidatePathParams(HttpServerExchange exchange){
		return exchange.getPathParameters().keySet();
	}
	
	static void deserialize(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
		Set<String> candidateQueryParams = getCandidateQueryParams(exchange);
		Set<String> candidatePathParams = getCandidatePathParams(exchange);
		
		openApiOperation.getOperation().getParameters().forEach(p->{
			ParameterType type = ParameterType.of(p.getIn());
			
			if (ParameterType.QUERY==type) {
				ParameterType.QUERY.getDeserializer().deserialize(exchange, p, candidateQueryParams);
			}else if (ParameterType.PATH==type) {
				ParameterType.PATH.getDeserializer().deserialize(exchange, p, candidatePathParams);
			}
		});
	}
	
	boolean isApplicable(HttpServerExchange exchange, Parameter parameter, Set<String> candidateParams);
	
	default void deserialize(HttpServerExchange exchange, Parameter parameter, Set<String> candidateParams) {
		if (!isApplicable(exchange, parameter, candidateParams)) {
			return;
		}
		
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
