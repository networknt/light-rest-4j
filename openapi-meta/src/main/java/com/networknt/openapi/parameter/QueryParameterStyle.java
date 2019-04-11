package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.utility.StringUtils;

public enum QueryParameterStyle {
	FORM,
	SPACEDELIMITED,
	PIPEDELIMITED,
	DEEPOBJECT;
	
	private static Map<String, QueryParameterStyle> lookup = new HashMap<>();
	
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
}
