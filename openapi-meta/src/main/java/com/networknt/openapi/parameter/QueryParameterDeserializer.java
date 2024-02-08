package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Schema;
import com.networknt.openapi.OpenApiHandler;

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
	public StyleParameterDeserializer getStyleDeserializer(String style) {
		QueryParameterStyle styleDef = QueryParameterStyle.of(style);

		if (null==styleDef) {
			return null;
		}

		return styleDef.getDeserializer();
	}

	@Override
	public boolean isApplicable(HttpServerExchange exchange, Parameter parameter, Set<String> candidateParams) {
		if (!candidateParams.contains(parameter.getName())) {
			QueryParameterStyle style = QueryParameterStyle.of(parameter.getStyle());
			ValueType valueType = StyleParameterDeserializer.getValueType(parameter);

			return ValueType.OBJECT == valueType
					&& parameter.isExplode()
					&& QueryParameterStyle.FORM == style
					&& null!=parameter.getSchema().getProperties()
					&& parameter.getSchema().getProperties().keySet().stream().filter(prop->candidateParams.contains(prop)).findAny().isPresent();
		}

		return true;

	}
}

class FormStyleDeserializer implements StyleParameterDeserializer{
	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());

		if (valueType == ValueType.ARRAY) {
			List<String> valueList = new ArrayList<>();

			values.forEach(v->valueList.addAll(asList(v, Delimiters.COMMA)));

			return valueList;
		}else {
			Map<String, String> valueMap = new HashMap<>();
			Schema schema = parameter.getSchema();

			if (exploade) {
				schema.getProperties().keySet().forEach(k->valueMap.put(k, getFirst(exchange.getQueryParameters().get(k), k)));
			}else {
				values.forEach(v->valueMap.putAll(asMap(v, Delimiters.COMMA)));
			}

			return valueMap;
		}
	}

	@Override
	public boolean isApplicable(ValueType valueType, boolean expload) {
		return (valueType == ValueType.ARRAY && !expload) || valueType == ValueType.OBJECT;
	}
}

class SpaceDelimitedStyleDeserializer implements StyleParameterDeserializer{
	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());

		List<String> valueList = new ArrayList<>();

		values.forEach(v->valueList.addAll(asList(v, Delimiters.SPACE)));

		return valueList;
	}

	@Override
	public boolean isApplicable(ValueType valueType, boolean expload) {
		return (valueType == ValueType.ARRAY && !expload);
	}
}

class PipeDelimitedStyleDeserializer implements StyleParameterDeserializer{
	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Collection<String> values = exchange.getQueryParameters().get(parameter.getName());

		List<String> valueList = new ArrayList<>();

		values.forEach(v->valueList.addAll(asList(v, Delimiters.PIPE)));

		return valueList;
	}

	@Override
	public boolean isApplicable(ValueType valueType, boolean exploade) {
		return (valueType == ValueType.ARRAY && !exploade);
	}
}

class DeepObjectStyleDeserializer implements StyleParameterDeserializer{
	@Override
	public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType, boolean exploade) {
		Map<String, String> valueMap = new HashMap<>();
		Schema schema = parameter.getSchema();
		schema.getProperties().keySet().forEach(k->valueMap.put(k, getFirst(exchange.getQueryParameters().get(makeKey(parameter.getName(), k)), k)));

		return valueMap;
	}

	@Override
	public boolean isApplicable(ValueType valueType, boolean exploade) {
		return valueType == ValueType.OBJECT  && exploade;
	}

	private String makeKey(String paramName, String prop) {
		return String.format("%s[%s]", paramName, prop);
	}
}
