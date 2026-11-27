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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolCallerTest {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    private static final McpTool TOOL = new McpTool("listPets", "GET", "/pets/{petId}",
            List.of(new McpTool.Parameter("petId", "path"), new McpTool.Parameter("tags", "query"), new McpTool.Parameter("limit", "query"), new McpTool.Parameter("all", "query")),
            false, Map.of());

    static Stream<Arguments> targets() {
        return Stream.of(
                Arguments.of("{'petId': 'a/b c'}", "/pets/a%2Fb%20c"),
                Arguments.of("{'petId': '~*+%7E-._é'}", "/pets/~%2A%2B%257E-._%C3%A9"),
                Arguments.of("{'petId': [1, 2], 'tags': ['x', 'y z'], 'all': true}", "/pets/1,2?tags=x&tags=y%20z&all=true"),
                Arguments.of("{'petId': 'p', 'limit': 12345678901234567890.10, 'tags': null}", "/pets/p?limit=12345678901234567890.10"),
                Arguments.of("{'petId': '...'}", "/pets/..."),
                Arguments.of("{'petId': 'a/.../b..'}", "/pets/a%2F...%2Fb.."),
                Arguments.of("{'petId': ['.', '.']}", "/pets/.,."));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource
    void targets(String arguments, String target) {
        assertEquals(target, McpToolCaller.target(TOOL, json(arguments), new HashMap<>()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'.'", "'..'", "['..']", "''", "[]", "'../../admin'", "'a/../b'", "'x/.'", "'..\\\\x'", "'a\\\\.'"})
    void refusedPathArgument(String petId) {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> McpToolCaller.target(TOOL, json("{'petId': " + petId + "}"), new HashMap<>()));
        assertTrue(e.getMessage().startsWith("Invalid value for path parameter petId: "), e.getMessage());
    }

    @Test
    void objectArgument() {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> McpToolCaller.target(TOOL, json("{'petId': 'p', 'tags': {'a': 1}}"), new HashMap<>()));
        assertEquals("Unsupported value for parameter tags: only strings, numbers, booleans and arrays of them are supported", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e999999999", "1e-999999999", "1e1000", "[1, 1e1000]"})
    void numberTooLongWrittenOut(String limit) {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> McpToolCaller.target(TOOL, json("{'petId': 'p', 'limit': " + limit + "}"), new HashMap<>()));
        assertEquals("Unsupported value for parameter limit: a number may have at most 1000 digits when written out in full", e.getMessage());
    }

    @Test
    void numberOf1000Digits() {
        assertEquals("/pets/p?limit=1" + "0".repeat(999), McpToolCaller.target(TOOL, json("{'petId': 'p', 'limit': 1e999}"), new HashMap<>()));
    }

    static Stream<Arguments> results() {
        byte[] eleven = "12345678901".getBytes(StandardCharsets.UTF_8);
        return Stream.of(
                Arguments.of(200, "application/problem+json", bytes("{\"a\":1}"), List.of(text("{\"a\":1}")), false),
                Arguments.of(200, "audio/wav; rate=8000", new byte[]{1, 2, 3},
                        List.of(Map.of("type", "audio", "data", "AQID", "mimeType", "audio/wav")), false),
                Arguments.of(200, "application/pdf", bytes("%PDF"), List.of(text("Unsupported response Content-Type: application/pdf")), true),
                Arguments.of(200, null, bytes("x"), List.of(text("Unsupported response Content-Type: none")), true),
                Arguments.of(200, "text/plain", eleven, List.of(text("Response body exceeds the 10 byte limit")), true),
                Arguments.of(418, "text/plain", bytes("teapot"), List.of(text("HTTP 418"), text("teapot")), true),
                Arguments.of(302, null, new byte[0], List.of(text("HTTP 302 Found")), true));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource
    void results(int status, String contentType, byte[] body, List<Map<String, Object>> content, boolean isError) {
        McpToolCaller caller = new McpToolCaller("http://localhost", 10, MAPPER);
        assertEquals(Map.of("content", content, "isError", isError),
                caller.result(status, contentType, body));
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json.replace('\'', '"'));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> text(String text) {
        return Map.of("type", "text", "text", text);
    }
}
