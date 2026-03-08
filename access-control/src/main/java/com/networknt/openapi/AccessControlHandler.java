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

import com.networknt.access.AccessControlConfig;
import com.networknt.config.JsonMapper;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.http.HttpMethod;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.rule.RuleConstants;
import com.networknt.rule.RuleExecutor;
import com.networknt.service.SingletonServiceFactory;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;

/**
 * This is a business middleware handler that should be put after the technical middleware handlers in the request/response chain. It handles
 * fine-grained authorization on the business domain. In the request chain, it will check a list of conditions (rule-based authorization) at
 * the endpoint level. In the response chain, a list of rules/conditions will be checked to filter the response to remove some rows and/or
 * some columns.
 * <p>
 * This handler is depending on the yaml-rule from light-4j of networknt and all rules for the service will be loaded during the startup time
 * with a startup hook and this handler will use the cached rules and data to do local calculation for the best performance.
 *
 * @author Steve Hu
 */
public class AccessControlHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(AccessControlHandler.class);
    static final String ACCESS_CONTROL_ERROR = "ERR10067";
    static final String ACCESS_CONTROL_MISSING = "ERR10069";
    static final String STARTUP_HOOK_NOT_LOADED = "ERR11019";
    static final String REQUEST_ACCESS = "req-acc";
    static final String PERMISSION = "permission";
    static final String RULE_ID = "ruleId";
    private volatile HttpHandler next;

    public AccessControlHandler() {
        AccessControlConfig.load();
        if (logger.isInfoEnabled())
            logger.info("AccessControlHandler is loaded.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        AccessControlConfig config = AccessControlConfig.load();
        if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest starts.");
        String reqPath = exchange.getRequestPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the security check.
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(logger.isTraceEnabled()) logger.trace("Skip request path base on skipPathPrefixes for {}", reqPath);
            if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends with path skipped.");
            Handler.next(exchange, next);
            return;
        }

        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);

        // This map is the input for the rule. For different req-acc access control rules, the input might be different.
        // so we just put as many objects into the map in case the rule needs it for the access control calculation.
        Map<String, Object> ruleEnginePayload = new HashMap<>();

        // get the endpoint to look up and execute the corresponding access control rules via RuleExecutor.
        String endpoint = (String) auditInfo.get("endpoint");
        this.populateRuleEnginePayload(exchange, auditInfo, ruleEnginePayload);

        // execute rules using the executor
        RuleExecutor ruleExecutor = SingletonServiceFactory.getBean(RuleExecutor.class);
        if (ruleExecutor == null) {
            logger.error("RuleExecutor startup hook is not loaded. Cannot perform access control for endpoint {}", endpoint);
            setExchangeStatus(exchange, STARTUP_HOOK_NOT_LOADED, RuleExecutor.class.getName());
            return;
        }
        Map<String, Object> result = ruleExecutor.executeRules(endpoint, REQUEST_ACCESS, ruleEnginePayload);
        if (result == null) {
            if (config.isDefaultDeny()) {
                logger.error("Access control rule is missing and default deny is true for endpoint {}", endpoint);
                setExchangeStatus(exchange, ACCESS_CONTROL_MISSING, endpoint);
            } else {
                if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends.");
                next(exchange);
            }
        } else {
            boolean res = (Boolean)result.getOrDefault(RuleConstants.RESULT, false);
            if (res) {
                this.next(exchange);
            } else {
                logger.error(JsonMapper.toJson(result));
                setExchangeStatus(exchange, ACCESS_CONTROL_ERROR, endpoint);
            }
        }
    }

    /**
     * Populate the rule engine payload with basic request information.
     *
     * @param exchange - current exchange.
     * @param auditInfo - audit info.
     * @param ruleEnginePayload - hashmap for all basic request info.
     */
    protected void populateRuleEnginePayload(HttpServerExchange exchange, Map<String, Object> auditInfo, Map<String, Object> ruleEnginePayload) {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        HttpMethod httpMethod = HttpMethod.resolve(exchange.getRequestMethod().toString());
        Map<String, Deque<String>> queryParameters = exchange.getQueryParameters();
        Map<String, Deque<String>> pathParameters = exchange.getPathParameters();

        ruleEnginePayload.put("auditInfo", auditInfo); // Jwt info is in this object.
        ruleEnginePayload.put("headers", requestHeaders);
        ruleEnginePayload.put("queryParameters", queryParameters);
        ruleEnginePayload.put("pathParameters", pathParameters);
        ruleEnginePayload.put("method", httpMethod.toString());

        this.addBodyData(exchange, httpMethod, ruleEnginePayload);
    }



    /**
     * Grabs body data from request (if it has one defined),
     * and pushes it into the rule engine payload.
     *
     * @param exchange - current exchange
     * @param httpMethod - httpMethod for request
     * @param ruleEnginePayload - the payload rule engine requires
     */
    @SuppressWarnings("unchecked")
    protected void addBodyData(HttpServerExchange exchange, HttpMethod httpMethod, Map<String, Object> ruleEnginePayload) {
        if (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH) {
            Map<String, Object> bodyMap = (Map<String, Object>) exchange.getAttachment(AttachmentConstants.REQUEST_BODY);
            if(bodyMap != null) {
                ruleEnginePayload.put("requestBody", bodyMap);
            } else if (logger.isTraceEnabled()) {
                logger.trace("Could not get body from body handler");
            }

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
        return AccessControlConfig.load().isEnabled();
    }
}
