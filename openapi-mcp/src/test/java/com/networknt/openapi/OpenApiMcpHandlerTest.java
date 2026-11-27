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
import com.networknt.httpstring.AttachmentConstants;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs example MCP requests against a stub backend, and checks the MCP responses and the backend requests.
 */
class OpenApiMcpHandlerTest {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static Undertow backend;
    private static Undertow mcp;
    private static volatile BackendRequest lastRequest;
    private static volatile Map<String, Object> lastAuditInfo;

    // What the backend received.
    private record BackendRequest(String target, Map<String, List<String>> headers, String body) { }

    @BeforeAll
    static void start() {
        backend = Undertow.builder().addHttpListener(0, "localhost")
                .setHandler(new BlockingHandler(OpenApiMcpHandlerTest::backend)).build();
        backend.start();
        Map<String, Object> config = new HashMap<>();
        config.put("downstreamHost", "http://localhost:" + port(backend));
        config.put("endpoints", List.of("/pets/{petId}@get", "/pets/{petId}@delete", "/pets/{petId}/photo@get", "/pets@post"));
        Config.getInstance().putInConfigCache(OpenApiMcpConfig.CONFIG_NAME, config);
        OpenApiMcpHandler handler = new OpenApiMcpHandler();
        // Attaches the audit info first, as JwtVerifyHandler does, so that the test can read what the handler adds.
        mcp = Undertow.builder().addHttpListener(0, "localhost").setHandler(exchange -> {
            lastAuditInfo = new ConcurrentHashMap<>();
            exchange.putAttachment(AttachmentConstants.AUDIT_INFO, lastAuditInfo);
            handler.handleRequest(exchange);
        }).build();
        mcp.start();
    }

    @AfterAll
    static void stop() {
        mcp.stop();
        backend.stop();
        Config.getInstance().clearConfigCache(OpenApiMcpConfig.CONFIG_NAME);
    }

    @BeforeEach
    void forgetLastRequest() {
        lastRequest = null;
    }

