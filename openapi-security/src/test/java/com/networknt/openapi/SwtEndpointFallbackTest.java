package com.networknt.openapi;

import com.networknt.utility.Constants;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.util.SetSystemProperty;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SetSystemProperty(key = "light_4j_config_password", value = "light")
class SwtEndpointFallbackTest {
    @Test
    void fallbackUsesTheSelectedSpecBaseAndCanonicalEndpoint() {
        OpenApiRouting previous = OpenApiHandler.routingSnapshot();
        try {
            for (String base : new String[]{"/", "/api/"}) {
                OpenApiHelper helper = new OpenApiHelper("""
                        {"openapi":"3.0.0","info":{"title":"test","version":"1"},
                         "paths":{"/pets":{"get":{"responses":{"200":{"description":"OK"}}}}}}
                        """);
                helper.setBasePath(base);
                for (boolean multiple : new boolean[]{false, true}) {
                    OpenApiHandler.publishRouting(new OpenApiRouting(multiple ? null : helper,
                            multiple ? Map.of(base, helper) : null));
                    String uri = (base.equals("/") ? "" : "/api") + "/pets";
                    HttpServerExchange exchange = new HttpServerExchange(null);
                    exchange.setRequestURI(uri);
                    exchange.setRequestPath(uri);
                    exchange.setRequestMethod(Methods.GET);
                    SwtVerifyHandler handler = new SwtVerifyHandler();
                    handler.basePath = "/different-handler-default";
                    Map<String, Object> audit = new HashMap<>();
                    assertNotNull(handler.getOperation(exchange, null, audit));
                    assertEquals(uri + "@get", audit.get(Constants.ENDPOINT_STRING));
                }
            }
        } finally {
            OpenApiHandler.publishRouting(previous);
        }
    }
}
