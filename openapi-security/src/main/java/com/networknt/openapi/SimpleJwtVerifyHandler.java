package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.security.*;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is very simple jwt verify handler that is used to verify jwt token without scopes. Other than scopes, it is
 * the same as the normal JwtVerifyHandler.
 *
 * @author Steve Hu
 */
public class SimpleJwtVerifyHandler extends AbstractSimpleJwtVerifyHandler {
    static final Logger logger = LoggerFactory.getLogger(SimpleJwtVerifyHandler.class);
    static final String OPENAPI_SECURITY_CONFIG = "openapi-security";

    static SecurityConfig config;

    // make this static variable public so that it can be accessed from the server-info module
    public static JwtVerifier jwtVerifier;

    String basePath;

    private volatile HttpHandler next;

    public SimpleJwtVerifyHandler() {
        // at this moment, we assume that the OpenApiHandler is fully loaded with a single spec or multiple specs.
        // And the basePath is the correct one from the OpenApiHandler helper or helperMap if multiple is used.
        config = SecurityConfig.load(OPENAPI_SECURITY_CONFIG);
        jwtVerifier = new JwtVerifier(config);
        // in case that the specification doesn't exist, get the basePath from the handler.yml for endpoint lookup.
        HandlerConfig handlerConfig = HandlerConfig.load();
        this.basePath = handlerConfig == null ? "/" : handlerConfig.getBasePath();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRequest(final HttpServerExchange exchange) throws Exception {

        if (logger.isDebugEnabled())
            logger.debug("SimpleJwtVerifyHandler.handleRequest starts.");

        String reqPath = exchange.getRequestPath();

        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the security check.
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(logger.isTraceEnabled())
                logger.trace("Skip request path base on skipPathPrefixes for {}", reqPath);
            Handler.next(exchange, next);
            if (logger.isDebugEnabled())
                logger.debug("SimpleJwtVerifyHandler.handleRequest ends.");
            return;
        }
        // only UnifiedSecurityHandler will have the jwkServiceIds as the third parameter.
        if(handleJwt(exchange, null, reqPath, null)) {
            if(logger.isDebugEnabled()) logger.debug("SimpleJwtVerifyHandler.handleRequest ends.");
            Handler.next(exchange, next);
        }
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
        return config.isEnableVerifyJwt();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(OPENAPI_SECURITY_CONFIG, SimpleJwtVerifyHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(OPENAPI_SECURITY_CONFIG), null);
    }

    @Override
    public void reload() {
        config.reload(OPENAPI_SECURITY_CONFIG);
        jwtVerifier = new JwtVerifier(config);
        ModuleRegistry.registerModule(OPENAPI_SECURITY_CONFIG, SimpleJwtVerifyHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(OPENAPI_SECURITY_CONFIG), null);
    }

}
