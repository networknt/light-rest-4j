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
import com.networknt.handler.config.HandlerConfig;
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
 * For subsequent handlers like the JwtVerifierHandler and ValidatorHandler,
 * they need to get the basePath and the OpenApiHelper for scope verification
 * and schema validation. Put the helperMap to the exchange for easy sharing.
 *
 * @author Steve Hu
 */
public class OpenApiHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(OpenApiHandler.class);

    public static final String CONFIG_NAME = "openapi";
    public static final String SPEC_INJECT = "openapi-inject";
    public static final String HANDLER_CONFIG = "handler";

    public static final AttachmentKey<Map<String, Object>> DESERIALIZED_QUERY_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_PATH_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_HEADER_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_COOKIE_PARAMETERS = AttachmentKey.create(Map.class);

    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";
    HandlerConfig handlerConfig;

    static OpenApiHandlerConfig config;
    // for multiple specifications use case. The key is the basePath and the value is the instance of OpenApiHelper.
    public static Map<String, OpenApiHelper> helperMap;
    // for single specification case which covers 99 percent use cases. This is why, we don't put it into the helperMap for
    // better performance. The subsequent handlers will only need to check the OpenApiHandler config instead of iterate a map.
    public static OpenApiHelper helper;

    private volatile HttpHandler next;
    public OpenApiHandler(OpenApiHandlerConfig cfg) {
        config = cfg;
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig(SPEC_INJECT);
        if(config.isMultipleSpec()) {
            // multiple specifications in the same handler.
            Map<String, Object> pathSpecMapping = config.getPathSpecMapping();
            helperMap = new HashMap<>();
            // iterate the mapping to load the specifications.
            for(Map.Entry<String, Object> entry: pathSpecMapping.entrySet()) {
                if(logger.isTraceEnabled()) logger.trace("key = " + entry.getKey() + " value = " + entry.getValue());
                Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache((String)entry.getValue());
                InjectableSpecValidator validator = SingletonServiceFactory.getBean(InjectableSpecValidator.class);
                if (validator == null) {
                    validator = new DefaultInjectableSpecValidator();
                }
                if (!validator.isValid(openapi, inject)) {
                    logger.error("the original spec {} and injected spec has error, please check the validator {}", entry.getValue(), validator.getClass().getName());
                    throw new RuntimeException("inject spec error for " + entry.getValue());
                }
                OpenApiHelper.merge(openapi, inject);
                try {
                    OpenApiHelper h = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));
                    helperMap.put(entry.getKey(), h);
                } catch (JsonProcessingException e) {
                    logger.error("merge specification failed for " + entry.getValue());
                    throw new RuntimeException("merge specification failed for " + entry.getValue());
                }
            }
        } else {
            Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
            handlerConfig = (HandlerConfig)Config.getInstance().getJsonObjectConfig(HANDLER_CONFIG, HandlerConfig.class);
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
                helper = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));
                // overwrite the helper.basePath it cannot be derived from the openapi.yaml from the handler.yml
                if(helper.basePath == null && handlerConfig != null) {
                    helper.setBasePath(handlerConfig.getBasePath());
                }
            } catch (JsonProcessingException e) {
                logger.error("merge specification failed");
                throw new RuntimeException("merge specification failed");
            }
        }
    }

    public OpenApiHandler() {
        this(OpenApiHandlerConfig.load());
    }
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest starts.");
        if(config.isMultipleSpec()) {
            String p = exchange.getRequestPath();
            boolean found = false;
            for(Map.Entry<String, OpenApiHelper> entry: helperMap.entrySet()) {
                if(p.startsWith(entry.getKey())) {
                    found = true;
                    OpenApiHelper h = entry.getValue();
                    // found the match base path here.
                    final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), h.basePath);
                    final Optional<NormalisedPath> maybeApiPath = h.findMatchingApiPath(requestPath);
                    if (!maybeApiPath.isPresent()) {
                        setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
                        if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends with an error.");
                        return;
                    }

                    final NormalisedPath openApiPathString = maybeApiPath.get();
                    final Path path = h.openApi3.getPath(openApiPathString.original());

                    final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
                    final Operation operation = path.getOperation(httpMethod);

                    if (operation == null) {
                        setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
                        if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends with an error.");
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
                    break;
                }
            }
            if(!found) {
                setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH, p);
                if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends with an error.");
                return;
            }
        } else {
            final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), helper.basePath);
            final Optional<NormalisedPath> maybeApiPath = helper.findMatchingApiPath(requestPath);
            if (!maybeApiPath.isPresent()) {
                setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
                if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends with an error.");
                return;
            }

            final NormalisedPath openApiPathString = maybeApiPath.get();
            final Path path = helper.openApi3.getPath(openApiPathString.original());

            final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
            final Operation operation = path.getOperation(httpMethod);

            if (operation == null) {
                setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
                if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends with an error.");
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
        }
        if (logger.isDebugEnabled()) logger.debug("OpenApiHandler.handleRequest ends.");
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
        boolean enabled = false;
        if(config.multipleSpec) {
            enabled = config.getMappedConfig().size() > 0;
        } else {
            enabled = helper.openApi3 != null;
        }
        return enabled;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(OpenApiHandler.class.getName(), Config.getInstance().getJsonMapConfig(CONFIG_NAME), null);
    }

    @Override
    public void reload() {
        handlerConfig = (HandlerConfig)Config.getInstance().getJsonObjectConfig(HANDLER_CONFIG, HandlerConfig.class);
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

    // this is used to get the basePath from the OpenApiHandler regardless single specification or multiple specifications.
    public static String getBasePath(String requestPath) {
        String basePath = "";
        // check single first.
        if(OpenApiHandler.helper != null) {
            basePath = OpenApiHandler.helper.basePath;
            if(logger.isTraceEnabled()) logger.trace("Got basePath for single spec from helper " + basePath);
        } else {
            // based on the requestPath to find the right helper in the helperMap.
            for(Map.Entry<String, OpenApiHelper> entry: OpenApiHandler.helperMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    basePath = entry.getKey();
                    if(logger.isTraceEnabled()) logger.trace("Got basePath for multiple specs from helperMap " + basePath);
                    break;
                }
            }
        }
        return basePath;
    }

    // this is used to get the helper instance matches to the request path from the OpenApiHandler regardless single specification or multiple specifications.
    public static OpenApiHelper getHelper(String requestPath) {
        OpenApiHelper helper = null;
        // check single first.
        if(OpenApiHandler.helper != null) {
            helper = OpenApiHandler.helper;
        } else {
            // based on the requestPath to find the right helper in the helperMap.
            for(Map.Entry<String, OpenApiHelper> entry: OpenApiHandler.helperMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    helper = entry.getValue();
                    break;
                }
            }
        }
        return helper;
    }

}
