# openapi-mcp

The openapi-mcp handler exposes the endpoints of an OpenAPI specification as [MCP](https://modelcontextprotocol.io) tools. It lets an MCP client, such as an AI agent, call an existing REST API through a light-4j server, without changes to the API.

The handler serves an MCP endpoint (e.g. `POST /mcp`):
- `tools/list` returns the configured endpoints as tools. Each tool's name is the endpoint's `operationId`, and its input schema is built from the endpoint's parameters and request body. It is answered from the spec; the backend is not called.
- `tools/call` is translated into a REST call to the backend API, and the REST response is returned as the MCP tool result.

The handler only translates between MCP and REST. Authentication, token exchange, `Origin` checks and input validation are left to the rest of the handler chain and to the backend.

## Usage
### Dependency
```xml
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>openapi-mcp</artifactId>
    <version>${version.light-rest-4j}</version>
</dependency>
```

### Specification
The handler reads the same `openapi.yaml` (or `openapi.yml` / `openapi.json`) as the OpenApiHandler. It must describe the backend API, and be a single, resolved OpenAPI 3.0 spec (every `$ref` points into the spec itself). Every schema `$ref` must be to a component schema, `#/components/schemas/<name>`.

### Configuration
`openapi-mcp.yaml` has three settings, usually set in `values.yml`:

| Setting | Default | Description |
| --- | --- | --- |
| `downstreamHost` | (required) | Base URL of the backend API, an absolute `http` or `https` URL without a query, fragment or user info, e.g. `https://pets.example.com`. It may include a path prefix. |
| `endpoints` | (required) | Endpoints exposed as tools, as `{path}@{method}`. The path includes the base path of the spec's first `servers` URL. Tools are listed in this order. |
| `maxResponseBodySize` | `1048576` (1 MiB) | Maximum size of a backend response body, in bytes, from 1 to 2147483646. A larger body is replaced by an error message. |

TLS and the backend timeouts come from `client.yml` (`tls`, `request.connectTimeout` and `request.timeout`). `request.timeout` covers the whole backend call, including reading the response body.

```yaml
# values.yml
openapi-mcp.downstreamHost: https://pets.example.com
openapi-mcp.endpoints:
  - /v1/pets@get
  - /v1/pets@post
  - /v1/pets/{petId}@get
```

### Handler chain
Register the handler in `handler.yml` as the last handler of the MCP endpoint's chain. Route `POST` to it, and also `GET` and `DELETE`, which it answers with `405 Method Not Allowed`, as the MCP spec recommends for servers without a `GET` stream or sessions (older MCP clients may send them):

```yaml
handler.handlers:
  - com.networknt.exception.ExceptionHandler@exception
  - com.networknt.correlation.CorrelationHandler@correlation
  - com.networknt.cors.CorsHttpHandler@cors
  - com.networknt.openapi.JwtVerifyHandler@security
  - com.networknt.audit.AuditHandler@audit
  - com.networknt.openapi.OpenApiMcpHandler@openapiMcp

handler.chains:
  mcp:
    - exception
    - correlation
    - cors
    - security
    - audit
    - openapiMcp

handler.paths:
  - path: '/mcp'
    method: 'POST'
    exec:
      - mcp
  - path: '/mcp'
    method: 'GET'
    exec:
      - mcp
  - path: '/mcp'
    method: 'DELETE'
    exec:
      - mcp
```

- `/mcp` is not in the spec, so do not put OpenApiHandler or ValidatorHandler in the chain. With JwtVerifyHandler, set `skipVerifyScopeWithoutSpec: true` (or `enableVerifyScope: false`) in `security.yml`.
- Do not put BodyHandler in the chain. The handler reads the request body itself.
- The CORS handler rejects requests with an invalid `Origin`, as MCP requires.
- The headers of the MCP request are forwarded to the backend the way ProxyHandler forwards them, including `Authorization`, so the backend receives the token that reaches openapi-mcp. If the backend needs a different token than the MCP client sends (e.g. one from another issuer or for another audience), put a handler that replaces it, such as TokenExchangeHandler, earlier in the chain.
- For each `tools/call`, the handler adds the tool name to the request's audit info, so AuditHandler records which tool was called. Add `toolName` to the `audit` list in `audit.yml`:

  ```yaml
  # values.yml
  audit.audit: ["client_id", "user_id", "scope_client_id", "endpoint", "serviceId", "toolName"]
  # AuditHandler records only error statuses unless this is false.
  audit.auditOnError: false
  ```

  Keep `audit.statusCode` or `audit.responseTime` on (both are by default): with both off, AuditHandler writes its record before openapi-mcp runs, so the record has no tool name. AuditHandler's `statusCode` is the status of the MCP response, which is 200 even when the backend call failed.

## Limitations
- **MCP versions:** `2026-07-28`, and the earlier `2025-11-25`, `2025-06-18` and `2025-03-26` (with the `initialize` handshake), over the Streamable HTTP transport. The handler is stateless in every version: it issues no session ID, offers no `GET` stream, and never responds with SSE. JSON-RPC batches and `2024-11-05` (HTTP+SSE) are not supported.
- **MCP methods:** `server/discover` (`2026-07-28`) or `initialize` and `ping` (earlier versions), plus `tools/list` and `tools/call`. Resources, prompts, subscriptions, tasks, pagination, `outputSchema` and `structuredContent` are not supported.
- **Request bodies:** only `application/json`. The tool argument `body` is forwarded as JSON.
- **Parameters:** path, query and header parameters, serialized only the OpenAPI default way: a parameter with a different `style` or `explode`, or with `content` instead of a schema, fails startup. Cookie parameters are not supported, and a parameter may not be named `body`. Header parameters named `Accept`, `Content-Type` or `Authorization` are ignored, as OpenAPI requires, so a tool call cannot replace the forwarded token. So are the hop-by-hop headers `Transfer-Encoding`, `TE`, `Keep-Alive`, `Trailer` and `Proxy-Connection`, which describe the connection to the backend. Header parameters for headers that the handler sets itself (`Accept-Encoding`, `X-Forwarded-*`) or that the HTTP client cannot send (`Host`, `Connection`, `Content-Length`, `Expect`, `Upgrade`) fail startup. An empty path argument, or one with a `.` or `..` piece between `/` or `\` characters (e.g. `..`, `../admin` or `a/../b`), is refused with `isError: true`, so that it cannot reach a path outside the endpoint. A number argument is written out in full (e.g. `1e3` becomes `1000`), so one with more than 1000 digits written out (e.g. `1e999999999`) is refused with `isError: true` too.
- **Input schemas:** parameter and request body schemas are copied as written, with referenced component schemas copied into `$defs`. OpenAPI 3.0-specific keywords (e.g. `nullable`, or a boolean `exclusiveMinimum`) are not converted to JSON Schema 2020-12, the default dialect of MCP tool schemas. openapi-parser drops the keys next to a `$ref`, so a schema with a `$ref` keeps only the `$ref`.
- **Response bodies:**

  | Backend Content-Type | Tool result content |
  | --- | --- |
  | `text/*`, `application/json`, `application/xml`, `*+json`, `*+xml` | `text`, decoded as UTF-8 |
  | `image/*` | base64 `image` |
  | `audio/*` | base64 `audio` |
  | Anything else, or none (including other binary types and multipart) | Not supported: an error message, with `isError: true` |

- **Errors:** a non-2xx backend status, a body over `maxResponseBodySize`, and connection or timeout failures are returned as a tool result with `isError: true`. Redirects are not followed.
- **Validation:** tool arguments are not validated against the input schema; the backend is expected to reject invalid input.
- **Startup:** the server fails to start if `downstreamHost` or `maxResponseBodySize` is invalid, or a configured endpoint is not in the spec, has no `operationId` or one that is not a valid MCP tool name (1 to 128 characters of `A-Z`, `a-z`, `0-9`, `_`, `-` and `.`), shares its `operationId` with another, or cannot be exposed (non-JSON request body, cookie parameter, a parameter named `body`, two parameters with the same name, a header parameter the handler cannot send, a non-default parameter serialization, a `{name}` in the path with no path parameter, or a schema `$ref` that is not to a component schema).
- **Other:** a single spec and a fixed `downstreamHost` (no service discovery). Every caller sees every tool. Config and spec changes need a restart.
