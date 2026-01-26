package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.SecurityParameter;
import com.networknt.oas.model.SecurityRequirement;
import com.networknt.security.AbstractSwtVerifyHandler;
import com.networknt.security.SwtVerifier;
import com.networknt.security.SecurityConfig;
import com.networknt.utility.Constants;
import com.networknt.server.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * This is a middleware handler used to verify simple web token with token introspection on
 * the OAuth 2.0 provider. It does the scope verification against the openapi.yml file, and
 * that is the reason it is located in the light-rest-4j.
 *
 * @author Steve Hu
 */
public class SwtVerifyHandler extends AbstractSwtVerifyHandler {
    static final Logger logger = LoggerFactory.getLogger(SwtVerifyHandler.class);
    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";

    String basePath;

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
        return config.isEnableVerifySwt();
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

    public SwtVerifyHandler() {
        // at this moment, we assume that the OpenApiHandler is fully loaded with a single spec or multiple specs.
        // And the basePath is the correct one from the OpenApiHandler helper or helperMap if multiple is used.
        config = SecurityConfig.load();
        swtVerifier = new SwtVerifier(config);
        // in case that the specification doesn't exist, get the basePath from the handler.yml for endpoint lookup.
        HandlerConfig handlerConfig = HandlerConfig.load();
        this.basePath = handlerConfig == null ? "/" : handlerConfig.getBasePath();
    }

}
