package com.networknt.specification;

import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is the handler that returns the favicon.ico from the resources config folder for
 * swagger ui rendering.
 *
 * @author Steve Hu
 */
public class FaviconHandler implements LightHttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(FaviconHandler.class);
    private static final String FAVICON_CONTENT_TYPE = "image/x-icon";
    private static final HttpString X_CONTENT_TYPE_OPTIONS = new HttpString("X-Content-Type-Options");
    private static final String NOSNIFF = "nosniff";

    public FaviconHandler(){
        if(logger.isInfoEnabled()) logger.info("FaviconHandler is initialized.");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.startBlocking();
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, FAVICON_CONTENT_TYPE);
        exchange.getResponseHeaders().put(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
        try (InputStream inputStream = Config.getInstance().getInputStreamFromFile("favicon.ico"); OutputStream outputStream = exchange.getOutputStream()) {
            byte[] buf = new byte[8192];
            int c;
            while ((c = inputStream.read(buf, 0, buf.length)) > 0) {
                outputStream.write(buf, 0, c);
                outputStream.flush();
            }
        }
    }
}
