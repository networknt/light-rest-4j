package com.networknt.openapi;

import com.networknt.apikey.ApiKeyHandler;
import com.networknt.basicauth.BasicAuthHandler;
import com.networknt.config.Config;
import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.StringUtils;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This is a security handler that combines Anonymous, ApiKey, Basic and OAuth together to avoid all of them
 * to be wired in the request/response chain and skip some of them based on the request path. It allows one
 * path to choose several security handlers at the same time. In most cases, this handler will only be used
 * in a shard light-gateway instance.
 *
 * @author Steve Hu
 */
public class UnifiedSecurityHandler implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(UnifiedSecurityHandler.class);
    static final String BEARER_PREFIX = "BEARER";
    static final String BASIC_PREFIX = "BASIC";
    static final String API_KEY = "apikey";
    static final String JWT = "jwt";
    static final String SWT = "swt";
    static final String MISSING_AUTH_TOKEN = "ERR10002";
    static final String INVALID_AUTHORIZATION_HEADER = "ERR12003";
    static final String HANDLER_NOT_FOUND = "ERR11200";
    static final String MISSING_PATH_PREFIX_AUTH = "ERR10078";
    static UnifiedSecurityConfig config;
    // make this static variable public so that it can be accessed from the server-info module
    private volatile HttpHandler next;

    public UnifiedSecurityHandler() {
        logger.info("UnifiedSecurityHandler starts");
        config = UnifiedSecurityConfig.load();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (logger.isDebugEnabled())
            logger.debug("UnifiedSecurityHandler.handleRequest starts.");
        String reqPath = exchange.getRequestPath();
        // check if the path prefix is in the anonymousPrefixes list. If yes, skip all other check and goes to next handler.
        if (config.getAnonymousPrefixes() != null && config.getAnonymousPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(logger.isTraceEnabled())
                logger.trace("Skip request path base on anonymousPrefixes for " + reqPath);
            Handler.next(exchange, next);
            return;
        }
        if(config.getPathPrefixAuths() != null) {
            boolean found = false;
            // iterate each entry to check enabled security methods.
            for(UnifiedPathPrefixAuth pathPrefixAuth: config.getPathPrefixAuths()) {
                if(logger.isTraceEnabled()) logger.trace("Check with requestPath = " + reqPath + " prefix = " + pathPrefixAuth.getPathPrefix());
                if(reqPath.startsWith(pathPrefixAuth.getPathPrefix())) {
                    found = true;
                    if(logger.isTraceEnabled()) logger.trace("Found with requestPath = " + reqPath + " prefix = " + pathPrefixAuth.getPathPrefix());
                    // check jwt and basic first with authorization header, then check the apikey if it is enabled.
                    if(pathPrefixAuth.isBasic() || pathPrefixAuth.isJwt() || pathPrefixAuth.isSwt()) {
                        String authorization = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                        if(authorization == null) {
                            logger.error("Basic or JWT or SWT is enabled and authorization header is missing.");
                            // set the WWW-Authenticate header to Basic realm="realm"
                            if(pathPrefixAuth.isBasic()) {
                                if(logger.isTraceEnabled()) logger.trace("Basic is enabled and set WWW-Authenticate header to Basic realm=\"Default Realm\"");
                                exchange.getResponseHeaders().put(Headers.WWW_AUTHENTICATE, "Basic realm=\"Default Realm\"");
                            }
                            setExchangeStatus(exchange, MISSING_AUTH_TOKEN);
                            if(logger.isDebugEnabled())
                                logger.debug("UnifiedSecurityHandler.handleRequest ends with an error.");
                            exchange.endExchange();
                            return;
                        } else {
                            // make sure that the length is greater than 5.
                            if(authorization.trim().length() <= 5) {
                                logger.error("Invalid/Unsupported authorization header {}", authorization);
                                setExchangeStatus(exchange, INVALID_AUTHORIZATION_HEADER, authorization);
                                exchange.endExchange();
                                return;
                            }
                            // check if it is basic or bearer and handler it differently.
                            if(BASIC_PREFIX.equalsIgnoreCase(authorization.substring(0, 5))) {
                                Map<String, HttpHandler> handlers = Handler.getHandlers();
                                BasicAuthHandler handler = (BasicAuthHandler) handlers.get(BASIC_PREFIX.toLowerCase());
                                if(handler == null) {
                                    logger.error("Cannot find BasicAuthHandler with alias name basic.");
                                    setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.basicauth.BasicAuthHandler@basic");
                                    exchange.endExchange();
                                    return;
                                } else {
                                    if(handler.handleBasicAuth(exchange, reqPath, authorization)) {
                                        // verification is passed, go to the next handler in the chain
                                        break;
                                    } else {
                                        // verification is not passed and an error is returned. Don't call the next handler.
                                        return;
                                    }
                                }
                            } else if (BEARER_PREFIX.equalsIgnoreCase(authorization.substring(0, 6))) {
                                // in the case that a bearer token is used, there are three scenarios: both jwt and swt are true, only jwt is true and only swt is true
                                // in the first case, we need to identify if the token is jwt or swt before calling the right handler to verify it.
                                Map<String, HttpHandler> handlers = Handler.getHandlers();
                                if(pathPrefixAuth.isJwt() && pathPrefixAuth.isSwt()) {
                                    // both jwt and swt are enabled.
                                    boolean isJwt = StringUtils.isJwtToken(authorization);
                                    if(logger.isTraceEnabled()) logger.trace("Both jwt and swt are true and check token is jwt = {}", isJwt);
                                    if(isJwt) {
                                        JwtVerifyHandler handler = (JwtVerifyHandler) handlers.get(JWT);
                                        if (handler == null) {
                                            logger.error("Cannot find JwtVerifyHandler with alias name jwt.");
                                            setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.openapi.JwtVerifyHandler@jwt");
                                            exchange.endExchange();
                                            return;
                                        } else {
                                            // get the jwkServiceIds list.
                                            if (handler.handleJwt(exchange, pathPrefixAuth.getPathPrefix(), reqPath, pathPrefixAuth.getJwkServiceIds())) {
                                                // verification is passed, go to the next handler in the chain.
                                                break;
                                            } else {
                                                // verification is not passed and an error is returned. Don't call the next handler.
                                                return;
                                            }
                                        }
                                    } else {
                                        SwtVerifyHandler handler = (SwtVerifyHandler) handlers.get(SWT);
                                        if (handler == null) {
                                            logger.error("Cannot find SwtVerifyHandler with alias name swt.");
                                            setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.openapi.SwtVerifyHandler@swt");
                                            exchange.endExchange();
                                            return;
                                        } else {
                                            // get the jwkServiceIds list.
                                            if (handler.handleSwt(exchange, reqPath, pathPrefixAuth.getSwtServiceIds())) {
                                                // verification is passed, go to the next handler in the chain.
                                                break;
                                            } else {
                                                // verification is not passed and an error is returned. Don't call the next handler.
                                                return;
                                            }
                                        }
                                    }
                                } else if(pathPrefixAuth.isJwt()) {
                                    // only jwt is enabled
                                    JwtVerifyHandler handler = (JwtVerifyHandler) handlers.get(JWT);
                                    if (handler == null) {
                                        logger.error("Cannot find JwtVerifyHandler with alias name jwt.");
                                        setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.openapi.JwtVerifyHandler@jwt");
                                        exchange.endExchange();
                                        return;
                                    } else {
                                        // get the jwkServiceIds list.
                                        if (handler.handleJwt(exchange, pathPrefixAuth.getPathPrefix(), reqPath, pathPrefixAuth.getJwkServiceIds())) {
                                            // verification is passed, go to the next handler in the chain.
                                            break;
                                        } else {
                                            // verification is not passed and an error is returned. Don't call the next handler.
                                            return;
                                        }
                                    }
                                } else {
                                    // only swt is enabled
                                    SwtVerifyHandler handler = (SwtVerifyHandler) handlers.get(SWT);
                                    if (handler == null) {
                                        logger.error("Cannot find SwtVerifyHandler with alias name swt.");
                                        setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.openapi.SwtVerifyHandler@swt");
                                        exchange.endExchange();
                                        return;
                                    } else {
                                        // get the jwkServiceIds list.
                                        if (handler.handleSwt(exchange, reqPath, pathPrefixAuth.getSwtServiceIds())) {
                                            // verification is passed, go to the next handler in the chain.
                                            break;
                                        } else {
                                            // verification is not passed and an error is returned. Don't call the next handler.
                                            return;
                                        }
                                    }
                                }
                            } else {
                                String s = authorization.length() > 10 ? authorization.substring(0, 10) : authorization;
                                logger.error("Invalid/Unsupported authorization header {}", s);
                                setExchangeStatus(exchange, INVALID_AUTHORIZATION_HEADER, s);
                                exchange.endExchange();
                                return;
                            }
                        }
                    } else if (pathPrefixAuth.isApikey()) {
                        Map<String, HttpHandler> handlers = Handler.getHandlers();
                        ApiKeyHandler handler = (ApiKeyHandler) handlers.get(API_KEY);
                        if(handler == null) {
                            logger.error("Cannot find ApiKeyHandler with alias name apikey.");
                            setExchangeStatus(exchange, HANDLER_NOT_FOUND, "com.networknt.apikey.ApiKeyHandler@apikey");
                            exchange.endExchange();
                            return;
                        } else {
                            if(handler.handleApiKey(exchange, reqPath)) {
                                // the APIKey handler successfully verified the credentials. Need to break here so that the next handler can be called.
                                break;
                            } else {
                                // verification is not passed and an error is returned. need to bypass the next handler.
                                return;
                            }
                        }

                    }
                }
            }
            if(!found) {
                // cannot find the prefix auth entry for request path.
                logger.error("Cannot find prefix entry in pathPrefixAuths for " + reqPath);
                setExchangeStatus(exchange, MISSING_PATH_PREFIX_AUTH, reqPath);
                exchange.endExchange();
                return;
            }
        } else {
            // pathPrefixAuths is not defined in the values.yml
            logger.error("Cannot find pathPrefixAuths definition for " + reqPath);
            setExchangeStatus(exchange, MISSING_PATH_PREFIX_AUTH, reqPath);
            exchange.endExchange();
            return;
        }

        if(logger.isDebugEnabled()) logger.debug("UnifiedSecurityHandler.handleRequest ends.");
        Handler.next(exchange, next);
    }


    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(HttpHandler next) {
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
        ModuleRegistry.registerModule(UnifiedSecurityConfig.CONFIG_NAME, UnifiedSecurityHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(UnifiedSecurityConfig.CONFIG_NAME), null);
    }

    @Override
    public void reload() {
        config.reload();
        ModuleRegistry.registerModule(UnifiedSecurityConfig.CONFIG_NAME, UnifiedSecurityHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(UnifiedSecurityConfig.CONFIG_NAME), null);
    }

}
