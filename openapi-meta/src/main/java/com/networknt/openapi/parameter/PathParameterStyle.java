package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.utility.StringUtils;

public enum PathParameterStyle {
	SIMPLE(new SimpleStyleDeserializer()),
	LABEL(new LabelStyleDeserializer()),
	MATRIX(new MatrixStyleDeserializer());
	
	private static Map<String, PathParameterStyle> lookup = new HashMap<>();
	private final ParameterDeserializer deserializer;
	
	private PathParameterStyle(ParameterDeserializer deserializer) {
		this.deserializer = deserializer;
	}
	
	static {
		for (PathParameterStyle style: PathParameterStyle.values()) {
			lookup.put(style.name(), style);
		}
	}
	
	public static PathParameterStyle of(String styleStr) {
		return lookup.get(StringUtils.trimToEmpty(styleStr).toUpperCase());
	}
	
	public static boolean is(String styleStr, PathParameterStyle style) {
		return style == of(styleStr);
	}
	
	public ParameterDeserializer getDeserializer() {
		return this.deserializer;
	}
}
