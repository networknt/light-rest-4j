package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.utility.StringUtils;

public enum ParameterType {
	PATH(new PathParameterDeserializer()),
	QUERY(new QueryParameterDeserializer());
	
	private static Map<String, ParameterType> lookup = new HashMap<>();
	
	private final ParameterDeserializer deserializer;
	
	private ParameterType(ParameterDeserializer deserializer) {
		this.deserializer = deserializer;
	}
	
	static {
		for (ParameterType type: ParameterType.values()) {
			lookup.put(type.name(), type);
		}
	}
	
	public static ParameterType of(String typeStr) {
		return lookup.get(StringUtils.trimToEmpty(typeStr).toUpperCase());
	}
	
	public static boolean is(String typeStr, ParameterType type) {
		return type == of(typeStr);
	}
	
	public ParameterDeserializer getDeserializer() {
		return this.deserializer;
	}
}
