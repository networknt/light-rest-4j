package com.networknt.openapi.parameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.oas.model.Parameter;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.utility.StringUtils;

import io.undertow.UndertowOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;

public class CookieParameterDeserializer implements ParameterDeserializer {
	private static final String FORM="form";

	@Override
	public AttachmentKey<Map<String, Object>> getAttachmentKey(){
		return OpenApiHandler.DESERIALIZED_COOKIE_PARAMETERS;
	}

	@Override
	public StyleParameterDeserializer getStyleDeserializer(String style) {
		if (StringUtils.isNotBlank(style) && !FORM.equalsIgnoreCase(style)) {
			return null;
		}

		return new StyleParameterDeserializer() {
			@Override
			public boolean isApplicable(ValueType valueType, boolean exploade) {
				return !exploade || StyleParameterDeserializer.super.isApplicable(valueType, exploade);
			}

			@Override
			public Object deserialize(HttpServerExchange exchange, Parameter parameter, ValueType valueType,
					boolean exploade) {
				List<String> rawCookies = exchange.getRequestHeaders().get(Headers.COOKIE);


				Map<String, Cookie> cookies = CookieHelper.parseRequestCookies(
						exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_COOKIES, 200),
						exchange.getConnection().getUndertowOptions().get(UndertowOptions.ALLOW_EQUALS_IN_COOKIE_VALUE, false),
						rawCookies);


				Cookie cookie = cookies.get(parameter.getName());

				String value = cookie.getValue();

				if (ValueType.ARRAY == valueType) {
					List<String> valueList = new ArrayList<>();

					valueList.addAll(asList(value, Delimiters.COMMA));

					return valueList;
				}else if (ValueType.OBJECT == valueType) {
					Map<String, String> valueMap = new HashMap<>();
					valueMap.putAll(asMap(value, Delimiters.COMMA));

					return valueMap;
				}

				return null;
			}

		};
	}

}
