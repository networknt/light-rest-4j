package com.networknt.openapi;

import com.networknt.common.ContentType;
import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import com.networknt.httpstring.AttachmentConstants;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.util.List;
import java.util.stream.Collectors;

public class ForwardRequestHandler implements LightHttpHandler {

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String responseBody = null;
        if(exchange.getAttachment(AttachmentConstants.REQUEST_BODY) != null) {
            responseBody = Config.getInstance().getMapper().writeValueAsString(exchange.getAttachment(AttachmentConstants.REQUEST_BODY));
        }

        List<HttpString> headerNames = exchange.getRequestHeaders().getHeaderNames().stream()
                .filter( s -> s.toString().startsWith("todo"))
                .collect(Collectors.toList());
        for(HttpString headerName : headerNames) {
            String headerValue = exchange.getRequestHeaders().get(headerName).getFirst();
            exchange.getResponseHeaders().put(headerName, headerValue);
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ContentType.APPLICATION_JSON.value());
        exchange.getResponseSender().send(responseBody);
    }
}
