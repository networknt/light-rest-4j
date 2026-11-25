package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import com.networknt.oas.model.Info;
import com.networknt.utility.StringUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exposes the endpoints of an OpenAPI specification as MCP tools, over the Streamable HTTP transport of MCP version
 * 2026-07-28. A tools/call request is translated into a REST call to the backend API, and the REST response into an
 * MCP tool result. See openapi-mcp/README.md for its usage and limitations.
 * <p>
 * This is the last handler in its chain: it answers every request itself. The tools are built from the spec when
 * the handler is constructed, and any endpoint that cannot be exposed fails startup.
 */
public class OpenApiMcpHandler implements LightHttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(OpenApiMcpHandler.class);
    static final String PROTOCOL_VERSION = "2026-07-28";
    private static final String SPEC_NAME = "openapi";
    private static final String PROTOCOL_VERSION_META = "io.modelcontextprotocol/protocolVersion";
    private static final String CLIENT_CAPABILITIES_META = "io.modelcontextprotocol/clientCapabilities";
    private static final HttpString MCP_PROTOCOL_VERSION = new HttpString("MCP-Protocol-Version");
    private static final HttpString MCP_METHOD = new HttpString("Mcp-Method");
    private static final HttpString MCP_NAME = new HttpString("Mcp-Name");
    // server/discover and tools/list are the same for every caller, so anyone may cache them for 5 minutes.
    private static final long TTL_MS = 300_000;
    // Numbers are read as BigDecimal and not normalized, so that the body argument keeps its exact numbers.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    // The exposed tools by name, in the order of the endpoints setting.
    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    // The server/discover serverInfo: the spec's info.title and info.version.
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
     * The request is dispatched to a worker thread first, because a tools/call blocks on the backend call.
     *
     * @param exchange the MCP request
     * @throws Exception if the request body cannot be read or the response cannot be sent
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // The backend call blocks, so the request is handled on a worker thread.
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }
        // TODO 'BlockingHttpExchange' used without 'try'-with-resources statement
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
     * Checks an MCP request, then answers it. The checks run in order: JSON, JSON-RPC message, notification,
     * MCP-Protocol-Version header, _meta, Mcp-Method header, then method. The first
     * check that fails decides the reply. A failure in answering the method is replied with 500 Internal error.
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

        HeaderMap headers = exchange.getRequestHeaders();
        String version = headers.getFirst(MCP_PROTOCOL_VERSION);
        if (version == null) {
            return error(400, id, -32020, "Missing MCP-Protocol-Version header. Supported versions: " + PROTOCOL_VERSION);
        }
        if (!version.equals(PROTOCOL_VERSION)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supported", List.of(PROTOCOL_VERSION));
            data.put("requested", version);
            return error(400, id, -32022, "Unsupported protocol version", data);
        }
        JsonNode params = message.path("params");
        JsonNode meta = params.path("_meta");
        if (!meta.has(PROTOCOL_VERSION_META) || !meta.has(CLIENT_CAPABILITIES_META)) {
            return error(400, id, -32602, "Invalid params: _meta must include " + PROTOCOL_VERSION_META + " and " + CLIENT_CAPABILITIES_META);
        }
        if (!version.equals(meta.path(PROTOCOL_VERSION_META).stringValue(null))) {
            return error(400, id, -32020, "Header mismatch: MCP-Protocol-Version header does not match " + PROTOCOL_VERSION_META);
        }
        String method = message.get("method").stringValue();
        if (!method.equals(headers.getFirst(MCP_METHOD))) {
            return error(400, id, -32020, "Header mismatch: Mcp-Method header does not match method " + method);
        }
        try {
            return switch (method) {
                case "server/discover" -> result(id, Map.of("resultType", "complete",
                        "supportedVersions", List.of(PROTOCOL_VERSION),
                        "capabilities", Map.of("tools", Map.of()),
                        "_meta", Map.of("io.modelcontextprotocol/serverInfo", serverInfo),
                        "ttlMs", TTL_MS, "cacheScope", "public"));
                case "tools/list" -> result(id, Map.of("resultType", "complete",
                        "tools", tools.values().stream().map(McpTool::definition).toList(),
                        "ttlMs", TTL_MS, "cacheScope", "public"));
                case "tools/call" -> callTool(exchange, id, params);
                default -> error(404, id, -32601, "Method not found: " + method);
            };
        } catch (RuntimeException e) {
            logger.error("openapi-mcp failed to answer {}", method, e);
            return error(500, id, -32603, "Internal error");
        }
    }

    /**
     * Answers a tools/call request by calling the backend for the tool. The Mcp-Name header must match the tool
     * name, the tool must exist, and the arguments, if present, must be an object.
     *
     * @param exchange the MCP request, whose headers are forwarded to the backend
     * @param id       the JSON-RPC request id
     * @param params   the request params, with the tool name and arguments
     * @return the reply, with the tool result or a JSON-RPC error
     */
    private Reply callTool(HttpServerExchange exchange, JsonNode id, JsonNode params) {
        String name = params.path("name").asString();
        if (!name.equals(exchange.getRequestHeaders().getFirst(MCP_NAME))) {
            return error(400, id, -32020, "Header mismatch: Mcp-Name header does not match params.name " + name);
        }
        McpTool tool = tools.get(name);
        if (tool == null) return error(200, id, -32602, "Unknown tool: " + name);
        JsonNode arguments = params.path("arguments");
        if (!arguments.isMissingNode() && !arguments.isObject()) {
            return error(200, id, -32602, "Invalid params: arguments must be an object");
        }
        return result(id, caller.call(tool, arguments, exchange));
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
