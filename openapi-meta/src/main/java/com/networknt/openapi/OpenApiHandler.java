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
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This is the handler that parses the OpenApi object based on uri and method
 * of the request and attached the operation to the request so that security
 * and validator can use it without parsing it.
 * <p>
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
        if(logger.isInfoEnabled()) logger.info("OpenApiHandler is constructed with cfg.");
        config = cfg;
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig(SPEC_INJECT);

        if (config.isMultipleSpec()) {

            // multiple specifications in the same handler.
            Map<String, Object> pathSpecMapping = config.getPathSpecMapping();
            helperMap = new HashMap<>();
            // add adm helper to the helperMap
            if(inject != null) {
                try {
                    OpenApiHelper h = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(inject));
                    helperMap.put("/adm", h);
                } catch (JsonProcessingException e) {
                    logger.error("parse inject failed for adm");
                    throw new RuntimeException("parse inject failed for adm");
                }
            }
            // iterate the mapping to load the specifications.
            for (Map.Entry<String, Object> entry : pathSpecMapping.entrySet()) {

                if (logger.isTraceEnabled())
                    logger.trace("key = {} value = {}", entry.getKey(), entry.getValue());

                try {
                    Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache((String) entry.getValue());
                    this.validateSpec(openapi, inject, entry.getKey());
                    OpenApiHelper h = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));
                    helperMap.put(entry.getKey(), h);
                } catch (Exception e) {
                    logger.error("merge specification failed for {}", entry.getValue(), e);
                    throw new RuntimeException("merge specification failed for " + entry.getValue());
                }
            }
            if(logger.isTraceEnabled()) logger.trace("multiple specifications loaded.");
        } else {
            try {
                Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
                handlerConfig = HandlerConfig.load();

                this.validateSpec(openapi, inject, "openapi.yaml");

                openapi = OpenApiHelper.merge(openapi, inject);

                helper = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));

                // overwrite the helper.basePath if it cannot be derived from the openapi.yaml from the handler.yml
                // if the basePath is not null. The default value if openapi.yml doesn't have basePath is "".
                if (helper.basePath.isEmpty() && handlerConfig != null && handlerConfig.getBasePath() != null) {
                    helper.setBasePath(handlerConfig.getBasePath());
                }
            } catch (Exception e) {
                logger.error("merge specification failed", e);
                throw new RuntimeException("merge specification failed", e);
            }
            if(logger.isTraceEnabled()) logger.trace("single specification loaded.");
        }
    }

    public OpenApiHandler() {
        this(OpenApiHandlerConfig.load());
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (logger.isDebugEnabled())
            logger.debug("OpenApiHandler.handleRequest starts.");

        if (config.isMultipleSpec()) {
            String p = exchange.getRequestPath();
            boolean found = false;
            for (Map.Entry<String, OpenApiHelper> entry : helperMap.entrySet()) {

                if (p.startsWith(entry.getKey())) {
                    found = true;
                    OpenApiHelper h = entry.getValue();

                    // found the match base path here.
                    final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), h.basePath);

                    final Optional<NormalisedPath> maybeApiPath = h.findMatchingApiPath(requestPath);

                    if (maybeApiPath.isEmpty()) {
                        this.setExchangeFailed(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
                        return;
                    }

                    final NormalisedPath openApiPathString = maybeApiPath.get();
                    final Path path = h.openApi3.getPath(openApiPathString.original());

                    final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
                    final Operation operation = path.getOperation(httpMethod);

                    if (operation == null) {
                        this.setExchangeFailed(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
                        return;
                    }

                    // This handler can identify the openApiOperation and endpoint only. Other info will be added by JwtVerifyHandler.
                    final OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);

                    try {
                        ParameterDeserializer.deserialize(exchange, openApiOperation);
                    } catch (Throwable t) {// do not crash the handler
                        logger.error(t.getMessage(), t);
                    }
                    String endpoint = null;
                    if(h.basePath != null && !h.basePath.isEmpty()) {
                        endpoint = h.basePath + openApiPathString.normalised() + "@" + httpMethod.toLowerCase();
                    } else {
                        endpoint = openApiPathString.normalised() + "@" + httpMethod.toLowerCase();
                    }
                    Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO) == null
                            ? new HashMap<>()
                            : exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
                    auditInfo.put(Constants.ENDPOINT_STRING, endpoint);
                    auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
                    exchange.putAttachment(AttachmentConstants.AUDIT_INFO, auditInfo);
                    break;
                }
            }
            if (!found && !config.isIgnoreInvalidPath()) {
                this.setExchangeFailed(exchange, STATUS_INVALID_REQUEST_PATH, p);
                return;
            }
        } else {
            final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), helper.basePath);

            final Optional<NormalisedPath> maybeApiPath = helper.findMatchingApiPath(requestPath);

            if (maybeApiPath.isEmpty()) {
                if (config.isIgnoreInvalidPath()) {
                    if (logger.isDebugEnabled())
                        logger.debug("OpenApiHandler.handleRequest ends with ignoreInvalidPath.");
                    Handler.next(exchange, next);
                } else {
                    this.setExchangeFailed(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
                }
                return;
            }

            final NormalisedPath openApiPathString = maybeApiPath.get();
            final Path path = helper.openApi3.getPath(openApiPathString.original());

            final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
            final Operation operation = path.getOperation(httpMethod);

            if (operation == null) {
                this.setExchangeFailed(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
                return;
            }

            // This handler can identify the openApiOperation and endpoint only. Other info will be added by JwtVerifyHandler.
            final OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);

            try {
                ParameterDeserializer.deserialize(exchange, openApiOperation);
            } catch (Throwable t) {// do not crash the handler
                logger.error(t.getMessage(), t);
            }

            String endpoint = null;
            if(helper.basePath != null && !helper.basePath.isEmpty()) {
                endpoint = helper.basePath + openApiPathString.normalised() + "@" + httpMethod.toLowerCase();
            } else {
                endpoint = openApiPathString.normalised() + "@" + httpMethod.toLowerCase();
            }
            Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO) == null
                    ? new HashMap<>()
                    : exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
            auditInfo.put(Constants.ENDPOINT_STRING, endpoint);
            auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
            exchange.putAttachment(AttachmentConstants.AUDIT_INFO, auditInfo);
        }

        if (logger.isDebugEnabled())
            logger.debug("OpenApiHandler.handleRequest ends.");

        Handler.next(exchange, next);
    }

    /**
     * Validates the injectMap and openapiMap.Throws an exception if not valid.
     *
     * @param openapiMap - openapiSpec
     * @param openapiInjectMap - inject map
     * @param specName - name of the openapiSpec
     */
    private void validateSpec(Map<String, Object> openapiMap, Map<String, Object> openapiInjectMap, String specName) {
        InjectableSpecValidator validator = SingletonServiceFactory.getBean(InjectableSpecValidator.class);
        if (validator == null) {
            validator = new DefaultInjectableSpecValidator();
        }

        if (!validator.isValid(openapiMap, openapiInjectMap)) {
            logger.error("the original spec {} and injected spec has error, please check the validator {}", specName, validator.getClass().getName());
            throw new RuntimeException("inject spec error for " + specName);
        }
    }

    /**
     * Sets the exchange status for the current exchange + trace logging.
     *
     * @param exchange - current exchange
     * @param err - the err/status code.
     * @param args - args for return statement.
     */
    private void setExchangeFailed(HttpServerExchange exchange, String err, Object... args) {
        setExchangeStatus(exchange, err, args);
        if (logger.isDebugEnabled())
            logger.debug("OpenApiHandler.handleRequest ends with an error.");
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
        if (config.multipleSpec) {
            enabled = config.getMappedConfig().size() > 0;
        } else {
            enabled = helper.openApi3 != null;
        }
        return enabled;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(OpenApiHandlerConfig.CONFIG_NAME, OpenApiHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(OpenApiHandlerConfig.CONFIG_NAME), null);
    }

    @Override
    public void reload() {
        config.reload();
        ModuleRegistry.registerModule(OpenApiHandlerConfig.CONFIG_NAME, OpenApiHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(OpenApiHandlerConfig.CONFIG_NAME), null);
    }

    /**
     * merge two maps. The values in preferredMap take priority.
     *
     * @param preferredMap   preferred map
     * @param alternativeMap alternative map
     * @return Map result map
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected static Map<String, ?> mergeMaps(Map preferredMap, Map alternativeMap) {
        Map mergedMap = new HashMap<>();

        if (null != alternativeMap)
            mergedMap.putAll(alternativeMap);

        if (null != preferredMap)
            mergedMap.putAll(preferredMap);

        return Collections.unmodifiableMap(mergedMap);
    }

    protected static Map<String, Object> nonNullMap(Map<String, Object> map) {
        return null == map ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    public static Map<String, ?> getQueryParameters(final HttpServerExchange exchange) {
        return getQueryParameters(exchange, false);
    }

    public static Map<String, ?> getQueryParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly) {
        Map<String, Object> deserializedQueryParamters = exchange.getAttachment(DESERIALIZED_QUERY_PARAMETERS);

        return deserializedValueOnly ? nonNullMap(deserializedQueryParamters)
                : mergeMaps(deserializedQueryParamters, exchange.getQueryParameters());
    }

    public static Map<String, ?> getPathParameters(final HttpServerExchange exchange) {
        return getPathParameters(exchange, false);
    }

    public static Map<String, ?> getPathParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly) {
        Map<String, Object> deserializedPathParameters = exchange.getAttachment(DESERIALIZED_PATH_PARAMETERS);

        return deserializedValueOnly ? nonNullMap(deserializedPathParameters)
                : mergeMaps(deserializedPathParameters, exchange.getPathParameters());
    }

    public static Map<String, ?> getHeaderParameters(final HttpServerExchange exchange) {
        return getHeaderParameters(exchange, false);
    }

    public static Map<String, ?> getHeaderParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly) {
        Map<String, Object> deserializedHeaderParameters = exchange.getAttachment(DESERIALIZED_HEADER_PARAMETERS);

        if (!deserializedValueOnly) {
            HeaderMap headers = exchange.getRequestHeaders();

            if (null == headers) {
                return Collections.emptyMap();
            }

            Map<String, HeaderValues> headerMap = new HashMap<>();

            for (HttpString headerName : headers.getHeaderNames()) {
                headerMap.put(headerName.toString(), headers.get(headerName));
            }

            return mergeMaps(deserializedHeaderParameters, headerMap);
        }

        return nonNullMap(deserializedHeaderParameters);
    }

    public static Map<String, ?> getCookieParameters(final HttpServerExchange exchange) {
        return getCookieParameters(exchange, false);
    }

    public static Map<String, ?> getCookieParameters(final HttpServerExchange exchange, final boolean deserializedValueOnly) {
        Map<String, Object> deserializedCookieParamters = exchange.getAttachment(DESERIALIZED_COOKIE_PARAMETERS);
        Map<String, Cookie> cookieMap = new HashMap<>();
        exchange.requestCookies().forEach(s -> cookieMap.put(s.getName(), s));
        return deserializedValueOnly ? nonNullMap(deserializedCookieParamters)
                : mergeMaps(deserializedCookieParamters, cookieMap);
    }

    // this is used to get the basePath from the OpenApiHandler regardless single specification or multiple specifications.
    public static String getBasePath(String requestPath) {
        String basePath = "";

        // check single first.
        if (OpenApiHandler.helper != null) {
            basePath = OpenApiHandler.helper.basePath;

            if (logger.isTraceEnabled())
                logger.trace("Found basePath for single spec from OpenApiHandler helper: {}", basePath);

        } else {

            // based on the requestPath to find the right helper in the helperMap.
            for (Map.Entry<String, OpenApiHelper> entry : OpenApiHandler.helperMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    basePath = entry.getKey();
                    if (logger.isTraceEnabled())
                        logger.trace("Found basePath for multiple specs from OpenApiHandler helper HashMap: {}", basePath);
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
        if (OpenApiHandler.helper != null) {
            helper = OpenApiHandler.helper;
        } else {

            // based on the requestPath to find the right helper in the helperMap.
            for (Map.Entry<String, OpenApiHelper> entry : OpenApiHandler.helperMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    helper = entry.getValue();
                    break;
                }
            }
        }
        return helper;
    }

}
