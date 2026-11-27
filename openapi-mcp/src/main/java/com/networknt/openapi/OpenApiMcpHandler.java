package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.oas.model.Info;
import com.networknt.utility.StringUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exposes the endpoints of an OpenAPI specification as MCP tools, over the Streamable HTTP transport. A tools/call
 * request is translated into a REST call to the backend API, and the REST response into an MCP tool result. See
 * openapi-mcp/README.md for its usage and limitations.
 * <p>
 * It serves MCP 2026-07-28 and the legacy, handshake-based versions 2025-03-26 to 2025-11-25, all without sessions.
 * The MCP-Protocol-Version header selects the version of each request.
 * <p>
 * This is the last handler in its chain: it answers every request itself. The tools are built from the spec when
 * the handler is constructed, and any endpoint that cannot be exposed fails startup.
 */
public class OpenApiMcpHandler implements LightHttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(OpenApiMcpHandler.class);
    static final String PROTOCOL_VERSION = "2026-07-28";
    // The legacy versions, which open with an initialize handshake, latest first.
    static final List<String> LEGACY_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");
    static final List<String> SUPPORTED_VERSIONS = List.of(PROTOCOL_VERSION, "2025-11-25", "2025-06-18", "2025-03-26");
    // Clients of versions before 2025-06-18 send no MCP-Protocol-Version header.
    private static final String NO_HEADER_VERSION = "2025-03-26";
    private static final String SPEC_NAME = "openapi";
    private static final String PROTOCOL_VERSION_META = "io.modelcontextprotocol/protocolVersion";
    private static final String CLIENT_CAPABILITIES_META = "io.modelcontextprotocol/clientCapabilities";
    private static final HttpString MCP_PROTOCOL_VERSION = new HttpString("MCP-Protocol-Version");
    private static final HttpString MCP_METHOD = new HttpString("Mcp-Method");
    private static final HttpString MCP_NAME = new HttpString("Mcp-Name");
    // The audit info key that AuditHandler records the called tool under, as for light-4j's McpHandler.
    private static final String TOOL_NAME_AUDIT_KEY = "toolName";
    // server/discover and tools/list are the same for every caller, so anyone may cache them for 5 minutes.
    private static final long TTL_MS = 300_000;
    // Numbers are read as BigDecimal and not normalized, so that the body argument keeps its exact numbers.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    // The exposed tools by name, in the order of the endpoints setting.
    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    // The server/discover and initialize serverInfo: the spec's info.title and info.version.
    private final Map<String, String> serverInfo;
    private final McpToolCaller caller;

    /**
     * An HTTP response to an MCP request.
     *
     * @param status the HTTP status code
     * @param body   the JSON-RPC response, or null for a response with no body
     */
    private record Reply(int status, Map<String, Object> body) { }

    /**
     * Loads openapi-mcp.yaml and the openapi.yaml spec, and builds the tools for the exposed endpoints.
     *
     * @throws com.networknt.config.ConfigException if an exposed endpoint cannot be exposed
     */
    public OpenApiMcpHandler() {
        OpenApiMcpConfig config = OpenApiMcpConfig.load();
        String downstreamHost = StringUtils.stripEnd(config.getDownstreamHost(), "/");
        Map<String, Object> spec = Config.getInstance().getJsonMapConfigNoCache(SPEC_NAME);
        // Parsed the same way OpenApiHandler parses it.
        OpenApiHelper helper = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(spec));
        for (McpTool tool : McpToolFactory.create(helper, config.getEndpoints())) tools.put(tool.name(), tool);
        caller = new McpToolCaller(downstreamHost, config.getMaxResponseBodySize(), MAPPER);

        Info info = helper.openApi3.getInfo();
        serverInfo = Map.of("name", Objects.requireNonNullElse(info.getTitle(), ""),
                "version", Objects.requireNonNullElse(info.getVersion(), ""));
        logger.info("OpenApiMcpHandler exposes {} to {} as MCP tools {}", config.getEndpoints(), downstreamHost, tools.keySet());
    }

    /**
     * Answers an MCP request with a single JSON-RPC response, or with 202 Accepted and no body for a notification.
     * Any method other than POST, e.g. the legacy GET stream or session DELETE, is answered with 405 Method Not
     * Allowed. A POST is dispatched to a worker thread first, because a tools/call blocks on the backend call.
     *
     * @param exchange the MCP request
     * @throws Exception if the request body cannot be read or the response cannot be sent
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (!Methods.POST.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().put(Headers.ALLOW, Methods.POST_STRING);
            exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
            return;
        }
        // The backend call blocks, so the request is handled on a worker thread.
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        exchange.startBlocking();
        Reply reply = reply(exchange, exchange.getInputStream().readAllBytes());
        exchange.setStatusCode(reply.status());
        if (reply.body() == null) {
            exchange.endExchange();
            return;
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(MAPPER.writeValueAsString(reply.body()));
    }

    /**
     * Checks an MCP request, then answers it. The checks run in order: JSON, JSON-RPC message, notification and
     * MCP-Protocol-Version header, then the checks of the request's version (see {@link #modern} and
     * {@link #legacy}). The first check that fails decides the reply. A failure in answering the method is replied
     * with 500 Internal error.
     *
     * @param exchange the MCP request, whose MCP headers are checked
     * @param body     the request body
     * @return the reply
     */
    private Reply reply(HttpServerExchange exchange, byte[] body) {
        JsonNode message;
        try {
            message = MAPPER.readTree(body);
        } catch (JacksonException e) {
            return error(400, null, -32700, "Parse error");
        }
        JsonNode id = message.get("id");
        boolean validId = id != null && (id.isString() || id.isIntegralNumber());
        if (!message.isObject() || !"2.0".equals(message.path("jsonrpc").stringValue(null)) || !message.path("method").isString()
                || id != null && !validId) {
            return error(400, validId ? id : null, -32600, "Invalid Request");
        }
        if (id == null) return new Reply(StatusCodes.ACCEPTED, null);

        String version = Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst(MCP_PROTOCOL_VERSION), NO_HEADER_VERSION);
        if (!SUPPORTED_VERSIONS.contains(version)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supported", SUPPORTED_VERSIONS);
            data.put("requested", version);
            return error(400, id, -32022, "Unsupported protocol version", data);
        }
        String method = message.get("method").stringValue();
        JsonNode params = message.path("params");
        try {
            return version.equals(PROTOCOL_VERSION) ? modern(exchange, id, method, params) : legacy(exchange, id, method, params);
        } catch (RuntimeException e) {
            logger.error("openapi-mcp failed to answer {}", method, e);
            return error(500, id, -32603, "Internal error");
        }
    }

    /**
     * Answers a 2026-07-28 request. The request metadata in params._meta must be present and match the
     * MCP-Protocol-Version header, and the Mcp-Method header must match the method. Results have resultType
     * complete, and an unknown method is replied with 404.
     *
     * @param exchange the MCP request
     * @param id       the JSON-RPC request id
     * @param method   the JSON-RPC method
     * @param params   the request params
     * @return the reply
     */
    private Reply modern(HttpServerExchange exchange, JsonNode id, String method, JsonNode params) {
        JsonNode meta = params.path("_meta");
        if (!meta.has(PROTOCOL_VERSION_META) || !meta.has(CLIENT_CAPABILITIES_META)) {
            return error(400, id, -32602, "Invalid params: _meta must include " + PROTOCOL_VERSION_META + " and " + CLIENT_CAPABILITIES_META);
        }
        if (!PROTOCOL_VERSION.equals(meta.path(PROTOCOL_VERSION_META).stringValue(null))) {
            return error(400, id, -32020, "Header mismatch: MCP-Protocol-Version header does not match " + PROTOCOL_VERSION_META);
        }
        if (!method.equals(exchange.getRequestHeaders().getFirst(MCP_METHOD))) {
            return error(400, id, -32020, "Header mismatch: Mcp-Method header does not match method " + method);
        }
        return switch (method) {
            case "server/discover" -> result(id, Map.of("resultType", "complete",
                    "supportedVersions", SUPPORTED_VERSIONS,
                    "capabilities", Map.of("tools", Map.of()),
                    "_meta", Map.of("io.modelcontextprotocol/serverInfo", serverInfo),
                    "ttlMs", TTL_MS, "cacheScope", "public"));
            case "tools/list" -> result(id, Map.of("resultType", "complete", "tools", toolDefinitions(),
                    "ttlMs", TTL_MS, "cacheScope", "public"));
            case "tools/call" -> callTool(exchange, id, params, true);
            default -> error(404, id, -32601, "Method not found: " + method);
        };
    }

    /**
     * Answers a request of a legacy version (2025-03-26 to 2025-11-25), without a session. initialize negotiates
     * the version, and other requests are answered statelessly, whether or not the client initialized. ping gets
     * the empty result these versions require. An unknown method is replied with 200, because in these versions a
     * 404 tells the client that its session has ended.
     *
     * @param exchange the MCP request
     * @param id       the JSON-RPC request id
     * @param method   the JSON-RPC method
     * @param params   the request params
     * @return the reply
     */
    private Reply legacy(HttpServerExchange exchange, JsonNode id, String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> {
                // The client's version if it is supported, otherwise the latest legacy version.
                String requested = params.path("protocolVersion").stringValue(null);
                yield result(id, Map.of("protocolVersion", LEGACY_VERSIONS.contains(requested) ? requested : LEGACY_VERSIONS.getFirst(),
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", serverInfo));
            }
            case "tools/list" -> result(id, Map.of("tools", toolDefinitions()));
            case "tools/call" -> callTool(exchange, id, params, false);
            case "ping" -> result(id, Map.of());
            default -> error(200, id, -32601, "Method not found: " + method);
        };
    }

    private List<Map<String, Object>> toolDefinitions() {
        return tools.values().stream().map(McpTool::definition).toList();
    }

    /**
     * The exchange's audit info, which AuditHandler adds to its record for the keys listed in audit.yml. Earlier
     * handlers in the chain (e.g. JwtVerifyHandler) may have created it already.
     *
     * @param exchange the MCP request
     * @return the audit info
     */
    private static Map<String, Object> auditInfo(HttpServerExchange exchange) {
        Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
        if (auditInfo == null) {
            auditInfo = new HashMap<>();
            exchange.putAttachment(AttachmentConstants.AUDIT_INFO, auditInfo);
        }
        return auditInfo;
    }

    /**
     * Answers a tools/call request by calling the backend for the tool. The tool name must be a string, and in
     * 2026-07-28 the Mcp-Name header must match it. The tool must exist, and the arguments, if present, must be an
     * object. The tool name is added to the exchange's audit info, so that AuditHandler can record it.
     *
     * @param exchange the MCP request, whose headers are forwarded to the backend
     * @param id       the JSON-RPC request id
     * @param params   the request params, with the tool name and arguments
     * @param modern   whether the request is 2026-07-28, whose results have resultType complete
     * @return the reply, with the tool result or a JSON-RPC error
     */
    private Reply callTool(HttpServerExchange exchange, JsonNode id, JsonNode params, boolean modern) {
        if (!params.path("name").isString()) return error(200, id, -32602, "Invalid params: name must be a string");
        String name = params.get("name").stringValue();
        if (modern && !name.equals(exchange.getRequestHeaders().getFirst(MCP_NAME))) {
            return error(400, id, -32020, "Header mismatch: Mcp-Name header does not match params.name " + name);
        }
        McpTool tool = tools.get(name);
        if (tool == null) return error(200, id, -32602, "Unknown tool: " + name);
        auditInfo(exchange).put(TOOL_NAME_AUDIT_KEY, name);
        JsonNode arguments = params.path("arguments");
        if (!arguments.isMissingNode() && !arguments.isObject()) {
            return error(200, id, -32602, "Invalid params: arguments must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (modern) result.put("resultType", "complete");
        result.putAll(caller.call(tool, arguments, exchange));
        return result(id, result);
    }

    /**
     * A 200 reply with a JSON-RPC result.
     *
     * @param id     the JSON-RPC request id
     * @param result the result
     * @return the reply
     */
    private static Reply result(JsonNode id, Map<String, Object> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return new Reply(StatusCodes.OK, body);
    }

    /**
     * A reply with a JSON-RPC error that has no data.
     *
     * @param status  the HTTP status code
     * @param id      the JSON-RPC request id, or null if it could not be read
     * @param code    the JSON-RPC error code
     * @param message the error message
     * @return the reply
     */
    private static Reply error(int status, JsonNode id, int code, String message) {
        return error(status, id, code, message, null);
    }

    /**
     * A reply with a JSON-RPC error.
     *
     * @param status  the HTTP status code
     * @param id      the JSON-RPC request id, or null if it could not be read
     * @param code    the JSON-RPC error code
     * @param message the error message
     * @param data    the error data, or null for none
     * @return the reply
     */
    private static Reply error(int status, JsonNode id, int code, String message, Object data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) error.put("data", data);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", error);
        return new Reply(status, body);
    }
}
