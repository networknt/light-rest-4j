package com.networknt.openapi;

import com.networknt.utility.Constants;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtEndpointFallbackTest {
    @Test
    void fallbackUsesTheSelectedServerBaseAndCanonicalEndpoint() {
        OpenApiHelper previous = OpenApiHandler.helper;
        Map<String, OpenApiHelper> previousMap = OpenApiHandler.helperMap;
        try {
            for (String base : new String[]{"/", "/api/"}) {
                OpenApiHelper helper = new OpenApiHelper("""
                        {"openapi":"3.0.0","info":{"title":"test","version":"1"},
                         "paths":{"/pets":{"get":{"responses":{"200":{"description":"OK"}}}}}}
                        """);
                helper.setBasePath(base);
                for (boolean multiple : new boolean[]{false, true}) {
                    OpenApiHandler.helper = multiple ? null : helper;
                    OpenApiHandler.helperMap = multiple ? Map.of(base, helper) : null;
                    String request = (base.equals("/") ? "" : "/api") + "/pets";
                    HttpServerExchange exchange = new HttpServerExchange(null);
                    exchange.setRequestMethod(Methods.GET);
                    exchange.setRequestURI(request);
                    exchange.setRequestPath(request);
                    Map<String, Object> audit = new HashMap<>();
                    JwtVerifyHandler handler = new JwtVerifyHandler();
                    assertNotNull(handler.getOperation(exchange, null, audit));
                    assertEquals(request + "@get", audit.get(Constants.ENDPOINT_STRING));
                    assertNotNull(audit.get(Constants.OPENAPI_OPERATION_STRING));
                }
            }
        } finally {
            OpenApiHandler.helper = previous;
            OpenApiHandler.helperMap = previousMap;
        }
    }
}
