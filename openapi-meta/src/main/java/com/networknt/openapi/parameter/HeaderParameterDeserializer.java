package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HttpString;

public class HeaderParameterDeserializer implements ParameterDeserializer {
	private static final String SIMPLE="simple";

	@Override
	public AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return OpenApiHandler.DESERIALIZED_HEADER_PARAMETERS;
	}	
	
	@Override
	public StyleParameterDeserializer getStyleDeserializer(String style) {
		if (StringUtils.isNotBlank(style) && !SIMPLE.equalsIgnoreCase(style)) {
			return null;
		}
		
		return new StyleParameterDeserializer() {

			@Override
			public Object deserialize(HttpServerExchange exchange, Parameter parameter, 
					ValueType valueType,
					boolean exploade) {
				Collection<String> values = exchange.getRequestHeaders().get(new HttpString(parameter.getName()));
				
				if (ValueType.ARRAY == valueType) {
					List<String> valueList = new ArrayList<>();
					
					values.forEach(v->valueList.addAll(asList(v, Delimiters.COMMA)));
					
					return valueList;			
				}else if (ValueType.OBJECT == valueType) {
					Map<String, String> valueMap = new HashMap<>();
					values.forEach(v->valueMap.putAll(exploade?asExploadeMap(v, Delimiters.COMMA):asMap(v, Delimiters.COMMA)));
					
					return valueMap;
				}
				
				return null;
			}
			
		};
	}
}

