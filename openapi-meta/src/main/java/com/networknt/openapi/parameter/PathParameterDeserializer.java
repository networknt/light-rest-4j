package com.networknt.openapi.parameter;

import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiHandler;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

public class PathParameterDeserializer implements ParameterDeserializer{
	@Override
	public AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return OpenApiHandler.DESERIALIZED_PATH_PARAMETERS;
	}

	@Override
	public void deserialize(HttpServerExchange exchange, Parameter parameter) {
		PathParameterStyle style = PathParameterStyle.of(parameter.getStyle());
		
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
}

class SimpleStyleDeserializer extends StyledDeserializer{

	@Override
	protected void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
	}
	
	@Override
	protected boolean isApplicable(ValueType valueType, boolean expload) {
		return valueType == ValueType.OBJECT;
	}
}

class LabelStyleDeserializer extends StyledDeserializer{

	@Override
	protected void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
	}
}

class MatrixStyleDeserializer extends StyledDeserializer{

	@Override
	protected void deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
			boolean exploade) {
	}
}