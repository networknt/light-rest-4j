package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public class QueryParameterDeserializer implements ParameterDeserializer{
	public QueryParameterDeserializer() {
	}
	
	@Override
	public AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return OpenApiHandler.DESERIALIZED_QUERY_PARAMETERS;
	}
	
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter) {
		QueryParameterStyle style = QueryParameterStyle.of(parameter.getStyle());
		
		if (null==style) {
			return;
		}
		
		style.getDeserializer().deserialize(exchange, parameter);
	}
	
	protected boolean isApplicable(ValueType valueType, boolean exploade) {
		if (null == valueType || ValueType.PRIMITIVE == valueType) {
			return false;
		}
		
		return true;
	}
	
	public String getFirst(Deque<String> values, String key) {
		if (null!=values) {
			return StringUtils.trimToEmpty(values.peekFirst());
		}
		
		return StringUtils.EMPTY;
	}
	
	public List<String> asList(String str, String delimiter) {
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
	
	public Map<String, String> asMap(String str, String delimiter) {
		Map<String, String> valueMap = new HashMap<>();
		
		if (!str.contains(delimiter)) {
			valueMap.put(str, StringUtils.EMPTY);
		}else {
			String[] items = str.split(delimiter);
			
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
}

class FormStyleDeserializer extends StyledDeserializer{
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());
		
		if (valueType == ValueType.ARRAY) {
			List<String> valueList = new ArrayList<>();
			
			values.forEach(v->valueList.addAll(asList(v, COMMA)));
			
			attach(exchange, parameter.getName(), valueList);
		}else {
			Map<String, String> valueMap = new HashMap<>();
			Schema schema = parameter.getSchema();
			
			if (exploade) {
				schema.getProperties().keySet().forEach(k->valueMap.put(k, getFirst(exchange.getQueryParameters().get(k), k)));
				
				attach(exchange, parameter.getName(), valueMap);
			}else {
				values.forEach(v->valueMap.putAll(asMap(v, COMMA)));
				
				attach(exchange, parameter.getName(), valueMap);
			}
		}
	}
	
	@Override
	protected boolean isApplicable(ValueType valueType, boolean expload) {
		return (valueType == ValueType.ARRAY && !expload) || valueType == ValueType.OBJECT;
	}
}

class SpaceDelimitedStyleDeserializer extends StyledDeserializer{
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());
		
		List<String> valueList = new ArrayList<>();
		
		values.forEach(v->valueList.addAll(asList(v, SPACE)));
		
		attach(exchange, parameter.getName(), valueList);
	}
	
	@Override
	protected boolean isApplicable(ValueType valueType, boolean expload) {
		return (valueType == ValueType.ARRAY && !expload);
	}
}

class PipeDelimitedStyleDeserializer extends StyledDeserializer{
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());
		
		List<String> valueList = new ArrayList<>();
		
		values.forEach(v->valueList.addAll(asList(v, PIPE)));
		
		attach(exchange, parameter.getName(), valueList);
	}
	
	@Override
	protected boolean isApplicable(ValueType valueType, boolean exploade) {
		return (valueType == ValueType.ARRAY && !exploade);
	}
}

class DeepObjectStyleDeserializer extends StyledDeserializer{
	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Map<String, String> valueMap = new HashMap<>();
		Schema schema = parameter.getSchema();
		schema.getProperties().keySet().forEach(k->valueMap.put(k, getFirst(exchange.getQueryParameters().get(makeKey(parameter.getName(), k)), k)));
		
		attach(exchange, parameter.getName(), valueMap);
	}
	
	@Override
	protected boolean isApplicable(ValueType valueType, boolean exploade) {
		return valueType == ValueType.OBJECT  && exploade;
	}
	
	private String makeKey(String paramName, String prop) {
		return String.format("%s[%s]", paramName, prop);
	}
}
