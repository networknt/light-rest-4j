package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiEnabledTest {
    @Test
    void configuredMultiSpecHandlerRemainsEnabledWithoutAnyLoadedHelpers() {
        OpenApiRouting previous = OpenApiHandler.routingSnapshot();
        Config config = Config.getInstance();
        try {
            config.clear();
            config.setClassLoader(new ClassLoader(getClass().getClassLoader()) {
                @Override
                public InputStream getResourceAsStream(String name) {
                    return name.contains("openapi-inject.") ? null : super.getResourceAsStream(name);
                }
            });
            config.putInConfigCache("openapi-handler", Map.of(
                    "multipleSpec", true, "ignoreInvalidPath", false, "pathSpecMapping", Map.of()));
            OpenApiHandler handler = new OpenApiHandler();
            assertTrue(OpenApiHandler.routingSnapshot().helpers().isEmpty());
            assertTrue(handler.isEnabled(), "enabled status must retain the mapped-config contract");
        } finally {
            config.clear();
            config.setClassLoader(getClass().getClassLoader());
            OpenApiHandler.publishRouting(previous);
        }
    }
}
