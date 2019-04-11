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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Path;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

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
    
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_QUERY_PARAMETERS = AttachmentKey.create(Map.class);
	public static final AttachmentKey<Map<String, Object>> DESERIALIZED_PATH_PARAMETERS = AttachmentKey.create(Map.class);

    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    private volatile HttpHandler next;

    public OpenApiHandler() {

    }


    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI());
        final Optional<NormalisedPath> maybeApiPath = OpenApiHelper.findMatchingApiPath(requestPath);
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
        String endpoint = openApiPathString.normalised() + "@" + httpMethod.toString().toLowerCase();
        Map<String, Object> auditInfo = new HashMap<>();
        auditInfo.put(Constants.ENDPOINT_STRING, endpoint);
        auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
        exchange.putAttachment(AuditHandler.AUDIT_INFO, auditInfo);

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
}
