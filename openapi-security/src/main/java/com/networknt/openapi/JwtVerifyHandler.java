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
import com.networknt.security.*;
import com.networknt.utility.Constants;
import com.networknt.server.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.crypto.dsig.spec.ExcC14NParameterSpec;
import java.util.*;

/**
 * This is a middleware handler that handles security verification for light-rest-4j framework. It
 * verifies token signature and token expiration. And optional scope verification if it is enabled
 * in security.yml config file.
 *
 * @author Steve Hu
 */
public class JwtVerifyHandler extends AbstractJwtVerifyHandler {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifyHandler.class);
    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    String basePath;

    public JwtVerifyHandler() {
        // at this moment, we assume that the OpenApiHandler is fully loaded with a single spec or multiple specs.
        // And the basePath is the correct one from the OpenApiHandler helper or helperMap if multiple is used.
        config = SecurityConfig.load();
        jwtVerifier = new JwtVerifier(config);
        // in case that the specification doesn't exist, get the basePath from the handler.yml for endpoint lookup.
        HandlerConfig handlerConfig = HandlerConfig.load();
        this.basePath = handlerConfig == null ? "/" : handlerConfig.getBasePath();
    }

    @Override
    public boolean isSkipAuth(HttpServerExchange exchange) {
        String reqPath = exchange.getRequestPath();
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(logger.isTraceEnabled()) logger.trace("Skip auth base on skipPathPrefixes for {}", reqPath);
            return true;
        }
        // there is no other ways to skip the auth for openapi security handler.
        return false;
    }

    @Override
    public List<String> getSpecScopes(HttpServerExchange exchange, Map<String, Object> auditInfo) throws Exception {
        /* get openapi operation */
        OpenApiOperation openApiOperation = (OpenApiOperation) auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        Operation operation = this.getOperation(exchange, openApiOperation, auditInfo);
        if(operation == null) {
            if(config.isSkipVerifyScopeWithoutSpec()) {
                if (logger.isDebugEnabled()) logger.debug("SwtVerifyHandler.handleRequest ends without verifying scope due to spec.");
                Handler.next(exchange, next);
            }
            return null;
        }
        // get scope defined in OpenAPI spec for this endpoint.
        List<String> specScopes = null;
        Collection<SecurityRequirement> securityRequirements = operation.getSecurityRequirements();
        if (securityRequirements != null) {
            for (SecurityRequirement requirement : securityRequirements) {
                SecurityParameter securityParameter = null;

                for (String oauth2Name : OpenApiHandler.getHelper(exchange.getRequestPath()).oauth2Names) {
                    securityParameter = requirement.getRequirement(oauth2Name);
                    if (securityParameter != null) break;
                }

                if (securityParameter != null)
                    specScopes = securityParameter.getParameters();

                if (specScopes != null)
                    break;
            }
        }
        return specScopes;
    }


    /**
     * Gets the operation from the spec. If not defined or defined incorrectly, return null.
     *
     * @param exchange - the current exchange
     * @param openApiOperation - the openapi operation (from spec)
     * @param auditInfo A map of audit info properties
     * @return - return Operation
     */
    protected Operation getOperation(HttpServerExchange exchange, OpenApiOperation openApiOperation, Map<String, Object> auditInfo) {
        Operation operation;
        if (openApiOperation == null) {
            final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequestURI(), basePath);
            OpenApiHelper helper = OpenApiHandler.getHelper(exchange.getRequestPath());
            final Optional<NormalisedPath> maybeApiPath = helper == null ? Optional.empty() : helper.findMatchingApiPath(requestPath);

            if (maybeApiPath.isEmpty()) {
                if(!config.isSkipVerifyScopeWithoutSpec()) {
                    setExchangeStatus(exchange, STATUS_INVALID_REQUEST_PATH);
                }
                return null;
            }

            final NormalisedPath swaggerPathString = maybeApiPath.get();
            final Path swaggerPath = OpenApiHandler.getHelper(exchange.getRequestPath()).openApi3.getPath(swaggerPathString.original());
            final String httpMethod = exchange.getRequestMethod().toString().toLowerCase();

            operation = swaggerPath.getOperation(httpMethod);

            if (operation == null) {
                setExchangeStatus(exchange, STATUS_METHOD_NOT_ALLOWED, httpMethod, swaggerPathString.normalised());
                exchange.endExchange();
                return null;
            }

            openApiOperation = new OpenApiOperation(swaggerPathString, swaggerPath, httpMethod, operation);
            auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
            auditInfo.put(Constants.ENDPOINT_STRING, swaggerPathString.normalised() + "@" + httpMethod);

        } else {
            operation = openApiOperation.getOperation();
        }
        return operation;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnableVerifyJwt();
    }
}
