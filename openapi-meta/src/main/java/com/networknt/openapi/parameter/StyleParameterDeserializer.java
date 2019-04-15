package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;

public interface StyleParameterDeserializer {
	static ValueType getValueType(Parameter parameter) {
		Schema schema = parameter.getSchema();
		
		if (null!=schema) {
			return ValueType.of(schema.getType());
		}
		
		return null;
	}
	
	default Object deserialize(HttpServerExchange exchange, Parameter parameter) {
		ValueType valueType = getValueType(parameter);
		boolean exploade = parameter.isExplode();
		
		if (!isApplicable(valueType, exploade)) {
			return null;
		}
		
		return deserialize(exchange, parameter, valueType, exploade);
	}
	
	Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade);
	
	default boolean isApplicable(ValueType valueType, boolean exploade) {
		if (null == valueType || ValueType.PRIMITIVE == valueType) {
			return false;
		}
		
		return true;
	}
	
	default String getFirst(Deque<String> values, String key) {
		if (null!=values) {
			return StringUtils.trimToEmpty(values.peekFirst());
		}
		
		return StringUtils.EMPTY;
	}
	
	default List<String> asList(String str, String delimiter) {
		List<String> valueList = new ArrayList<>();
		
		if (!str.contains(delimiter)) {
			valueList.add(str);
		}else {
			String[] items = str.split("\\"+delimiter);
			
			for (String item: items) {
				valueList.add(item);
			}
		}
		
		return valueList;
	}
	
	default Map<String, String> asExploadeMap(String str, String delimiter) {
		Map<String, String> valueMap = new HashMap<>();
		String[] items = str.split("\\"+delimiter);
		
		for (String item: items) {
			String[] tokens = item.split(Delimiters.EQUAL);
			
			String key=null;
			String value=null;
			
			if (tokens.length>0) {
				key = tokens[0];
			}
			
			if (tokens.length>1) {
				value = tokens[1];
			}
			
			if (StringUtils.isNotBlank(key)) {
				valueMap.put(key, StringUtils.trimToEmpty(value));
			}
		}
		
		return valueMap;
	}
	
	default Map<String, String> asMap(String str, String delimiter) {
		if (StringUtils.isBlank(str)) {
			return Collections.emptyMap();
		}
		
		Map<String, String> valueMap = new HashMap<>();
		
		if (!str.contains(delimiter)) {
			valueMap.put(str, StringUtils.EMPTY);
		}else {
			String[] items = str.split("\\"+delimiter);
			
			int len = items.length/2;
			
			int keyIndex = 0;
			int valueIndex = 0;
			
			for (int i=0; i<len; ++i) {
				keyIndex = 2*i;
				valueIndex = 2*i + 1;
				
				valueMap.put(items[keyIndex], items[valueIndex]);
			}
			
			if (valueIndex<items.length-1) {
				valueMap.put(items[items.length-1], StringUtils.EMPTY);
			}
		}
		
		return valueMap;
	}		
	
	default String trimStart(String str, String start) {
		if (StringUtils.isNotBlank(str) && StringUtils.isNotBlank(start) && str.startsWith(start)) {
			int pos = start.length();
			
			return str.substring(pos);
		}
		
		return str;
	}
	
	default String replace(String str, String target, String replacement) {
		if (StringUtils.isNotBlank(str)) {
			return str.replace(target, replacement);
		}
		
		return str;
	}
}
