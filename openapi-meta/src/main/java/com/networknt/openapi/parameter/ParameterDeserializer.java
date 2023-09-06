package com.networknt.openapi.parameter;

import java.util.*;
import java.util.stream.Collectors;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiOperation;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Cookies;

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
	
	static void deserialize(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
		Set<String> candidateQueryParams = getCandidateQueryParams(exchange);
		Set<String> candidatePathParams = exchange.getPathParameters().keySet();
		Set<String> candidateHeaderParams = exchange.getRequestHeaders().getHeaderNames().stream().map(name->name.toString()).collect(Collectors.toSet());
		List<String> cookieNames = new ArrayList<>();
		exchange.requestCookies().forEach(s -> cookieNames.add(s.getName()));
		Set<String> candidateCookieParams = new HashSet<String>(cookieNames);
		
		openApiOperation.getOperation().getParameters().forEach(p->{
			ParameterType type = ParameterType.of(p.getIn());
			
			if (null!=type) {
				ParameterDeserializer deserializer = type.getDeserializer();
				
				switch(type){
				case QUERY:
					deserializer.deserialize(exchange, p, candidateQueryParams);
					break;
				case PATH:
					deserializer.deserialize(exchange, p, candidatePathParams);
					break;
				case HEADER:
					deserializer.deserialize(exchange, p, candidateHeaderParams);
					break;
				case COOKIE:
					deserializer.deserialize(exchange, p, candidateCookieParams);
					break;
				}
			}
		});
	}
	
	default boolean isApplicable(HttpServerExchange exchange, Parameter parameter, Set<String> candidateParams) {
		// HTTP header names are case insensitive (RFC 7230, https://tools.ietf.org/html/rfc7230#section-3.2)
		if(ParameterType.of(parameter.getIn()) == ParameterType.HEADER)
			return candidateParams.stream().anyMatch(s->parameter.getName().equalsIgnoreCase(s));
		else
			return candidateParams.contains(parameter.getName());
	}
	
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
