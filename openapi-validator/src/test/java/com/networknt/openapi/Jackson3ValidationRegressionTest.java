package com.networknt.openapi;

import com.networknt.oas.OpenApi3Parser;
import com.networknt.oas.OpenApiParser.OpenApiParserException;
import com.networknt.oas.model.OpenApi3;
import io.undertow.server.HttpServerExchange;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Jackson3ValidationRegressionTest {
    private static final String SPEC = """
            openapi: 3.0.3
            info: {title: regression, version: '1'}
            paths:
              /items:
                get:
                  parameters:
                    - name: X-Required
                      in: header
                      required: %s
                      schema: {type: string}
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {type: object}
            """;

    @Test
    void absentHeaderUsesNullSafeRequiredPolicyAndReportsInvalidSpecScalars() throws Exception {
        for (String scalar : new String[]{"true", "false", "True", "yes"}) {
            OpenApi3 api = new OpenApi3Parser().parse(SPEC.formatted(scalar), URI.create("file:/regression.yaml").toURL());
            var path = api.getPath("/items");
            var normalised = new ApiNormalisedPath("/items", "");
            var operation = new OpenApiOperation(normalised, path, "get", path.getOperation("get"));
            var validator = new RequestValidator(new SchemaValidator(api), ValidatorConfig.load());
            var status = assertDoesNotThrow(() -> validator.validateRequest(normalised,
                    new HttpServerExchange(null), operation), scalar);
            if ("true".equals(scalar)) {
                assertNotNull(status);
                assertEquals("ERR11017", status.getCode());
            } else {
                assertNull(status);
            }
            assertEquals("true".equals(scalar) || "false".equals(scalar), api.isValid());
        }
    }

    @Test
    void malformedResponseReturnsTheExistingContentTypeError() {
        OpenApiHelper helper = new OpenApiHelper(SPEC.formatted("false"));
        var path = helper.openApi3.getPath("/items");
        var operation = new OpenApiOperation(new ApiNormalisedPath("/items", ""), path, "get", path.getOperation("get"));
        var validator = new ResponseValidator(new SchemaValidator(helper.openApi3), ValidatorConfig.load());
        assertNull(validator.validateResponseContent("{}", operation, "200", "application/json"));
        var expected = validator.validateResponseContent("not-json", operation, "200", "application/json");
        var actual = assertDoesNotThrow(() -> validator.validateResponseContent("{broken", operation, "200", "application/json"));
        assertNotNull(actual);
        assertEquals(expected.getCode(), actual.getCode());
    }

    @Test
    void injectedMalformedSpecificationRetainsParserErrorContract() {
        var config = com.networknt.config.Config.getInstance();
        var previous = config.getJsonMapConfig(OpenApiHandler.SPEC_INJECT);
        var routing = OpenApiHandler.routingSnapshot();
        try {
            config.putInConfigCache(OpenApiHandler.SPEC_INJECT, Map.of("unserializable", new Object()));
            var failure = assertThrows(RuntimeException.class,
                    () -> new OpenApiHandler("openapi-handler-multiple"));
            assertEquals("parse inject failed for adm", failure.getMessage());
            assertSame(routing, OpenApiHandler.routingSnapshot());
        } finally {
            config.clearConfigCache(OpenApiHandler.SPEC_INJECT);
            if (previous != null) config.putInConfigCache(OpenApiHandler.SPEC_INJECT, previous);
            OpenApiHandler.publishRouting(routing);
        }
        assertThrows(OpenApiParserException.class, () -> new OpenApiHelper("openapi: ["));
    }
}
