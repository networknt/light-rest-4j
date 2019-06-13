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

package com.networknt.security;

import com.networknt.audit.AuditHandler;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.exception.ExpiredTokenException;
import com.networknt.swagger.ApiNormalisedPath;
import com.networknt.swagger.NormalisedPath;
import com.networknt.swagger.SwaggerHelper;
import com.networknt.swagger.SwaggerOperation;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This is a middleware handler that handles security verification for light-rest-4j framework. It
 * verifies token signature and token expiration. And optional scope verification if it is enabled
 * in security.yml config file.
 *
 * @author Steve Hu
 */
public class JwtVerifyHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifyHandler.class);

    static final String SWAGGER_SECURITY_CONFIG = "swagger-security";
    static final String ENABLE_VERIFY_SCOPE = "enableVerifyScope";

    static final String STATUS_INVALID_AUTH_TOKEN = "ERR10000";
    static final String STATUS_AUTH_TOKEN_EXPIRED = "ERR10001";
    static final String STATUS_MISSING_AUTH_TOKEN = "ERR10002";
    static final String STATUS_INVALID_SCOPE_TOKEN = "ERR10003";
    static final String STATUS_SCOPE_TOKEN_EXPIRED = "ERR10004";
    static final String STATUS_AUTH_TOKEN_SCOPE_MISMATCH = "ERR10005";
    static final String STATUS_SCOPE_TOKEN_SCOPE_MISMATCH = "ERR10006";
    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    static Map<String, Object> config;
    static {
        // check if swagger-security.yml exist
        config = Config.getInstance().getJsonMapConfig(SWAGGER_SECURITY_CONFIG);
        // fallback to generic security.yml
        if(config == null) config = Config.getInstance().getJsonMapConfig(JwtHelper.SECURITY_CONFIG);
    }

    private volatile HttpHandler next;

    public JwtVerifyHandler() {}

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        HeaderMap headerMap = exchange.getRequestHeaders();
        String authorization = headerMap.getFirst(Headers.AUTHORIZATION);
        String jwt = JwtHelper.getJwtFromAuthorization(authorization);
        if(jwt != null) {
            try {
                JwtClaims claims = JwtHelper.verifyJwt(jwt, false);
                Map<String, Object> auditInfo = exchange.getAttachment(AuditHandler.AUDIT_INFO);
                // In normal case, the auditInfo shouldn't be null as it is created by SwaggerHandler with
                // endpoint and swaggerOperation available. This handler will enrich the auditInfo.
                if(auditInfo == null) {
                    auditInfo = new HashMap<>();
                    exchange.putAttachment(AuditHandler.AUDIT_INFO, auditInfo);
                }
                auditInfo.put(Constants.CLIENT_ID_STRING, claims.getStringClaimValue(Constants.CLIENT_ID_STRING));
                auditInfo.put(Constants.USER_ID_STRING, claims.getStringClaimValue(Constants.USER_ID_STRING));
                auditInfo.put(Constants.SUBJECT_CLAIMS, claims);
                if(config != null && (Boolean)config.get(ENABLE_VERIFY_SCOPE) && SwaggerHelper.swagger != null) {
                    Operation operation = null;
                    SwaggerOperation swaggerOperation = (SwaggerOperation)auditInfo.get(Constants.SWAGGER_OPERATION_STRING);
                    if(swaggerOperation == null) {
                        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI());
                        final Optional<NormalisedPath> maybeApiPath = SwaggerHelper.findMatchingApiPath(requestPath);
                        if (!maybeApiPath.isPresent()) {
                            setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH);
                            return;
                        }

                        final NormalisedPath swaggerPathString = maybeApiPath.get();
                        final Path swaggerPath = SwaggerHelper.swagger.getPath(swaggerPathString.original());

                        final HttpMethod httpMethod = HttpMethod.valueOf(exchange.getRequestMethod().toString());
                        operation = swaggerPath.getOperationMap().get(httpMethod);

                        if (operation == null) {
                            setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED);
                            return;
                        }
                        swaggerOperation = new SwaggerOperation(swaggerPathString, swaggerPath, httpMethod, operation);
                        auditInfo.put(Constants.SWAGGER_OPERATION_STRING, swaggerOperation);
                        auditInfo.put(Constants.ENDPOINT_STRING, swaggerPathString.normalised() + "@" + httpMethod);
                    } else {
                        operation = swaggerOperation.getOperation();
                    }

                    // is there a scope token
                    String scopeHeader = headerMap.getFirst(HttpStringConstants.SCOPE_TOKEN);
                    String scopeJwt = JwtHelper.getJwtFromAuthorization(scopeHeader);
                    List<String> secondaryScopes = null;
                    if(scopeJwt != null) {
                        try {
                            JwtClaims scopeClaims = JwtHelper.verifyJwt(scopeJwt, false);
                            secondaryScopes = scopeClaims.getStringListClaimValue("scope");
                            auditInfo.put(Constants.SCOPE_CLIENT_ID_STRING, scopeClaims.getStringClaimValue(Constants.CLIENT_ID_STRING));
                            auditInfo.put(Constants.ACCESS_CLAIMS, scopeClaims);
                        } catch (InvalidJwtException | MalformedClaimException e) {
                            logger.error("InvalidJwtException", e);
                            setExchangeStatus(exchange, STATUS_INVALID_SCOPE_TOKEN);
                            return;
                        } catch (ExpiredTokenException e) {
                            logger.error("ExpiredTokenException", e);
                            setExchangeStatus(exchange, STATUS_SCOPE_TOKEN_EXPIRED);
                            return;
                        }
                    }

                    // get scope defined in swagger spec for this endpoint.
                    List<String> specScopes = null;
                    List<Map<String, List<String>>> security = operation.getSecurity();
                    if(security != null) {
                        for(Map<String, List<String>> requirement: security) {
                            specScopes = requirement.get(SwaggerHelper.oauth2Name);
                            if(specScopes != null) break;
                        }
                    }

                    // validate scope
                    if (scopeHeader != null) {
                        if (secondaryScopes == null || !matchedScopes(secondaryScopes, specScopes)) {
                            setExchangeStatus(exchange, STATUS_SCOPE_TOKEN_SCOPE_MISMATCH, secondaryScopes, specScopes);
                            return;
                        }
                    } else {
                        // no scope token, verify scope from auth token.
                        List<String> primaryScopes;
                        try {
                            primaryScopes = claims.getStringListClaimValue("scope");
                        } catch (MalformedClaimException e) {
                            logger.error("MalformedClaimException", e);
                            setExchangeStatus(exchange, STATUS_INVALID_AUTH_TOKEN);
                            return;
                        }
                        if (!matchedScopes(primaryScopes, specScopes)) {
                            setExchangeStatus(exchange, STATUS_AUTH_TOKEN_SCOPE_MISMATCH, primaryScopes, specScopes);
                            return;
                        }
                    }
                }
                Handler.next(exchange, next);
            } catch (InvalidJwtException e) {
                // only log it and unauthorized is returned.
                logger.error("InvalidJwtException:", e);
                setExchangeStatus(exchange, STATUS_INVALID_AUTH_TOKEN);
            } catch (ExpiredTokenException e) {
                logger.error("ExpiredTokenException", e);
                setExchangeStatus(exchange, STATUS_AUTH_TOKEN_EXPIRED);
            }
        } else {
            setExchangeStatus(exchange, STATUS_MISSING_AUTH_TOKEN);
        }
    }

    protected boolean matchedScopes(List<String> jwtScopes, List<String> specScopes) {
        boolean matched = false;
        if(specScopes != null && specScopes.size() > 0) {
            if(jwtScopes != null && jwtScopes.size() > 0) {
                for(String scope: specScopes) {
                    if(jwtScopes.contains(scope)) {
                        matched = true;
                        break;
                    }
                }
            }
        } else {
            matched = true;
        }
        return matched;
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
        Object object = config.get(JwtHelper.ENABLE_VERIFY_JWT);
        return object != null && (Boolean) object;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(JwtVerifyHandler.class.getName(), config, null);
    }

}
