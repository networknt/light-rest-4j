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
class JwtEndpointFallbackTest {
    @Test
    void scopeLookupUsesTheRequestsGenerationAfterReload() throws Exception {
        OpenApiRouting previous = OpenApiHandler.routingSnapshot();
        try {
            String spec = """
                    {"openapi":"3.0.0","info":{"title":"test","version":"1"},
                     "paths":{"/pets":{"get":{"security":[{"oldAuth":["read"]}],
                       "responses":{"200":{"description":"OK"}}}}},
                     "components":{"securitySchemes":{"oldAuth":{"type":"oauth2",
                       "flows":{"clientCredentials":{"tokenUrl":"https://example.com/token",
                         "scopes":{"read":"read"}}}}}}}
                    """;
            OpenApiHelper old = new OpenApiHelper(spec);
            OpenApiRouting generation = new OpenApiRouting(old, null);
            OpenApiHandler.publishRouting(generation);
            HttpServerExchange exchange = new HttpServerExchange(null);
            exchange.setRequestURI("/pets");
            exchange.setRequestPath("/pets");
            exchange.setRequestMethod(Methods.GET);
            exchange.putAttachment(OpenApiHandler.ROUTING, generation);
            Map<String, Object> audit = new HashMap<>();
            JwtVerifyHandler handler = new JwtVerifyHandler();
            assertNotNull(handler.getOperation(exchange, null, audit));
            OpenApiHandler.publishRouting(new OpenApiRouting(new OpenApiHelper(spec.replace("oldAuth", "newAuth")), null));
            assertEquals(java.util.List.of("read"), handler.getSpecScopes(exchange, audit));
        } finally {
            OpenApiHandler.publishRouting(previous);
        }
    }

    @Test
    void fallbackOptionsLookupUsesRootLocale() {
        OpenApiRouting previous = OpenApiHandler.routingSnapshot();
        java.util.Locale locale = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            OpenApiHelper helper = new OpenApiHelper("""
                    {"openapi":"3.0.0","info":{"title":"test","version":"1"},
                     "paths":{"/pets":{"options":{"responses":{"200":{"description":"OK"}}}}}}
                    """);
            OpenApiHandler.publishRouting(new OpenApiRouting(helper, null));
            HttpServerExchange exchange = new HttpServerExchange(null);
            exchange.setRequestURI("/pets");
            exchange.setRequestPath("/pets");
            exchange.setRequestMethod(Methods.OPTIONS);
            assertNotNull(new JwtVerifyHandler().getOperation(exchange, null, new HashMap<>()));
        } finally {
            java.util.Locale.setDefault(locale);
            OpenApiHandler.publishRouting(previous);
        }
    }

    @Test
    void fallbackUsesTheSelectedServerBaseAndCanonicalEndpoint() {
        OpenApiRouting previousRouting = OpenApiHandler.routingSnapshot();
        try {
            for (String base : new String[]{"/", "/api/"}) {
                OpenApiHelper helper = new OpenApiHelper("""
                        {"openapi":"3.0.0","info":{"title":"test","version":"1"},
                         "paths":{"/pets":{"get":{"responses":{"200":{"description":"OK"}}}}}}
                        """);
                helper.setBasePath(base);
                for (boolean multiple : new boolean[]{false, true}) {
                    OpenApiHandler.publishRouting(new OpenApiRouting(multiple ? null : helper, multiple ? Map.of(base, helper) : null));
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
            OpenApiHandler.publishRouting(previousRouting);
        }
    }
}
