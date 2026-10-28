package com.networknt.specification;

import com.networknt.config.Config;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HstsConfigurationExampleTest {
    private Map<String, Object> example() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/hsts-example/values.yml")) {
            assertNotNull(input);
            return Config.getInstance().getYaml().load(input);
        }
    }

    @Test
    void dedicatedIngressExampleDoesNotReplaceApplicationChains() throws Exception {
        Map<String, Object> values = example();
        assertEquals(Boolean.TRUE, values.get("server.enableHttps"));
        assertEquals(Boolean.FALSE, values.get("server.enableHttp"));
        assertEquals(Boolean.TRUE, values.get("header.enabled"));
        assertFalse(values.containsKey("handler.chains"));
        assertFalse(values.containsKey("handler.paths"));
        assertFalse(values.containsKey("header.response.update"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exampleUsesLateResponsePolicyWithoutBroadeningTheDomain() throws Exception {
        Map<String, Object> prefixes = (Map<String, Object>) example().get("header.pathPrefixHeader");
        Map<String, Object> root = (Map<String, Object>) prefixes.get("/");
        Map<String, Object> response = (Map<String, Object>) root.get("response");
        Map<String, Object> update = (Map<String, Object>) response.get("update");
        assertEquals("max-age=31536000", update.get("Strict-Transport-Security"));
        assertEquals("no-store", update.get("Cache-Control"));
        assertEquals("nosniff", update.get("X-Content-Type-Options"));
    }
}
