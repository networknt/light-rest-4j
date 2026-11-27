/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiMcpConfigTest {

    @AfterEach
    void clear() {
        Config.getInstance().clearConfigCache(OpenApiMcpConfig.CONFIG_NAME);
    }

    // A config value, and the start of the error message. A null value leaves the setting out.
    static Stream<Arguments> invalidConfig() {
        return Stream.of(
                Arguments.of("endpoints", "[\"/pets@get\"] x", "openapi-mcp endpoints is not a valid JSON array of strings"),
                Arguments.of("endpoints", "[\"/pets@get\"] []", "openapi-mcp endpoints is not a valid JSON array of strings"),
                Arguments.of("downstreamHost", null, "openapi-mcp downstreamHost must be an absolute http or https URL"),
                Arguments.of("downstreamHost", "", "openapi-mcp downstreamHost must be an absolute http or https URL"),
                Arguments.of("downstreamHost", "localhost:9080", "openapi-mcp downstreamHost must be an absolute http or https URL"),
                Arguments.of("downstreamHost", "/petstore", "openapi-mcp downstreamHost must be an absolute http or https URL"),
                Arguments.of("downstreamHost", "ftp://pets.example.com", "openapi-mcp downstreamHost must be an absolute http or https URL"),
                Arguments.of("downstreamHost", "https://pets.example.com?code=k", "openapi-mcp downstreamHost must not have a query, fragment or user info"),
                Arguments.of("downstreamHost", "https://pets.example.com/#x", "openapi-mcp downstreamHost must not have a query, fragment or user info"),
                Arguments.of("downstreamHost", "https://user:secret@pets.example.com", "openapi-mcp downstreamHost must not have a query, fragment or user info"),
                Arguments.of("maxResponseBodySize", 0, "openapi-mcp maxResponseBodySize must be between 1 and 2147483646: 0"),
                Arguments.of("maxResponseBodySize", -1, "openapi-mcp maxResponseBodySize must be between 1 and 2147483646: -1"),
                Arguments.of("maxResponseBodySize", Integer.MAX_VALUE, "openapi-mcp maxResponseBodySize must be between 1 and 2147483646: 2147483647"));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource
    void invalidConfig(String name, Object value, String message) {
        Map<String, Object> config = new HashMap<>(Map.of("downstreamHost", "http://localhost:9080"));
        config.put(name, value);
        Config.getInstance().putInConfigCache(OpenApiMcpConfig.CONFIG_NAME, config);
        ConfigException e = assertThrows(ConfigException.class, OpenApiMcpConfig::load);
        assertTrue(e.getMessage().startsWith(message), e.getMessage());
    }

    @Test
    void validConfig() {
        Config.getInstance().putInConfigCache(OpenApiMcpConfig.CONFIG_NAME,
                Map.of("downstreamHost", "HTTPS://pets.example.com/petstore/", "maxResponseBodySize", 1));
        OpenApiMcpConfig config = OpenApiMcpConfig.load();
        assertEquals("HTTPS://pets.example.com/petstore/", config.getDownstreamHost());
        assertEquals(1, config.getMaxResponseBodySize());
    }
}
