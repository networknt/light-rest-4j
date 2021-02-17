/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Path;
import com.networknt.openapi.parameter.ParameterDeserializer;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This is the handler that parses the OpenApi object based on uri and method
 * of the request and attached the operation to the request so that security
 * and validator can use it without parsing it.
 *
 * @author Steve Hu
 */
public class OpenApiHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(OpenApiHandler.class);

    public static final String CONFIG_NAME = "openapi";
    public static final String SPEC_INJECT = "openapi-inject";

    public static final AttachmentKey<Map<String, Object>> DESERIALIZED_QUERY_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_PATH_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_HEADER_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_COOKIE_PARAMETERS = AttachmentKey.create(Map.class);

    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    private volatile HttpHandler next;
    public OpenApiHandler() {
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig(SPEC_INJECT);
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig(CONFIG_NAME);
        InjectableSpecValidator validator = SingletonServiceFactory.getBean(InjectableSpecValidator.class);
        if (validator == null) {
            validator = new DefaultInjectableSpecValidator();
        }
        if (!validator.isValid(openapi, inject)) {
            logger.error("the original spec and injected spec has error, please check the validator {}", validator.getClass().getName());
            throw new RuntimeException("inject spec error");
        }
        OpenApiHelper.merge(openapi, inject);
        try {
            OpenApiHelper.init(Config.getInstance().getMapper().writeValueAsString(openapi));
        } catch (JsonProcessingException e) {
            logger.error("merge specification failed");
            throw new RuntimeException("merge specification failed");
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI());
        final Optional<NormalisedPath> maybeApiPath = OpenApiHelper.getInstance().findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
            return;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = OpenApiHelper.openApi3.getPath(openApiPathString.original());

        final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
        final Operation operation = path.getOperation(httpMethod);

        if (operation == null) {
            setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED);
            return;
        }

        // This handler can identify the openApiOperation and endpoint only. Other info will be added by JwtVerifyHandler.
        final OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);
        
        try {
        	ParameterDeserializer.deserialize(exchange, openApiOperation);
        }catch (Throwable t) {// do not crash the handler
        	logger.error(t.getMessage(), t);
        }
        
        String endpoint = openApiPathString.normalised() + "@" + httpMethod.toString().toLowerCase();
        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO) == null
                ? new HashMap<>()
                : exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        auditInfo.put(Constants.ENDPOINT_STRING, endpoint);
        auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
        exchange.putAttachment(AttachmentConstants.AUDIT_INFO, auditInfo);

        Handler.next(exchange, next);
    }

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    @Override
    public boolean isEnabled() {
        // just check if swagger.json exists or not.
        return (OpenApiHelper.openApi3 != null);
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(OpenApiHandler.class.getName(), Config.getInstance().getJsonMapConfig(CONFIG_NAME), null);
    }
    
    /**
     * merge two maps. The values in preferredMap take priority.
     * 
     * @param preferredMap preferred map
     * @param alternativeMap alternative map
     * @return Map result map
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	protected static Map<String, ?> mergeMaps(Map preferredMap, Map alternativeMap){
    	Map mergedMap = new HashMap<>();
    	
    	if (null!=alternativeMap)
    		mergedMap.putAll(alternativeMap);
    	
    	if (null!=preferredMap)
    		mergedMap.putAll(preferredMap);
    	
    	return Collections.unmodifiableMap(mergedMap);
    }
    
    protected static Map<String, Object> nonNullMap(Map<String, Object> map){
    	return null==map?Collections.emptyMap():Collections.unmodifiableMap(map);
    }
    
    public static Map<String, ?> getQueryParameters(final HttpServerExchange exchange){
    	return getQueryParameters(exchange, false);
    }
    
    public static Map<String, ?> getQueryParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly){
    	Map<String, Object> deserializedQueryParamters = exchange.getAttachment(DESERIALIZED_QUERY_PARAMETERS);
    	
    	return deserializedValueOnly?nonNullMap(deserializedQueryParamters)
    			:mergeMaps(deserializedQueryParamters, exchange.getQueryParameters());
    }
    
    public static Map<String, ?> getPathParameters(final HttpServerExchange exchange){
    	return getPathParameters(exchange, false);
    }
    
    public static Map<String, ?> getPathParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly){
    	Map<String, Object> deserializedPathParamters = exchange.getAttachment(DESERIALIZED_PATH_PARAMETERS);
    	
    	return deserializedValueOnly?nonNullMap(deserializedPathParamters)
    			:mergeMaps(deserializedPathParamters, exchange.getPathParameters());
    }
    
    public static Map<String, ?> getHeaderParameters(final HttpServerExchange exchange){
    	return getHeaderParameters(exchange, false);
    }
    
    public static Map<String, ?> getHeaderParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly){
    	Map<String, Object> deserializedHeaderParamters = exchange.getAttachment(DESERIALIZED_HEADER_PARAMETERS);
    	
    	if (!deserializedValueOnly) {
    		HeaderMap headers = exchange.getRequestHeaders();
    		
    		if (null==headers) {
    			return Collections.emptyMap();
    		}
    		
    		Map<String, HeaderValues> headerMap = new HashMap<>();
    		
    		for (HttpString headerName: headers.getHeaderNames()) {
    			headerMap.put(headerName.toString(), headers.get(headerName));
    		}
    		
    		return mergeMaps(deserializedHeaderParamters, headerMap);
    	}
    	
    	return nonNullMap(deserializedHeaderParamters);
    }
    
    public static Map<String, ?> getCookieParameters(final HttpServerExchange exchange){
    	return getCookieParameters(exchange, false);
    }  
    
    public static Map<String, ?> getCookieParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly){
    	Map<String, Object> deserializedCookieParamters = exchange.getAttachment(DESERIALIZED_COOKIE_PARAMETERS);
    	
    	return deserializedValueOnly?nonNullMap(deserializedCookieParamters)
    			:mergeMaps(deserializedCookieParamters, exchange.getRequestCookies());
    } 
}
