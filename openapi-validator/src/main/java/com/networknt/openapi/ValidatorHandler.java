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

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.dump.StoreResponseStreamSinkConduit;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.security.JwtVerifier;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final String OPENAPI_YML_CONFIG = "openapi.yml";
    public static final String OPENAPI_YAML_CONFIG = "openapi.yaml";
    public static final String OPENAPI_JSON_CONFIG = "openapi.json";
    public static final String HANDLER_CONFIG_NAME = "handler";

    static final String STATUS_MISSING_OPENAPI_OPERATION = "ERR10012";

    static final Logger logger = LoggerFactory.getLogger(ValidatorHandler.class);

    static ValidatorConfig config;
    static {
        config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(OPENAPI_CONFIG_NAME, ValidatorConfig.class);
        if(config == null) {
            config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(CONFIG_NAME, ValidatorConfig.class);
        }
    }

    private volatile HttpHandler next;

    RequestValidator requestValidator;

    ResponseValidator responseValidator;

    String basePath;

    public ValidatorHandler() {
        if(OpenApiHelper.getInstance() == null) {
            String spec = Config.getInstance().getStringFromFile(OPENAPI_YML_CONFIG);
            if(spec == null) {
                spec = Config.getInstance().getStringFromFile(OPENAPI_YAML_CONFIG);
                if(spec == null) {
                    spec = Config.getInstance().getStringFromFile(OPENAPI_JSON_CONFIG);
                }
            }
            OpenApiHelper.init(spec);
        }
        final SchemaValidator schemaValidator = new SchemaValidator(OpenApiHelper.openApi3);
        this.requestValidator = new RequestValidator(schemaValidator);
        this.responseValidator = new ResponseValidator();
        HandlerConfig handlerConfig = (HandlerConfig)Config.getInstance().getJsonObjectConfig(HANDLER_CONFIG_NAME, HandlerConfig.class);
        // if PathHandlerProvider is used, the chain is defined in the service.yml and no handler.yml available.
        basePath = handlerConfig == null ? null : handlerConfig.getBasePath();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), basePath);
        OpenApiOperation openApiOperation = null;
        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        if(auditInfo != null) {
            openApiOperation = (OpenApiOperation)auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        }
        if(openApiOperation == null) {
            setExchangeStatus(exchange, STATUS_MISSING_OPENAPI_OPERATION);
            return;
        }
        Status status = requestValidator.validateRequest(requestPath, exchange, openApiOperation);
        if(status != null) {
            setExchangeStatus(exchange, status);
            if(config.logError) {
                logger.error("There is an Validation Error:");
            }
            return;
        }

        if(config.validateResponse) {
            validateResponse(exchange, openApiOperation);
        }
        Handler.next(exchange, next);
    }

    private void validateResponse(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
        exchange.addResponseWrapper((factory, exchange12) -> new StoreResponseStreamSinkConduit(factory.create(), exchange12));

        exchange.addExchangeCompleteListener((exchange1, nextListener) ->{
            Status status = responseValidator.validateResponse(exchange, openApiOperation);
            if(status != null) {
                logger.error("Response validation error: {} \n with response body: {}", status.getDescription(), new String(exchange.getAttachment(StoreResponseStreamSinkConduit.RESPONSE)));
            }
            nextListener.proceed();
        });
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
        ModuleRegistry.registerModule(ValidatorHandler.class.getName(), Config.getInstance().getJsonMapConfigNoCache(OPENAPI_CONFIG_NAME), null);
    }

    @Override
    public void reload() {
        config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(OPENAPI_CONFIG_NAME, ValidatorConfig.class);
        if(config == null) {
            config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(CONFIG_NAME, ValidatorConfig.class);
        }
    }
}