    private static void backend(HttpServerExchange exchange) throws Exception {
        String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (HeaderValues values : exchange.getRequestHeaders()) headers.put(values.getHeaderName().toString(), new ArrayList<>(values));
        String query = exchange.getQueryString().isEmpty() ? "" : "?" + exchange.getQueryString();
        lastRequest = new BackendRequest(exchange.getRequestMethod() + " " + exchange.getRequestURI() + query, headers, body);
        switch (exchange.getRequestMethod() + " " + exchange.getRequestPath()) {
            case "GET /pets/123" -> send(exchange, 200, "application/json", "{\"id\":\"123\",\"name\":\"Rex\"}");
            case "GET /pets/999" -> send(exchange, 404, "application/json", "{\"message\":\"Pet 999 not found\"}");
            case "GET /pets/slow" -> Thread.sleep(2000);
            case "GET /pets/stall-body" -> {
                // Headers and part of the body, then a stall longer than client.timeout (1000 ms).
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.setResponseContentLength(100);
                exchange.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
                exchange.getOutputStream().flush();
                Thread.sleep(3000);
            }
            case "POST /pets" -> send(exchange, 201, "application/json", "{\"id\":\"124\"," + body.substring(1));
            case "DELETE /pets/123" -> exchange.setStatusCode(204);
            case "GET /pets/123/photo" -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "image/png");
                exchange.getResponseSender().send(ByteBuffer.wrap(PNG));
            }
            default -> exchange.setStatusCode(500);
        }
    }

    private static void send(HttpServerExchange exchange, int status, String contentType, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        exchange.getResponseSender().send(body);
    }

    @Test
    void discovery() throws Exception {
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 0,
                  "result": {
                    "resultType": "complete",
                    "supportedVersions": ["2026-07-28", "2025-11-25", "2025-06-18", "2025-03-26"],
                    "capabilities": { "tools": {} },
                    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "Pet Store", "version": "1.0.0" } },
                    "ttlMs": 300000,
                    "cacheScope": "public" } }""",
                send("{ \"jsonrpc\": \"2.0\", \"id\": 0, \"method\": \"server/discover\" }"));
    }

    @Test
    void toolDefinitions() throws Exception {
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 1,
                  "result": {
                    "resultType": "complete",
                    "tools": [
                      {
                        "name": "getPet",
                        "description": "Get a pet by its ID.",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "petId": { "type": "string", "description": "The pet's ID." },
                            "fields": { "type": "string" },
                            "X-Locale": { "type": "string" }
                          },
                          "required": ["petId"]
                        }
                      },
                      {
                        "name": "deletePet",
                        "description": "Delete a pet.",
                        "inputSchema": {
                          "type": "object",
                          "properties": { "petId": { "type": "string", "description": "The pet's ID." } },
                          "required": ["petId"]
                        }
                      },
                      {
                        "name": "getPetPhoto",
                        "description": "Get a pet's photo.",
                        "inputSchema": {
                          "type": "object",
                          "properties": { "petId": { "type": "string" } },
                          "required": ["petId"]
                        }
                      },
                      {
                        "name": "createPet",
                        "description": "Create a pet.",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "body": { "$ref": "#/$defs/NewPet" }
                          },
                          "required": ["body"],
                          "$defs": {
                            "NewPet": {
                              "type": "object",
                              "required": ["name"],
                              "properties": {
                                "name": { "type": "string" },
                                "price": { "type": "number" },
                                "owner": { "$ref": "#/$defs/Owner" }
                              }
                            },
                            "Owner": {
                              "type": "object",
                              "properties": { "name": { "type": "string" } }
                            }
                          }
                        }
                      }
                    ],
                    "ttlMs": 300000,
                    "cacheScope": "public" } }""",
                send("{ \"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"tools/list\" }"));
    }

    @Test
    void getWithPathQueryAndHeaderParameters() throws Exception {
        HttpResponse<String> response = send("""
                        { "jsonrpc": "2.0", "id": 2, "method": "tools/call",
                          "params": { "name": "getPet", "arguments": { "petId": "123", "fields": "name", "X-Locale": "en-CA" } } }""",
                "Authorization", "Bearer backend-token", "X-Correlation-Id", "7d2a6b0c", "Accept-Encoding", "gzip",
                "X-Forwarded-For", "198.51.100.1", "service_id", "com.networknt.petstore-1.0.0",
                "Trailer", "X-Checksum", "Proxy-Connection", "keep-alive");
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 2,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "{\\"id\\":\\"123\\",\\"name\\":\\"Rex\\"}" } ], "isError": false } }""",
                response);

        assertEquals("GET /pets/123?fields=name", lastRequest.target());
        assertEquals("getPet", lastAuditInfo.get("toolName"));
        Map<String, List<String>> headers = lastRequest.headers();
        assertEquals(List.of("localhost:" + port(backend)), headers.get("Host"));
        assertEquals(List.of("Bearer backend-token"), headers.get("Authorization"));
        assertEquals(List.of("7d2a6b0c"), headers.get("X-Correlation-Id"));
        assertEquals(List.of("en-CA"), headers.get("X-Locale"));
        assertEquals(List.of("identity"), headers.get("Accept-Encoding"));
        assertEquals(List.of("127.0.0.1"), headers.get("X-Forwarded-For"));
        assertEquals(List.of("http"), headers.get("X-Forwarded-Proto"));
        assertEquals(List.of("localhost"), headers.get("X-Forwarded-Host"));
        assertEquals(List.of(String.valueOf(port(mcp))), headers.get("X-Forwarded-Port"));
        assertEquals(List.of("localhost"), headers.get("X-Forwarded-Server"));
        for (String name : List.of("Accept", "Content-Type", "MCP-Protocol-Version", "Mcp-Method", "Mcp-Name", "service_id", "Trailer", "Proxy-Connection")) {
            assertFalse(headers.containsKey(name), name + " is forwarded");
        }
        // The JDK client sends Content-Length: 0 for a request without a body, instead of the inbound Content-Length.
        assertEquals(List.of("0"), headers.getOrDefault("Content-Length", List.of("0")));
    }

    @Test
    void postWithJsonRequestBody() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 3, "method": "tools/call",
                  "params": { "name": "createPet", "arguments": { "body": { "name": "Rex", "price": 12345678901234567890.10 } } } }""");
        assertEquals("POST /pets", lastRequest.target());
        assertEquals(List.of("application/json"), lastRequest.headers().get("Content-Type"));
        assertEquals("{\"name\":\"Rex\",\"price\":12345678901234567890.10}", lastRequest.body());
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 3,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "{\\"id\\":\\"124\\",\\"name\\":\\"Rex\\",\\"price\\":12345678901234567890.10}" } ], "isError": false } }""",
                response);
    }

    @Test
    void backendError() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 4, "method": "tools/call",
                  "params": { "name": "getPet", "arguments": { "petId": "999" } } }""");
        assertEquals("GET /pets/999", lastRequest.target());
        assertFalse(lastRequest.headers().containsKey("X-Locale"));
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 4,
                  "result": {
                    "resultType": "complete",
                    "content": [
                      { "type": "text", "text": "HTTP 404 Not Found" },
                      { "type": "text", "text": "{\\"message\\":\\"Pet 999 not found\\"}" }
                    ],
                    "isError": true } }""",
                response);
    }

    @Test
    void emptyResponseBody() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 5, "method": "tools/call",
                  "params": { "name": "deletePet", "arguments": { "petId": "123" } } }""");
        assertEquals("DELETE /pets/123", lastRequest.target());
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 5, "result": { "resultType": "complete", "content": [], "isError": false } }""", response);
    }

    @Test
    void imageResponse() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 6, "method": "tools/call",
                  "params": { "name": "getPetPhoto", "arguments": { "petId": "123" } } }""");
        assertEquals("GET /pets/123/photo", lastRequest.target());
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 6,
                  "result": { "resultType": "complete", "content": [ { "type": "image", "data": "iVBORw0KGgo=", "mimeType": "image/png" } ], "isError": false } }""",
                response);
    }

    @Test
    void missingPathParameter() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 7, "method": "tools/call",
                  "params": { "name": "getPet", "arguments": { "fields": "name" } } }""");
        assertNull(lastRequest, "the backend is called");
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 7,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "Missing required path parameter: petId" } ], "isError": true } }""",
                response);
    }

    @Test
    void invalidHeaderArgument() throws Exception {
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 7, "method": "tools/call",
                  "params": { "name": "getPet", "arguments": { "petId": "123", "X-Locale": "en\\r\\nX-Injected: 1" } } }""");
        assertNull(lastRequest, "the backend is called");
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 7,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "invalid header value: \\"en\\r\\nX-Injected: 1\\"" } ], "isError": true } }""",
                response);
    }

    @Test
    void transportFailure() throws Exception {
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 8,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "Backend request failed: timed out" } ], "isError": true } }""",
                send("""
                        { "jsonrpc": "2.0", "id": 8, "method": "tools/call",
                          "params": { "name": "getPet", "arguments": { "petId": "slow" } } }"""));
    }

    // The JDK 25 HttpClient stops its request timeout once the response headers arrive.
    @Test
    void backendStallsAfterHeaders() throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = send("""
                { "jsonrpc": "2.0", "id": 8, "method": "tools/call",
                  "params": { "name": "getPet", "arguments": { "petId": "stall-body" } } }""");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 8,
                  "result": { "resultType": "complete", "content": [ { "type": "text", "text": "Backend request failed: timed out" } ], "isError": true } }""",
                response);
        assertTrue(elapsedMs < 2500, "answered after " + elapsedMs + " ms, not at the 1000 ms client.timeout");
    }

    @Test
    void unsupportedProtocolVersion() throws Exception {
        assertResponse(400, """
                { "jsonrpc": "2.0", "id": 9,
                  "error": { "code": -32022, "message": "Unsupported protocol version",
                             "data": { "supported": ["2026-07-28", "2025-11-25", "2025-06-18", "2025-03-26"], "requested": "2024-11-05" } } }""",
                send("{ \"jsonrpc\": \"2.0\", \"id\": 9, \"method\": \"tools/list\" }", "MCP-Protocol-Version", "2024-11-05"));
    }

    // The client's version if it is a supported legacy version, otherwise the latest legacy version.
    static Stream<Arguments> initialize() {
        return Stream.of(
                Arguments.of("", "2025-03-26", "2025-03-26"),
                Arguments.of("2025-06-18", "2025-06-18", "2025-06-18"),
                Arguments.of("", "2025-11-25", "2025-11-25"),
                Arguments.of("", "2024-11-05", "2025-11-25"),
                Arguments.of("", "2026-07-28", "2025-11-25"));
    }

    @ParameterizedTest(name = "header {0}, requested {1}")
    @MethodSource
    void initialize(String header, String requested, String negotiated) throws Exception {
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 11,
                  "result": { "protocolVersion": "%s", "capabilities": { "tools": {} },
                              "serverInfo": { "name": "Pet Store", "version": "1.0.0" } } }""".formatted(negotiated),
                post("""
                        { "jsonrpc": "2.0", "id": 11, "method": "initialize",
                          "params": { "protocolVersion": "%s", "capabilities": {}, "clientInfo": { "name": "test", "version": "1" } } }"""
                        .formatted(requested), List.of("MCP-Protocol-Version", header)));
    }

    @Test
    void legacyToolsList() throws Exception {
        HttpResponse<String> response = post("{ \"jsonrpc\": \"2.0\", \"id\": 12, \"method\": \"tools/list\" }",
                List.of("MCP-Protocol-Version", "2025-11-25"));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = MAPPER.readTree(response.body()).get("result");
        assertEquals(List.of("tools"), List.copyOf(result.propertyNames()));
        assertEquals(List.of("getPet", "deletePet", "getPetPhoto", "createPet"),
                result.get("tools").valueStream().map(tool -> tool.get("name").stringValue()).toList());
    }

    @Test
    void legacyPing() throws Exception {
        assertResponse(200, "{ \"jsonrpc\": \"2.0\", \"id\": 14, \"result\": {} }",
                post("{ \"jsonrpc\": \"2.0\", \"id\": 14, \"method\": \"ping\" }", List.of("MCP-Protocol-Version", "2025-06-18")));
    }

    @Test
    void legacyToolsCall() throws Exception {
        HttpResponse<String> response = post("""
                { "jsonrpc": "2.0", "id": 13, "method": "tools/call",
                  "params": { "name": "getPet", "arguments": { "petId": "123" } } }""", List.of("MCP-Protocol-Version", "2025-06-18"));
        assertEquals("GET /pets/123", lastRequest.target());
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 13,
                  "result": { "content": [ { "type": "text", "text": "{\\"id\\":\\"123\\",\\"name\\":\\"Rex\\"}" } ], "isError": false } }""",
                response);
    }

    @Test
    void unknownTool() throws Exception {
        assertResponse(200, """
                { "jsonrpc": "2.0", "id": 10, "error": { "code": -32602, "message": "Unknown tool: adoptPet" } }""",
                send("""
                        { "jsonrpc": "2.0", "id": 10, "method": "tools/call",
                          "params": { "name": "adoptPet", "arguments": {} } }"""));
        assertFalse(lastAuditInfo.containsKey("toolName"));
    }

    @ParameterizedTest
    @MethodSource
    void methodNotAllowed(String method) throws Exception {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(mcpUri()).method(method, HttpRequest.BodyPublishers.noBody())
                .header("MCP-Protocol-Version", "2025-11-25").header("Mcp-Session-Id", "s-1").build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertEquals(List.of("POST"), response.headers().allValues("Allow"));
        assertEquals("", response.body());
    }

    static Stream<String> methodNotAllowed() {
        return Stream.of("GET", "DELETE");
    }

    // The MCP protocol checks, other than those covered by the examples above.
    static Stream<Arguments> protocolChecks() {
        String toolsList = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{\"_meta\":"
                + "{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        String getPet = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"getPet\",\"arguments\":ARGUMENTS,"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        return Stream.of(
                Arguments.of("body is not JSON", "{", List.of("Mcp-Method", "tools/list"), 400, -32700),
                Arguments.of("batch", "[" + toolsList + "]", List.of("Mcp-Method", "tools/list"), 400, -32600),
                Arguments.of("notification", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}", List.of(), 202, null),
                Arguments.of("no MCP-Protocol-Version: served as 2025-03-26", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                        List.of("MCP-Protocol-Version", ""), 200, null),
                Arguments.of("no _meta", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", List.of("Mcp-Method", "tools/list"), 400, -32602),
                Arguments.of("_meta version differs", toolsList.replace("\"2026-07-28\"", "\"2025-11-25\""), List.of("Mcp-Method", "tools/list"), 400, -32020),
                Arguments.of("Mcp-Method differs", toolsList, List.of("Mcp-Method", "tools/call"), 400, -32020),
                Arguments.of("unknown method", toolsList.replace("tools/list", "resources/list"), List.of("Mcp-Method", "resources/list"), 404, -32601),
                Arguments.of("initialize in 2026-07-28", toolsList.replace("tools/list", "initialize"), List.of("Mcp-Method", "initialize"), 404, -32601),
                Arguments.of("unknown legacy method", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/list\"}",
                        List.of("MCP-Protocol-Version", "2025-11-25"), 200, -32601),
                Arguments.of("ping in 2026-07-28", toolsList.replace("tools/list", "ping"), List.of("Mcp-Method", "ping"), 404, -32601),
                Arguments.of("non-string name", getPet.replace("\"getPet\"", "{}").replace("ARGUMENTS", "{}"),
                        List.of("Mcp-Method", "tools/call", "Mcp-Name", "getPet"), 200, -32602),
                Arguments.of("legacy non-string name", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":{}}}",
                        List.of("MCP-Protocol-Version", "2025-11-25"), 200, -32602),
                Arguments.of("no Mcp-Name", getPet.replace("ARGUMENTS", "{\"petId\":\"123\"}"), List.of("Mcp-Method", "tools/call"), 400, -32020),
                Arguments.of("arguments is not an object", getPet.replace("ARGUMENTS", "[]"), List.of("Mcp-Method", "tools/call", "Mcp-Name", "getPet"), 200, -32602));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void protocolChecks(String check, String body, List<String> headers, int status, Integer code) throws Exception {
        HttpResponse<String> response = post(body, headers);
        assertEquals(status, response.statusCode(), response.body());
        if (status == 202) {
            assertEquals("", response.body());
        } else if (code == null) {
            assertTrue(MAPPER.readTree(response.body()).has("result"), response.body());
        } else {
            assertEquals(code, MAPPER.readTree(response.body()).path("error").path("code").asInt(), response.body());
        }
    }

    // Sends an MCP request, adding params._meta and the headers that the Streamable HTTP transport
    // requires. The extra headers are name/value pairs that are added or replaced.
    private static HttpResponse<String> send(String message, String... extraHeaders) throws Exception {
        ObjectNode request = (ObjectNode) MAPPER.readTree(message);
        ObjectNode params = request.has("params") ? (ObjectNode) request.get("params") : request.putObject("params");
        params.putObject("_meta").put("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                .putObject("io.modelcontextprotocol/clientCapabilities");
        List<String> headers = new ArrayList<>(List.of("Mcp-Method", request.get("method").stringValue()));
        if (params.has("name")) headers.addAll(List.of("Mcp-Name", params.get("name").stringValue()));
        headers.addAll(List.of(extraHeaders));
        return post(MAPPER.writeValueAsString(request), headers);
    }

    // Posts a body with the standard MCP headers, plus the headers given as name/value pairs. An empty value removes a header.
    private static HttpResponse<String> post(String body, List<String> headers) throws Exception {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("Content-Type", "application/json");
        all.put("Accept", "application/json, text/event-stream");
        all.put("MCP-Protocol-Version", "2026-07-28");
        for (int i = 0; i < headers.size(); i += 2) all.put(headers.get(i), headers.get(i + 1));
        HttpRequest.Builder builder = HttpRequest.newBuilder(mcpUri()).POST(HttpRequest.BodyPublishers.ofString(body));
        all.forEach((name, value) -> {
            if (!value.isEmpty()) builder.setHeader(name, value);
        });
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertResponse(int status, String expected, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(List.of("application/json"), response.headers().allValues("Content-Type"));
        JsonNode actual = MAPPER.readTree(response.body());
        assertEquals(MAPPER.readTree(expected), actual, response.body());
    }

    private static URI mcpUri() {
        return URI.create("http://localhost:" + port(mcp) + "/mcp");
    }

    private static int port(Undertow server) {
        return ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }
}
