package com.networknt.openapi;

import com.networknt.client.ClientConfig;
import com.networknt.client.Http2Client;
import com.networknt.client.RequestConfig;
import com.networknt.config.ConfigException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Calls the backend API for a tools/call request, and maps the backend response to an MCP tool result.
 */
final class McpToolCaller {
    // Inbound headers that describe the MCP request rather than the backend request.
    private static final Set<String> NOT_FORWARDED = Set.of("host", "content-type", "content-length", "expect", "accept",
            "accept-encoding", "connection", "keep-alive", "te", "transfer-encoding", "trailer", "proxy-connection", "upgrade");
    // The most digits a number argument may have when written out in full, as Jackson's default maxNumberLength.
    private static final int MAX_NUMBER_DIGITS = 1000;
    private static final ScheduledThreadPoolExecutor BODY_READ_WATCHDOG = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "openapi-mcp-body-read-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    static {
        BODY_READ_WATCHDOG.setRemoveOnCancelPolicy(true);
    }

    private final HttpClient client;
    // The base URL of the backend, without a trailing /.
    private final String downstreamHost;
    // request.timeout from client.yml.
    private final Duration timeout;
    private final int maxResponseBodySize;
    // Serializes the body argument without losing numeric precision.
    private final ObjectMapper mapper;

    /**
     * Creates the HTTP/1.1 client for the backend, with the connect timeout, request timeout and TLS context from
     * client.yml, the way light-4j's ExternalServiceHandler sets up its client.
     *
     * @param downstreamHost      the base URL of the backend, without a trailing /
     * @param maxResponseBodySize the maximum size of a backend response body, in bytes
     * @param mapper              the mapper that the body argument is serialized with
     * @throws ConfigException if the TLS context cannot be created from client.yml
     */
    McpToolCaller(String downstreamHost, int maxResponseBodySize, ObjectMapper mapper) {
        RequestConfig request = ClientConfig.get().getRequest();
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(request.getConnectTimeout()));
        if (downstreamHost.regionMatches(true, 0, "https:", 0, 6)) {
            String tlsError = "openapi-mcp requires an initialized TLS context for HTTPS. "
                    + "Enable tls.loadTrustStore and configure tls.trustStore or "
                    + "tls.loadDefaultTrustStore in client.yml.";
            try {
                SSLContext context = Http2Client.createSSLContext();
                if (context == null) {
                    throw new ConfigException(tlsError);
                }
                context.getDefaultSSLParameters(); // Fails if the context has not been initialized.
                builder.sslContext(context);
            } catch (IOException | IllegalStateException e) {
                ConfigException failure = new ConfigException(tlsError);
                failure.initCause(e);
                throw failure;
            }
        }
        this.client = builder.build();
        this.downstreamHost = downstreamHost;
        this.timeout = Duration.ofMillis(request.getTimeout());
        this.maxResponseBodySize = maxResponseBodySize;
        this.mapper = mapper;
    }

    /**
     * Calls the backend for a tool. Problems with the arguments, the backend response or the connection are returned
     * as a tool result with isError true.
     *
     * @param tool      the tool
     * @param arguments the tool arguments
     * @param exchange  the MCP request, whose headers are forwarded
     * @return the tool result
     */
    Map<String, Object> call(McpTool tool, JsonNode arguments, HttpServerExchange exchange) {
        HttpRequest request;
        try {
            request = request(tool, arguments, exchange);
        } catch (IllegalArgumentException e) {
            return result(true, List.of(text(e.getMessage())));
        }
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream in = response.body()) {
                body = readBody(in, deadline);
            }
            return result(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null), body);
        } catch (HttpTimeoutException e) {
            return failure("timed out");
        } catch (IOException e) {
            return failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure("interrupted");
        }
    }

    /**
     * Reads a response body, up to one byte over the size limit, before the deadline of the request timeout. The
     * JDK 25 HttpClient stops its request timeout once the response headers arrive, so a backend that stalls after
     * them would block the read, and the worker thread, with no end. At the deadline, the stream is closed, which
     * ends a read that is still blocked.
     *
     * @param in       the response body
     * @param deadline the deadline, in System.nanoTime() terms
     * @return the body, read up to one byte over the size limit
     * @throws HttpTimeoutException if the deadline passed before the body was read
     * @throws IOException          if the body cannot be read
     */
    private byte[] readBody(InputStream in, long deadline) throws IOException {
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> watchdog = BODY_READ_WATCHDOG.schedule(() -> {
            timedOut.set(true);
            try {
                in.close();
            } catch (IOException ignored) {
                // The read fails either way.
            }
        }, Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        try {
            return in.readNBytes(maxResponseBodySize + 1);
        } catch (IOException e) {
            // On JDK 26 and later, the HttpClient's own timeout can close the stream first.
            if (timedOut.get() || e.getCause() instanceof HttpTimeoutException) throw new HttpTimeoutException("request timed out");
            throw e;
        } finally {
            watchdog.cancel(false);
        }
    }

    /**
     * Builds the backend request for a tool call: the forwarded headers, the path, query and header arguments,
     * Accept-Encoding: identity, and the body argument as JSON if the tool has a request body.
     *
     * @param tool      the tool
     * @param arguments the tool arguments
     * @param exchange  the MCP request, whose headers are forwarded
     * @return the backend request
     * @throws IllegalArgumentException if the request cannot be built from the arguments, e.g. a path argument is
     *                                  missing or a header argument is not a valid header value
     */
    private HttpRequest request(McpTool tool, JsonNode arguments, HttpServerExchange exchange) {
        Map<String, List<String>> headers = forwardedHeaders(exchange);
        headers.put("Accept-Encoding", List.of("identity"));
        String target = target(tool, arguments, headers);
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();
        JsonNode bodyArgument = arguments.get("body");
        if (tool.hasBody() && bodyArgument != null && !bodyArgument.isNull()) {
            body = HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(bodyArgument));
            headers.put("Content-Type", List.of("application/json"));
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(downstreamHost + target))
                .timeout(timeout)
                .method(tool.method(), body);
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.build();
    }

    /**
     * Maps the parameter arguments: path arguments fill the path template, query arguments are appended to it,
     * and header arguments are put in headers.
     *
     * @param tool      the tool
     * @param arguments the tool arguments
     * @param headers   the backend request headers
     * @return the path and query of the backend request
     * @throws IllegalArgumentException if a path argument is missing, empty, or has a . or .. piece, or an argument has
     *                                  an unsupported value
     */
    static String target(McpTool tool, JsonNode arguments, Map<String, List<String>> headers) {
        String path = tool.path();
        StringBuilder query = new StringBuilder();
        for (McpTool.Parameter parameter : tool.parameters()) {
            String name = parameter.name();
            JsonNode value = arguments.get(name);
            if (value == null || value.isNull()) {
                if (parameter.in().equals("path")) throw new IllegalArgumentException("Missing required path parameter: " + name);
                continue;
            }
            List<String> values = values(name, value);
            switch (parameter.in()) {
                case "path" -> path = path.replace("{" + name + "}", pathSegment(name, values));
                case "query" -> values.forEach(item -> query.append(query.isEmpty() ? '?' : '&').append(encode(name)).append('=').append(encode(item)));
                default -> headers.put(name, List.of(String.join(",", values)));
            }
        }
        return path + query;
    }

    /**
     * The encoded value of a path argument. An empty value is refused, and so is a value with a . or .. piece between
     * / or \ characters, or at either end. An empty value would leave an empty segment, which a backend that
     * collapses // or matches a trailing / maps to another endpoint. A . or .. piece is a dot segment, which a backend
     * that normalizes paths removes, calling a path outside the exposed endpoint: dots are not percent-encoded, and a
     * layer that decodes %2F or %5C before normalizing turns ../x into one too. A backend can also strip
     * semicolon-delimited path parameters before normalizing, so only the part before the first semicolon is checked.
     *
     * @param name   the parameter name, used in the error message
     * @param values the argument's values
     * @return the comma-separated, percent-encoded values
     * @throws IllegalArgumentException if the value is empty, or has a . or .. piece
     */
    private static String pathSegment(String name, List<String> values) {
        String value = String.join(",", values);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Invalid value for path parameter " + name + ": an empty value is not allowed");
        }
        for (String piece : value.split("[/\\\\]", -1)) {
            int parameterStart = piece.indexOf(';');
            String segment = parameterStart < 0 ? piece : piece.substring(0, parameterStart);
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid value for path parameter " + name + ": " + value
                        + " is not allowed, because " + piece + " would be a dot segment");
            }
        }
        return values.stream().map(McpToolCaller::encode).collect(Collectors.joining(","));
    }

    /**
     * The string values of a parameter argument. Strings are used as-is, numbers keep their full precision, and
     * booleans become true or false. An array gives one value per item.
     *
     * @param name  the parameter name, used in the error message
     * @param value the argument: a string, number or boolean, or an array of them
     * @return the values
     * @throws IllegalArgumentException if the argument is an object, an array of anything else, or a number with
     *                                  more than 1000 digits when written out in full
     */
    private static List<String> values(String name, JsonNode value) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : value.isArray() ? value.values() : List.of(value)) {
            if (item.isString()) values.add(item.stringValue());
            else if (item.isNumber()) values.add(plainNumber(name, item.decimalValue()));
            else if (item.isBoolean()) values.add(String.valueOf(item.booleanValue()));
            else throw new IllegalArgumentException("Unsupported value for parameter " + name
                        + ": only strings, numbers, booleans and arrays of them are supported");
        }
        return values;
    }

    /**
     * A number written out in full, without an exponent. toPlainString writes about precision + |scale| digits, so a
     * short argument such as 1e999999999 would be a billion digits; the limit is that of Jackson's number length.
     *
     * @param name   the parameter name, used in the error message
     * @param number the number
     * @return the number's digits
     * @throws IllegalArgumentException if the number has more than 1000 digits when written out in full
     */
    private static String plainNumber(String name, BigDecimal number) {
        if ((long) number.precision() + Math.abs((long) number.scale()) > MAX_NUMBER_DIGITS) {
            throw new IllegalArgumentException("Unsupported value for parameter " + name
                    + ": a number may have at most " + MAX_NUMBER_DIGITS + " digits when written out in full");
        }
        return number.toPlainString();
    }

    /**
     * RFC 3986 percent-encoding of everything except unreserved characters, so that a / in a path argument does not
     * add a path segment. URLEncoder does form encoding, which differs only in writing a space as +, leaving * as is
     * and encoding ~.
     *
     * @param value the value
     * @return the encoded value
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }


    /**
     * The inbound headers, forwarded the way ProxyHandler (light-4j proxy-handler) forwards them with its defaults
     * rewriteHostHeader: true and reuseXForwarded: false. Headers that describe the MCP request rather than the
     * backend request are left out, the X-Forwarded-* headers are set, and service_id and service_url are removed.
     * Replace with ProxyHandler's own code once light-4j makes it public.
     *
     * @param exchange the MCP request
     * @return the headers by name, case-insensitively
     */
    private static Map<String, List<String>> forwardedHeaders(HttpServerExchange exchange) {
        HeaderMap inbound = exchange.getRequestHeaders();
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (HeaderValues values : inbound) {
            String name = values.getHeaderName().toString();
            String lowerCase = name.toLowerCase(Locale.ROOT);
            if (!NOT_FORWARDED.contains(lowerCase) && !lowerCase.startsWith("mcp-")) headers.put(name, new ArrayList<>(values));
        }
        InetSocketAddress source = exchange.getSourceAddress();
        headers.put("X-Forwarded-For", List.of(source == null ? "localhost" : source.getHostString()));
        headers.put("X-Forwarded-Proto", List.of("https".equals(exchange.getRequestScheme()) ? "https" : "http"));
        // ProxyHandler sets X-Forwarded-Host to the Host header, then to the host name if the request has no X-Forwarded-Host.
        String forwardedHost = inbound.contains(Headers.X_FORWARDED_HOST) ? inbound.getFirst(Headers.HOST)
                : NetworkUtils.formatPossibleIpv6Address(exchange.getHostName());
        if (forwardedHost != null) headers.put("X-Forwarded-Host", List.of(forwardedHost));
        headers.put("X-Forwarded-Port", List.of(String.valueOf(exchange.getHostPort())));
        headers.put("X-Forwarded-Server", List.of(exchange.getHostName()));
        headers.remove("service_id");
        headers.remove("service_url");
        return headers;
    }

    /**
     * Maps a backend response to a tool result.
     *
     * @param status      the HTTP status code
     * @param contentType the Content-Type header, or null
     * @param body        the body, read up to one byte over the size limit
     * @return the tool result
     */
    Map<String, Object> result(int status, String contentType, byte[] body) {
        boolean isError = status < 200 || status > 299;
        List<Map<String, Object>> content = new ArrayList<>();
        if (isError) {
            String reason = StatusCodes.getReason(status);
            content.add(text("Unknown".equals(reason) ? "HTTP " + status : "HTTP " + status + " " + reason));
        }
        if (body.length > maxResponseBodySize) {
            content.add(text("Response body exceeds the " + maxResponseBodySize + " byte limit"));
            isError = true;
        } else if (body.length > 0) {
            Map<String, Object> item = item(contentType, body);
            if (item == null) {
                content.add(text("Unsupported response Content-Type: " + (contentType == null ? "none" : contentType)));
                isError = true;
            } else {
                content.add(item);
            }
        }
        return result(isError, content);
    }

    /**
     * The content item for a non-empty response body. Textual types are a text item decoded as UTF-8; image/* and
     * audio/* are a base64 image or audio item.
     *
     * @param contentType the Content-Type header, or null
     * @param body        the body
     * @return the content item, or null if the Content-Type is missing or not supported
     */
    private static Map<String, Object> item(String contentType, byte[] body) {
        if (contentType == null) return null;
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mediaType.startsWith("text/") || mediaType.equals("application/json") || mediaType.equals("application/xml")
                || mediaType.endsWith("+json") || mediaType.endsWith("+xml")) {
            return text(new String(body, StandardCharsets.UTF_8));
        }
        if (mediaType.startsWith("image/") || mediaType.startsWith("audio/")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", mediaType.substring(0, mediaType.indexOf('/')));
            item.put("data", Base64.getEncoder().encodeToString(body));
            item.put("mimeType", mediaType);
            return item;
        }
        return null;
    }

    /**
     * The tool result for a backend request that failed without a response, e.g. a connection failure or timeout.
     *
     * @param reason why the request failed
     * @return the tool result, with isError true
     */
    private static Map<String, Object> failure(String reason) {
        return result(true, List.of(text("Backend request failed: " + reason)));
    }

    /**
     * A tool result, without the resultType that only 2026-07-28 results have.
     *
     * @param isError whether the result is an error
     * @param content the content items
     * @return the tool result
     */
    private static Map<String, Object> result(boolean isError, List<Map<String, Object>> content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("isError", isError);
        return result;
    }

    /**
     * A text content item.
     *
     * @param text the text
     * @return the content item
     */
    private static Map<String, Object> text(String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "text");
        item.put("text", text);
        return item;
    }
}
