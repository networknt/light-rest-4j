/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package com.networknt.validator;

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.status.Status;
import com.networknt.swagger.*;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


/**
 * This is a swagger validator handler that validate request and response based on the spec. In
 * production only request validator should be turned on and response validator should only be
 * used during development.
 *
 * @author Steve Hu
 */
public class ValidatorHandler implements MiddlewareHandler {

    public static final String SWAGGER_CONFIG_NAME = "swagger-validator";
    public static final String CONFIG_NAME = "validator";

    static final String STATUS_MISSING_SWAGGER_OPERATION = "ERR10012";

    static final Logger logger = LoggerFactory.getLogger(ValidatorHandler.class);

    static ValidatorConfig config;
    static {
        config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(SWAGGER_CONFIG_NAME, ValidatorConfig.class);
        if(config == null) {
            config = (ValidatorConfig)Config.getInstance().getJsonObjectConfig(CONFIG_NAME, ValidatorConfig.class);
        }
    }

    private volatile HttpHandler next;

    RequestValidator requestValidator;

    public ValidatorHandler() {
        final SchemaValidator schemaValidator = new SchemaValidator(SwaggerHelper.swagger);
        this.requestValidator = new RequestValidator(schemaValidator);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI());
        SwaggerOperation swaggerOperation = null;
        Map<String, Object> auditInfo = exchange.getAttachment(AuditHandler.AUDIT_INFO);
        if(auditInfo != null) {
            swaggerOperation = (SwaggerOperation)auditInfo.get(Constants.SWAGGER_OPERATION_STRING);
        }
        if(swaggerOperation == null) {
            setExchangeStatus(exchange, STATUS_MISSING_SWAGGER_OPERATION);
            return;
        }

        Status status = requestValidator.validateRequest(requestPath, exchange, swaggerOperation);
        if(status != null) {
            exchange.setStatusCode(status.getStatusCode());
            status.setDescription(status.getDescription().replaceAll("\\\\", "\\\\\\\\"));
            exchange.getResponseSender().send(status.toString());
            if(config.isLogError()) logger.error("ValidationError:" + status.toString());
            return;
        }
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
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(ValidatorHandler.class.getName(), Config.getInstance().getJsonMapConfigNoCache(SWAGGER_CONFIG_NAME), null);
    }


}
