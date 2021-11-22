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
import com.networknt.config.JsonMapper;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.impl.OperationImpl;
import com.networknt.rule.RuleConstants;
import com.networknt.rule.RuleEngine;
import com.networknt.rule.RuleLoaderStartupHook;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;

/**
 * This is a business middleware handler that should be put after the technical middleware handlers in the request/response chain. It
 * handles fine-grained authorization on the business domain. In the request chain, it will check a list of conditions (role-based and
 * attributed-based authorization) at the endpoint level. In the response chain, a list of conditions will be checked to filter the
 * response to remove some rows and/or some columns.
 *
 * This handler is depending on the yaml-rule from networknt and all rules for the service will be loaded during the startup time with
 * a startup hook and this handler will use the cached rules and data to do local calculation for the best performance.
 *
 * @author Steve Hu
 */
public class AccessControlHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(AccessControlHandler.class);
    static final AccessControlConfig config = (AccessControlConfig) Config.getInstance().getJsonObjectConfig(AccessControlConfig.CONFIG_NAME, AccessControlConfig.class);
    static final String ACCESS_CONTROL_ERROR = "ERR10067";
    private volatile HttpHandler next;
    private RuleEngine engine;

    public AccessControlHandler() {
        if (logger.isInfoEnabled()) logger.info("AccessControlHandler is loaded.");
        engine = new RuleEngine(RuleLoaderStartupHook.rules, null);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        OpenApiOperation operation = (OpenApiOperation) auditInfo.get("openapi_operation");
        Operation op = operation.getOperation();
        Map<String, Object> ra = (Map)op.getExtension("x-request-access");
        String ruleId = (String)ra.get("rule");
        String roles = (String)ra.get("roles");
        Map<String, Object> objMap = new HashMap<>();
        objMap.put("auditInfo", auditInfo);
        objMap.put("roles", roles);
        Map<String, Object> result = engine.executeRule(ruleId, objMap);
        if((Boolean)result.get(RuleConstants.RESULT)) {
            next(exchange);
        } else {
            logger.error(JsonMapper.toJson(result));
            setExchangeStatus(exchange, ACCESS_CONTROL_ERROR, ruleId);
        }
    }

    protected void next(HttpServerExchange exchange) throws Exception {
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
        ModuleRegistry.registerModule(AccessControlHandler.class.getName(), Config.getInstance().getJsonMapConfigNoCache(AccessControlConfig.CONFIG_NAME), null);
    }
}
