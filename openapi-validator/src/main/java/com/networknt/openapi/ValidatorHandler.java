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

import com.networknt.dump.StoreResponseStreamSinkConduit;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * This is an OpenAPI validator handler that validate request based on the specification. There
 * is only request validator handler on the server side and response validation should be done
 * on the client side only.
 *
 * @author Steve Hu
 */
public class ValidatorHandler implements MiddlewareHandler {
    public static final String OPENAPI_CONFIG_NAME = "openapi-validator";
    public static final String CONFIG_NAME = "validator";

    static final String STATUS_MISSING_OPENAPI_OPERATION = "ERR10012";

    static final Logger logger = LoggerFactory.getLogger(ValidatorHandler.class);

    static volatile ValidatorConfig config;

    private volatile HttpHandler next;
    private String configName = OPENAPI_CONFIG_NAME;
    private record Validators(ValidatorConfig config, OpenApiRouting routing,
                              Map<String, RequestValidator> requests, Map<String, ResponseValidator> responses) {
        RequestValidator requestFor(String path) {
            String key = keyFor(path);
            return key == null ? null : requests.get(key);
        }
        ResponseValidator responseFor(String path) {
            String key = keyFor(path);
            return key == null ? null : responses.get(key);
        }
        private String keyFor(String path) {
            return routing.single() == null ? routing.matchingPrefix(path) : "";
        }
    }
    private volatile Validators validators;

    public ValidatorHandler() {
        ValidatorConfig loaded = ValidatorConfig.load(configName);
        if(loaded.getMappedConfig() == null || loaded.getMappedConfig().isEmpty()) {
            configName = CONFIG_NAME;
            loaded = ValidatorConfig.load(configName);
        }
        config = loaded; // Legacy package-level read mirror; runtime state is per instance.
        validators = initialize(loaded, OpenApiHandler.routingSnapshot());
    }

    private Validators initialize(ValidatorConfig config, OpenApiRouting routing) {
        Map<String, RequestValidator> requests = new HashMap<>();
        Map<String, ResponseValidator> responses = new HashMap<>();
        for (Map.Entry<String, OpenApiHelper> entry : routing.helpers().entrySet()) {
            SchemaValidator schema = new SchemaValidator(entry.getValue().openApi3, config.isLegacyPathType());
            requests.put(entry.getKey(), new RequestValidator(schema, config));
            responses.put(entry.getKey(), new ResponseValidator(schema, config, routing));
        }
        return new Validators(config, routing, Map.copyOf(requests), Map.copyOf(responses));
    }

    private Validators validatorsFor(OpenApiRouting routing) {
        ValidatorConfig loaded = ValidatorConfig.load(configName);
        Validators current = validators;
        if (current.config() == loaded && current.routing() == routing) return current;
        synchronized (this) {
            current = validators;
            if (current.config() != loaded || current.routing() != routing) {
                current = initialize(loaded, routing);
                validators = current;
                config = loaded;
            }
        }
        return current;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        OpenApiRouting routing = exchange.getAttachment(OpenApiHandler.ROUTING);
        if (routing == null) routing = OpenApiHandler.routingSnapshot();
        Validators selected = validatorsFor(routing);
        ValidatorConfig config = selected.config();
        if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest starts.");
        String reqPath = exchange.getRequestPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the validation.
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(s -> reqPath.startsWith(s))) {
            if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends with skipped path " + reqPath);
            Handler.next(exchange, next);
            return;
        }

        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), routing.basePathFor(exchange.getRequestPath()));
        OpenApiOperation openApiOperation = null;
        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        if(auditInfo != null) {
            openApiOperation = (OpenApiOperation)auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        }
        if(openApiOperation == null) {
            if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends with an error.");
            setExchangeStatus(exchange, STATUS_MISSING_OPENAPI_OPERATION);
            return;
        }
        RequestValidator reqV = selected.requestFor(exchange.getRequestPath());
        if (reqV == null) {
            setExchangeStatus(exchange, STATUS_MISSING_OPENAPI_OPERATION);
            return;
        }
        Status status = reqV.validateRequest(requestPath, exchange, openApiOperation);
        if(status != null) {
            if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends with an error.");
            setExchangeStatus(exchange, status);
            if(config.isLogError()) {
                logger.error("There is an Validation Error:");
            }
            return;
        }

        if(config.isValidateResponse()) {
            ResponseValidator resV = selected.responseFor(exchange.getRequestPath());
            if (resV == null) {
                setExchangeStatus(exchange, STATUS_MISSING_OPENAPI_OPERATION);
                return;
            }
            validateResponse(exchange, openApiOperation, resV);
        }
        if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends.");
        Handler.next(exchange, next);
    }

    private void validateResponse(HttpServerExchange exchange, OpenApiOperation openApiOperation, ResponseValidator resV) {
        exchange.addResponseWrapper((factory, exchange12) -> new StoreResponseStreamSinkConduit(factory.create(), exchange12));

        exchange.addExchangeCompleteListener((exchange1, nextListener) ->{
            Status status = resV.validateResponse(exchange, openApiOperation);
            if(status != null) {
                logger.error("Response validation error: {} \n with response body: {}", status.getDescription(), new String(exchange.getAttachment(StoreResponseStreamSinkConduit.RESPONSE)));
            }
            nextListener.proceed();
        });
    }

    RequestValidator getRequestValidator(String requestPath) {
        return validatorsFor(OpenApiHandler.routingSnapshot()).requestFor(requestPath);
    }

    ResponseValidator getResponseValidator(String requestPath) {
        return validatorsFor(OpenApiHandler.routingSnapshot()).responseFor(requestPath);
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
        return ValidatorConfig.load(configName).isEnabled();
    }

}
