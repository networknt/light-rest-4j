package com.networknt.openapi;

import com.networknt.handler.config.HandlerConfig;
import com.networknt.security.*;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is very simple jwt verify handler that is used to verify jwt token without scopes. Other than scopes, it is
 * the same as the normal JwtVerifyHandler.
 * <p>
 * This handler extends AbstractJwtVerifyHandler but does not override getSpecScopes(), so it defaults to null
 * which skips scope verification.
 *
 * @author Steve Hu
 */
public class SimpleJwtVerifyHandler extends AbstractJwtVerifyHandler {
    static final Logger logger = LoggerFactory.getLogger(SimpleJwtVerifyHandler.class);

    String basePath;

    public SimpleJwtVerifyHandler() {
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
        return false;
    }

    // Note: getSpecScopes() is NOT overridden, so it defaults to null (no scope verification)
}

