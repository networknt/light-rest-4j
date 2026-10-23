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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is the handler that parses the OpenApi object based on uri and method
 * of the request and attached the operation to the request so that security
 * and validator can use it without parsing it.
 * <p>
 * For subsequent handlers like the JwtVerifierHandler and ValidatorHandler,
 * they need the same basePath and OpenApiHelper for scope verification and
 * schema validation. Attach the routing generation to the exchange so a reload
 * cannot change the specification used by an in-flight request.
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

    private volatile String configName = OpenApiHandlerConfig.CONFIG_NAME;
    private record State(OpenApiHandlerConfig config, OpenApiRouting routing) {}
    private volatile State state;
    private record Published(String configName, State state) {}
    private record FailedReload(String configName, Object configMap, long retryAt) {}
    private static final ReentrantLock RELOAD_LOCK = new ReentrantLock();
    private static final long RELOAD_RETRY_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static volatile Published published;
    private static volatile FailedReload failedReload;
    private static volatile OpenApiRouting routing = new OpenApiRouting(null, Map.of());
    public static final AttachmentKey<OpenApiRouting> ROUTING = AttachmentKey.create(OpenApiRouting.class);


    /**
     * @deprecated Best-effort legacy read mirror, not atomic with helper/helperMap or
     * routingSnapshot(). Use one routingSnapshot() read for a consistent generation.
     * Direct writes do not update routing.
     */
    @Deprecated
    public static volatile Map<String, OpenApiHelper> helperMap;

    /**
     * @deprecated Best-effort legacy read mirror, not atomic with helper/helperMap or
     * routingSnapshot(). Use one routingSnapshot() read for a consistent generation.
     * Direct writes do not update routing.
     */
    @Deprecated
    public static volatile OpenApiHelper helper;

    private volatile HttpHandler next;

    public OpenApiHandler(String configName) {
        if(logger.isInfoEnabled()) logger.info("OpenApiHandler is constructed with configName {}.", configName);
        this.configName = configName;
        initialize();
    }

    public OpenApiHandler() {
        if(logger.isInfoEnabled()) logger.info("OpenApiHandler is constructed.");
        initialize();
    }

    private void initialize() {
        RELOAD_LOCK.lock();
        try {
            initialize(OpenApiHandlerConfig.load(configName));
        } finally {
            RELOAD_LOCK.unlock();
        }
    }

    private void initialize(OpenApiHandlerConfig config) {
        RELOAD_LOCK.lock();
        try {
            State loaded = loadState(config);
            publishRouting(loaded.routing());
            state = loaded;
            published = new Published(configName, loaded);
            failedReload = null;
        } finally {
            RELOAD_LOCK.unlock();
        }
    }

    private State loadState(OpenApiHandlerConfig config) {
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig(SPEC_INJECT);
        OpenApiRouting loaded;
        if (config.isMultipleSpec()) {
            // multiple specifications in the same handler.
            Map<String, Object> pathSpecMapping = config.getPathSpecMapping();
            Map<String, OpenApiHelper> loadedHelpers = new HashMap<>();
            // add adm helper to the helperMap
            if(inject != null) {
                try {
                    OpenApiHelper h = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(inject));
                    loadedHelpers.put("/adm", h);
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
                    String prefix = OpenApiRouting.normalizePrefix(entry.getKey());
                    if (loadedHelpers.putIfAbsent(prefix, h) != null) {
                        throw new IllegalArgumentException("Duplicate OpenAPI routing prefix: " + prefix);
                    }
                } catch (Exception e) {
                    logger.error("merge specification failed for {}", entry.getValue(), e);
                    throw new RuntimeException("merge specification failed for " + entry.getValue(), e);
                }
            }
            loaded = new OpenApiRouting(null, loadedHelpers);
            if(logger.isTraceEnabled()) logger.trace("multiple specifications loaded.");
        } else {
            try {
                Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
                handlerConfig = HandlerConfig.load();

                this.validateSpec(openapi, inject, "openapi.yaml");

                openapi = OpenApiHelper.merge(openapi, inject);

                OpenApiHelper loadedHelper = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));

                // overwrite the helper.basePath if it cannot be derived from the openapi.yaml from the handler.yml
                // if the basePath is not null. The default value if openapi.yml doesn't have basePath is "".
                if (loadedHelper.basePath.isEmpty() && handlerConfig != null && handlerConfig.getBasePath() != null) {
                    loadedHelper.setBasePath(handlerConfig.getBasePath());
                }
                loaded = new OpenApiRouting(loadedHelper, null);
            } catch (Exception e) {
                logger.error("merge specification failed", e);
                throw new RuntimeException("merge specification failed", e);
            }
            if(logger.isTraceEnabled()) logger.trace("single specification loaded.");
        }
        return new State(config, loaded);
    }

    public static OpenApiRouting routingSnapshot() { return routing; }

    static void publishRouting(OpenApiRouting snapshot) {
        RELOAD_LOCK.lock();
        try {
            // Legacy fields are intentionally best-effort; only routing is authoritative.
            helper = snapshot.single();
            helperMap = snapshot.single() == null ? snapshot.helpers() : null;
            routing = snapshot;
            published = null;
            failedReload = null;
        } finally {
            RELOAD_LOCK.unlock();
        }
    }

    private boolean retryDeferred(Object configMap) {
        FailedReload failure = failedReload;
        return failure != null && failure.configName().equals(configName)
                && failure.configMap() == configMap && System.nanoTime() - failure.retryAt() < 0;
    }

    private void deferReload(OpenApiHandlerConfig candidate, RuntimeException failure) {
        failedReload = new FailedReload(configName, candidate == null ? null : candidate.getMappedConfig(),
                System.nanoTime() + RELOAD_RETRY_NANOS);
        logger.error("OpenAPI reload failed; serving the last good routing generation. Retry deferred for 1 second.", failure);
    }

    private void reloadIfNeeded() {
        OpenApiHandlerConfig candidate;
        try {
            if (retryDeferred(null)) return;
            candidate = OpenApiHandlerConfig.load(configName);
        } catch (RuntimeException e) {
            deferReload(null, e);
            return;
        }
        if (candidate.getMappedConfig() == state.config().getMappedConfig()
                || retryDeferred(candidate.getMappedConfig()) || !RELOAD_LOCK.tryLock()) return;
        try {
            // Global publication has a global writer lock. Re-read configuration
            // under it and adopt a generation another instance already loaded.
            candidate = null;
            candidate = OpenApiHandlerConfig.load(configName);
            if (candidate.getMappedConfig() == state.config().getMappedConfig()
                    || retryDeferred(candidate.getMappedConfig())) return;
            Published latest = published;
            if (latest != null && latest.configName().equals(configName)
                    && latest.state().config().getMappedConfig() == candidate.getMappedConfig()) {
                state = latest.state();
            } else {
                initialize(candidate);
                logger.info("OpenApiHandler is reloaded.");
            }
        } catch (RuntimeException e) {
            // Record failure before releasing the writer lock, so another waiting
            // instance cannot immediately retry the same failed specification.
            deferReload(candidate, e);
        } finally {
            RELOAD_LOCK.unlock();
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        reloadIfNeeded();
        State current = state;
        OpenApiHandlerConfig config = current.config();
        OpenApiRouting snapshot = current.routing();
        exchange.putAttachment(ROUTING, snapshot);
        if (logger.isDebugEnabled())
            logger.debug("OpenApiHandler.handleRequest starts.");

        boolean multiple = config.isMultipleSpec();
        OpenApiHelper selected = snapshot.helperFor(exchange.getRequestPath());
        if (selected == null) {
            if (!config.isIgnoreInvalidPath()) {
                this.setExchangeFailed(exchange, STATUS_INVALID_REQUEST_PATH, exchange.getRequestPath());
                return;
            }
        } else {
            final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), selected.basePath);
            final Optional<NormalisedPath> maybeApiPath = selected.findMatchingApiPath(requestPath);
            if (maybeApiPath.isEmpty()) {
                if (!multiple && config.isIgnoreInvalidPath()) {
                    Handler.next(exchange, next);
                } else {
                    this.setExchangeFailed(exchange, STATUS_INVALID_REQUEST_PATH, requestPath.normalised());
                }
                return;
            }
            final NormalisedPath openApiPathString = maybeApiPath.get();
            final Path path = selected.openApi3.getPath(openApiPathString.original());
            final String httpMethod = exchange.getRequestMethod().toString().toLowerCase(Locale.ROOT);
            final Operation operation = path.getOperation(httpMethod);
            if (operation == null) {
                this.setExchangeFailed(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
                return;
            }
            final OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);
            try {
                ParameterDeserializer.deserialize(exchange, openApiOperation);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
            Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO) == null
                    ? new HashMap<>() : exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
            auditInfo.put(Constants.ENDPOINT_STRING, endpoint(selected, openApiPathString, httpMethod));
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
        State current = state;
        return current.config().isMultipleSpec() ? !current.config().getMappedConfig().isEmpty()
                : current.routing().single() != null && current.routing().single().openApi3 != null;
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

    /** Build the same canonical endpoint for metadata and security fallback paths. */
    public static String endpoint(OpenApiHelper selected, NormalisedPath path, String method) {
        String prefix = selected.basePath == null ? "" : selected.basePath;
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        return prefix + path.normalised() + "@" + method.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Legacy longest-prefix utility, retaining original keys and trailing-slash
     * alias tie-breaking. Runtime routing validates and sorts its own prefixes once.
     */
    public static String findMatchingPrefix(String requestPath, Set<String> prefixes) {
        if (requestPath == null) return null;
        String best = null;
        int longest = -1;
        for (String key : prefixes) {
            int end = key.length();
            while (end > 0 && key.charAt(end - 1) == '/') end--;
            if ((requestPath.length() == end || (requestPath.length() > end
                    && requestPath.charAt(end) == '/')) && requestPath.regionMatches(0, key, 0, end)
                    && (end > longest || (end == longest && (best == null || key.compareTo(best) < 0)))) {
                best = key;
                longest = end;
            }
        }
        return best;
    }

    public static String getBasePath(String requestPath) {
        return routing.basePathFor(requestPath);
    }

    /** Use the generation that resolved this request, including during reload. */
    public static OpenApiHelper getHelper(HttpServerExchange exchange) {
        OpenApiRouting snapshot = exchange.getAttachment(ROUTING);
        return (snapshot == null ? routing : snapshot).helperFor(exchange.getRequestPath());
    }

    public static OpenApiHelper getHelper(String requestPath) {
        return routing.helperFor(requestPath);
    }
}
