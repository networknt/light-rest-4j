package com.networknt.openapi.parameter;

import java.util.HashMap;
import java.util.Map;

import com.networknt.utility.StringUtils;

public enum PathParameterStyle {
	SIMPLE,
	LABEL,
	MATRIX;
	
	private static Map<String, PathParameterStyle> lookup = new HashMap<>();
	
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
}
