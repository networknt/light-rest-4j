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

import com.networknt.config.Config;
import com.networknt.dump.StoreResponseStreamSinkConduit;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.jose4j.http.Response;
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

    static ValidatorConfig config;

    private volatile HttpHandler next;
    // keep the single requestValidator instance as it covers 99 percent of use cases for best performance.
    RequestValidator requestValidator;
    Map<String, RequestValidator> requestValidatorMap;

    ResponseValidator responseValidator;
    Map<String, ResponseValidator> responseValidatorMap;

    public ValidatorHandler() {
        config = ValidatorConfig.load(OPENAPI_CONFIG_NAME);
        if(config == null) {
            config = ValidatorConfig.load(CONFIG_NAME);
        }

        if(OpenApiHandler.helper != null) {
            final SchemaValidator schemaValidator = new SchemaValidator(OpenApiHandler.helper.openApi3);
            this.requestValidator = new RequestValidator(schemaValidator);
            this.responseValidator = new ResponseValidator(schemaValidator);
        } else {
            requestValidatorMap = new HashMap<>();
            responseValidatorMap = new HashMap<>();
            for(Map.Entry<String, OpenApiHelper> entry: OpenApiHandler.helperMap.entrySet()) {
                final SchemaValidator schemaValidator = new SchemaValidator(entry.getValue().openApi3);
                RequestValidator reqV = new RequestValidator(schemaValidator);
                requestValidatorMap.put(entry.getKey(), reqV);
                ResponseValidator resV = new ResponseValidator(schemaValidator);
                responseValidatorMap.put(entry.getKey(), resV);
            }
        }
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest starts.");
        String reqPath = exchange.getRequestPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the validation.
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(s -> reqPath.startsWith(s))) {
            if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends with skipped path " + reqPath);
            Handler.next(exchange, next);
            return;
        }

        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), OpenApiHandler.getBasePath(exchange.getRequestPath()));
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
        RequestValidator reqV = getRequestValidator(exchange.getRequestPath());
        Status status = reqV.validateRequest(requestPath, exchange, openApiOperation);
        if(status != null) {
            if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends with an error.");
            setExchangeStatus(exchange, status);
            if(config.logError) {
                logger.error("There is an Validation Error:");
            }
            return;
        }

        if(config.validateResponse) {
            validateResponse(exchange, openApiOperation);
        }
        if (logger.isDebugEnabled()) logger.debug("ValidatorHandler.handleRequest ends.");
        Handler.next(exchange, next);
    }

    private void validateResponse(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
        exchange.addResponseWrapper((factory, exchange12) -> new StoreResponseStreamSinkConduit(factory.create(), exchange12));

        exchange.addExchangeCompleteListener((exchange1, nextListener) ->{
            ResponseValidator resV = getResponseValidator(exchange.getRequestPath());
            Status status = resV.validateResponse(exchange, openApiOperation);
            if(status != null) {
                logger.error("Response validation error: {} \n with response body: {}", status.getDescription(), new String(exchange.getAttachment(StoreResponseStreamSinkConduit.RESPONSE)));
            }
            nextListener.proceed();
        });
    }

    private RequestValidator getRequestValidator(String requestPath) {
        RequestValidator validator = null;
        if(this.requestValidator != null) {
            validator = this.requestValidator;
        } else {
            for(Map.Entry<String, RequestValidator> entry: requestValidatorMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    validator = entry.getValue();
                    break;
                }
            }
        }
        return validator;
    }

    private ResponseValidator getResponseValidator(String requestPath) {
        ResponseValidator validator = null;
        if(this.responseValidator != null) {
            validator = this.responseValidator;
        } else {
            for(Map.Entry<String, ResponseValidator> entry: responseValidatorMap.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    validator = entry.getValue();
                    break;
                }
            }
        }
        return validator;
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
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(OPENAPI_CONFIG_NAME, ValidatorHandler.class.getName(), config.getMappedConfig(), null);
    }

    @Override
    public void reload() {
        config.reload(OPENAPI_CONFIG_NAME);
        if(config == null) {
            config.reload(CONFIG_NAME);
        }
        ModuleRegistry.registerModule(OPENAPI_CONFIG_NAME, ValidatorHandler.class.getName(), config.getMappedConfig(), null);
    }
}
