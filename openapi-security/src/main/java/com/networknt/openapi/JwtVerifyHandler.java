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
import com.networknt.exception.ExpiredTokenException;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.SecurityParameter;
import com.networknt.oas.model.SecurityRequirement;
import com.networknt.security.IJwtVerifyHandler;
import com.networknt.security.JwtVerifier;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
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

import java.util.*;

/**
 * This is a middleware handler that handles security verification for light-rest-4j framework. It
 * verifies token signature and token expiration. And optional scope verification if it is enabled
 * in security.yml config file.
 *
 * @author Steve Hu
 */
public class JwtVerifyHandler implements MiddlewareHandler, IJwtVerifyHandler {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifyHandler.class);
    static final String OPENAPI_YML_CONFIG = "openapi.yml";
    static final String OPENAPI_YAML_CONFIG = "openapi.yaml";
    static final String OPENAPI_JSON_CONFIG = "openapi.json";

    static final String HANDLER_CONFIG = "handler";
    static final String OPENAPI_SECURITY_CONFIG = "openapi-security";
    static final String ENABLE_VERIFY_SCOPE = "enableVerifyScope";
    static final String ENABLE_VERIFY_JWT_SCOPE_TOKEN = "enableExtractScopeToken";
    static final String IGNORE_JWT_EXPIRY = "ignoreJwtExpiry";

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
    String basePath;
    // make this static variable public so that it can be accessed from the server-info module
    public static JwtVerifier jwtVerifier;
    static {
        // check if openapi-security.yml exist
        config = Config.getInstance().getJsonMapConfig(OPENAPI_SECURITY_CONFIG);
        // fallback to generic security.yml
        if(config == null) config = Config.getInstance().getJsonMapConfig(JwtVerifier.SECURITY_CONFIG);

    }

    private volatile HttpHandler next;

    public JwtVerifyHandler() {
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
        jwtVerifier = new JwtVerifier(config);
        HandlerConfig handlerConfig = (HandlerConfig)Config.getInstance().getJsonObjectConfig(HANDLER_CONFIG, HandlerConfig.class);
        // if PathHandlerProvider is used, the chain is defined in the service.yml and no handler.yml available.
        basePath = handlerConfig == null ? null : handlerConfig.getBasePath();
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        HeaderMap headerMap = exchange.getRequestHeaders();
        String authorization = headerMap.getFirst(Headers.AUTHORIZATION);
        String jwt = jwtVerifier.getJwtFromAuthorization(authorization);
        boolean ignoreExpiry = config.get(IGNORE_JWT_EXPIRY) == null ? false : (boolean)config.get(IGNORE_JWT_EXPIRY);
        if(jwt != null) {
            try {
                JwtClaims claims = jwtVerifier.verifyJwt(jwt, ignoreExpiry, true);
                Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
                // In normal case, the auditInfo shouldn't be null as it is created by OpenApiHandler with
                // endpoint and swaggerOperation available. This handler will enrich the auditInfo.
                if(auditInfo == null) {
                    auditInfo = new HashMap<>();
                    exchange.putAttachment(AttachmentConstants.AUDIT_INFO, auditInfo);
                }
                String clientId = claims.getStringClaimValue(Constants.CLIENT_ID_STRING);
                // try to get the cid as some OAuth tokens name it as cid like Okta.
                if(clientId == null) clientId = claims.getStringClaimValue(Constants.CID_STRING);
                auditInfo.put(Constants.CLIENT_ID_STRING, clientId);
                String userId = claims.getStringClaimValue(Constants.USER_ID_STRING);
                // try to get the uid as some OAuth tokens name it as uid like Okta.
                if(userId == null) userId = claims.getStringClaimValue(Constants.UID_STRING);
                auditInfo.put(Constants.USER_ID_STRING, userId);
                auditInfo.put(Constants.SUBJECT_CLAIMS, claims);
                String callerId = headerMap.getFirst(HttpStringConstants.CALLER_ID);
                if(callerId != null) auditInfo.put(Constants.CALLER_ID_STRING, callerId);
                if(config != null && (Boolean)config.get(ENABLE_VERIFY_JWT_SCOPE_TOKEN) && OpenApiHelper.openApi3 != null) {
                    Operation operation = null;
                    OpenApiOperation openApiOperation = (OpenApiOperation)auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
                    if(openApiOperation == null) {
                        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), basePath);
                        final Optional<NormalisedPath> maybeApiPath = OpenApiHelper.getInstance().findMatchingApiPath(requestPath);
                        if (!maybeApiPath.isPresent()) {
                            setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH);
                            return;
                        }

                        final NormalisedPath swaggerPathString = maybeApiPath.get();
                        final Path swaggerPath = OpenApiHelper.openApi3.getPath(swaggerPathString.original());

                        final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();
                        operation = swaggerPath.getOperation(httpMethod);

                        if (operation == null) {
                            setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, swaggerPathString.normalised());
                            return;
                        }
                        openApiOperation = new OpenApiOperation(swaggerPathString, swaggerPath, httpMethod, operation);
                        auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
                        auditInfo.put(Constants.ENDPOINT_STRING, swaggerPathString.normalised() + "@" + httpMethod);
                    } else {
                        operation = openApiOperation.getOperation();
                    }

                    // is there a scope token
                    String scopeHeader = headerMap.getFirst(HttpStringConstants.SCOPE_TOKEN);
                    String scopeJwt = jwtVerifier.getJwtFromAuthorization(scopeHeader);
                    List<String> secondaryScopes = null;
                    if(scopeJwt != null) {
                        try {
                            JwtClaims scopeClaims = jwtVerifier.verifyJwt(scopeJwt, ignoreExpiry, true);
                            Object scopeClaim = scopeClaims.getClaimValue(Constants.SCOPE_STRING);
                            if(scopeClaim instanceof String) {
                                secondaryScopes = Arrays.asList(scopeClaims.getStringClaimValue(Constants.SCOPE_STRING).split(" "));
                            } else if(scopeClaim instanceof List) {
                                secondaryScopes = scopeClaims.getStringListClaimValue(Constants.SCOPE_STRING);
                            }
                            if(secondaryScopes == null || secondaryScopes.isEmpty()) {
                                // some IDPs like Okta and Microsoft call scope claim "scp" instead of "scope"
                                Object scpClaim = scopeClaims.getClaimValue(Constants.SCP_STRING);
                                if(scpClaim instanceof String) {
                                    secondaryScopes = Arrays.asList(scopeClaims.getStringClaimValue(Constants.SCP_STRING).split(" "));
                                } else if(scpClaim instanceof List) {
                                    secondaryScopes = scopeClaims.getStringListClaimValue(Constants.SCP_STRING);
                                }
                            }
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

                    // validate the scope against the scopes configured in the OpenAPI spec
                    if((Boolean)config.get(ENABLE_VERIFY_SCOPE)) {
	                    // get scope defined in OpenAPI spec for this endpoint.
	                    Collection<String> specScopes = null;
	                    Collection<SecurityRequirement> securityRequirements = operation.getSecurityRequirements();
	                    if(securityRequirements != null) {
	                        for(SecurityRequirement requirement: securityRequirements) {
                                SecurityParameter securityParameter = null;
	                            for(String oauth2Name: OpenApiHelper.oauth2Names) {
                                    securityParameter = requirement.getRequirement(oauth2Name);
                                    if(securityParameter != null) break;
                                }
	                            if(securityParameter != null) specScopes = securityParameter.getParameters();
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
	                        List<String> primaryScopes = null;
	                        try {
	                            Object scopeClaim = claims.getClaimValue(Constants.SCOPE_STRING);
	                            if(scopeClaim instanceof String) {
	                                primaryScopes = Arrays.asList(claims.getStringClaimValue(Constants.SCOPE_STRING).split(" "));
	                            } else if(scopeClaim instanceof List) {
	                                primaryScopes = claims.getStringListClaimValue(Constants.SCOPE_STRING);
	                            }
	                            if(primaryScopes == null || primaryScopes.isEmpty()) {
	                                // some IDPs like Okta and Microsoft call scope claim "scp" instead of "scope"
	                                Object scpClaim = claims.getClaimValue(Constants.SCP_STRING);
	                                if(scpClaim instanceof String) {
	                                    primaryScopes = Arrays.asList(claims.getStringClaimValue(Constants.SCP_STRING).split(" "));
	                                } else if(scpClaim instanceof List) {
	                                    primaryScopes = claims.getStringListClaimValue(Constants.SCP_STRING);
	                                }
	                            }
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
                    } // end scope validation
                }
                Handler.next(exchange, next);
            } catch (InvalidJwtException e) {
                // only log it and unauthorized is returned.
                logger.error("InvalidJwtException: ", e);
                setExchangeStatus(exchange, STATUS_INVALID_AUTH_TOKEN);
            } catch (ExpiredTokenException e) {
                logger.error("ExpiredTokenException", e);
                setExchangeStatus(exchange, STATUS_AUTH_TOKEN_EXPIRED);
            }
        } else {
            setExchangeStatus(exchange, STATUS_MISSING_AUTH_TOKEN);
        }
    }

    protected boolean matchedScopes(List<String> jwtScopes, Collection<String> specScopes) {
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
        Object object = config.get(JwtVerifier.ENABLE_VERIFY_JWT);
        return object != null && Boolean.valueOf(object.toString()) ;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(JwtVerifyHandler.class.getName(), config, null);
        ModuleRegistry.registerModule(JwtVerifyHandler.class.getName(), Config.getInstance().getJsonMapConfigNoCache(OPENAPI_SECURITY_CONFIG), null);
    }

    @Override
    public JwtVerifier getJwtVerifier() {
        return jwtVerifier;
    }
}
