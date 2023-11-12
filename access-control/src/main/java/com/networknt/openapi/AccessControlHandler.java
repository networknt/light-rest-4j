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

import com.networknt.body.BodyHandler;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.http.HttpMethod;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.rule.RuleConstants;
import com.networknt.rule.RuleEngine;
import com.networknt.rule.RuleLoaderStartupHook;
import com.networknt.utility.ModuleRegistry;
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
    static AccessControlConfig config;
    static final String ACCESS_CONTROL_ERROR = "ERR10067";
    static final String ACCESS_CONTROL_MISSING = "ERR10069";
    static final String STARTUP_HOOK_NOT_LOADED = "ERR11019";
    static final String REQUEST_ACCESS = "request-access";
    static final String RESPONSE_FILTER = "response-filter";
    static final String RULE_ID = "ruleId";
    private volatile HttpHandler next;
    private final RuleEngine engine;

    public AccessControlHandler() {
        config = AccessControlConfig.load();
        engine = new RuleEngine(RuleLoaderStartupHook.rules, null);
        if (logger.isInfoEnabled())
            logger.info("AccessControlHandler is loaded.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest starts.");
        String reqPath = exchange.getRequestPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the security check.
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(s -> reqPath.startsWith(s))) {
            if(logger.isTraceEnabled()) logger.trace("Skip request path base on skipPathPrefixes for " + reqPath);
            if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends with path skipped.");
            Handler.next(exchange, next);
            return;
        }

        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);

        // This map is the input for the rule. For different access control rules, the input might be different.
        // so we just put as many objects into the map in case the rule needs it for the access control calculation.
        Map<String, Object> ruleEnginePayload = new HashMap<>();

        // need to get the rule/rules to execute from the RuleLoaderStartupHook. First, get the endpoint.
        String endpoint = (String) auditInfo.get("endpoint");
        this.populateRuleEnginePayload(exchange, auditInfo, ruleEnginePayload);

        // checked the RuleLoaderStartupHook to ensure it is loaded. If not, return an error to the caller.
        if (RuleLoaderStartupHook.endpointRules == null) {
            logger.error("RuleLoaderStartupHook endpointRules is null");
            setExchangeStatus(exchange, STARTUP_HOOK_NOT_LOADED, "RuleLoaderStartupHook");
            if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends with an error.");
            return;
        }

        // get the access rules (maybe multiple) based on the endpoint.
        Map<String, List<Map<String, Object>>> requestRules = (Map<String, List<Map<String, Object>>>) RuleLoaderStartupHook.endpointRules.get(endpoint);

        // if there is no access rule for this endpoint, check the default deny flag in the config.
        if (requestRules == null) {
            if (config.defaultDeny) {
                logger.error("Access control rule is missing and default deny is true for endpoint " + endpoint);
                if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends with an error.");
                setExchangeStatus(exchange, ACCESS_CONTROL_MISSING, endpoint);
            } else {
                if (logger.isDebugEnabled()) logger.debug("AccessControlHandler.handleRequest ends.");
                next(exchange);
            }
        } else {
            this.executeRules(exchange, ruleEnginePayload, requestRules);
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
     * Execute all the rules defined.
     *
     * @param exchange - current exchange
     * @param ruleEnginePayload - rulePayload
     * @param requestRules - rule(s) defined for the endpoint
     * @throws Exception - Rule engine exception
     */
    protected void executeRules(HttpServerExchange exchange, Map<String, Object> ruleEnginePayload, Map<String, List<Map<String, Object>>> requestRules) throws Exception {
        boolean finalResult = true;
        List<Map<String, Object>> accessRules = requestRules.get(REQUEST_ACCESS);
        Map result = null;
        String ruleId = null;

        // iterate the rules and execute them in sequence. Allow access only when all rules return true.
        for (Map<String, Object> ruleMap : accessRules) {
            ruleId = (String) ruleMap.get(RULE_ID);
            ruleEnginePayload.putAll(ruleMap);
            result = engine.executeRule(ruleId, ruleEnginePayload);
            boolean res = (Boolean) result.get(RuleConstants.RESULT);
            if (!res) {
                finalResult = false;
                break;
            }
        }
        if (finalResult) {
            this.next(exchange);
        } else {
            logger.error(JsonMapper.toJson(result));
            setExchangeStatus(exchange, ACCESS_CONTROL_ERROR, ruleId);
        }
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
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(AccessControlConfig.CONFIG_NAME, AccessControlHandler.class.getName(), config.getMappedConfig(), null);
    }

    @Override
    public void reload() {
        config.reload();
        ModuleRegistry.registerModule(AccessControlConfig.CONFIG_NAME, AccessControlHandler.class.getName(), config.getMappedConfig(), null);
        if (logger.isInfoEnabled()) logger.info("AccessControlHandler is reloaded.");
    }
}
