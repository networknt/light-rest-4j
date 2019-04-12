package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.utility.StringUtils;

public enum QueryParameterStyle {
	FORM(new FormStyleDeserializer()),
	SPACEDELIMITED(new SpaceDelimitedStyleDeserializer()),
	PIPEDELIMITED(new PipeDelimitedStyleDeserializer()),
	DEEPOBJECT(new DeepObjectStyleDeserializer());
	
	private static Map<String, QueryParameterStyle> lookup = new HashMap<>();
	private final StyleParameterDeserializer deserializer;
	
	private QueryParameterStyle(StyleParameterDeserializer deserializer) {
		this.deserializer = deserializer;
	}
	
	static {
		for (QueryParameterStyle style: QueryParameterStyle.values()) {
			lookup.put(style.name(), style);
		}
	}
	
	public static QueryParameterStyle of(String styleStr) {
		return lookup.get(StringUtils.trimToEmpty(styleStr).toUpperCase());
	}
	
	public static boolean is(String styleStr, QueryParameterStyle style) {
		return style == of(styleStr);
	}
	
	public StyleParameterDeserializer getDeserializer() {
		return this.deserializer;
	}
}
