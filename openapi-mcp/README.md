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
The handler reads the same `openapi.yaml` (or `openapi.yml` / `openapi.json`) as the OpenApiHandler. It must describe the backend API, and be a single, resolved OpenAPI 3.0 spec (every `$ref` points into the spec itself).

### Configuration
`openapi-mcp.yaml` has three settings, usually set in `values.yml`:

| Setting | Default | Description |
| --- | --- | --- |
| `downstreamHost` | (required) | Base URL of the backend API, e.g. `https://pets.example.com`. It may include a path prefix. |
| `endpoints` | (required) | Endpoints exposed as tools, as `{path}@{method}`. The path includes the base path of the spec's first `servers` URL. Tools are listed in this order. |
| `maxResponseBodySize` | `1048576` (1 MiB) | Maximum size of a backend response body, in bytes. A larger body is replaced by an error message. |

TLS and the backend timeouts come from `client.yml` (`tls`, `request.connectTimeout` and `request.timeout`).

```yaml
# values.yml
openapi-mcp.downstreamHost: https://pets.example.com
openapi-mcp.endpoints:
  - /v1/pets@get
  - /v1/pets@post
  - /v1/pets/{petId}@get
```

### Handler chain
Register the handler in `handler.yml` as the last handler of the MCP endpoint's chain, and route only `POST` to it:

```yaml
handler.handlers:
  - com.networknt.exception.ExceptionHandler@exception
  - com.networknt.correlation.CorrelationHandler@correlation
  - com.networknt.cors.CorsHttpHandler@cors
  - com.networknt.openapi.JwtVerifyHandler@security
  - com.networknt.openapi.OpenApiMcpHandler@openapiMcp

handler.chains:
  mcp:
    - exception
    - correlation
    - cors
    - security
    - openapiMcp

handler.paths:
  - path: '/mcp'
    method: 'POST'
    exec:
      - mcp
```

- `/mcp` is not in the spec, so do not put OpenApiHandler or ValidatorHandler in the chain. With JwtVerifyHandler, set `skipVerifyScopeWithoutSpec: true` (or `enableVerifyScope: false`) in `security.yml`.
- Do not put BodyHandler in the chain. The handler reads the request body itself.
- The CORS handler rejects requests with an invalid `Origin`, as MCP requires.
- The headers of the MCP request are forwarded to the backend the way ProxyHandler forwards them, including `Authorization`. MCP forbids passing the client's token through to the backend, so put a handler that replaces it, such as TokenExchangeHandler, earlier in the chain.

## Limitations
- **MCP version:** only `2026-07-28` over the Streamable HTTP transport. It is stateless: there is no `initialize` handshake, no session, and no SSE streaming. Earlier versions are rejected.
- **MCP methods:** only `server/discover`, `tools/list` and `tools/call`. Resources, prompts, subscriptions, tasks, pagination, `outputSchema` and `structuredContent` are not supported.
- **Request bodies:** only `application/json`. The tool argument `body` is forwarded as JSON.
- **Parameters:** path, query and header parameters, serialized the OpenAPI default way. Cookie parameters are not supported, and a parameter may not be named `body`.
- **Response bodies:**

  | Backend Content-Type | Tool result content |
  | --- | --- |
  | `text/*`, `application/json`, `application/xml`, `*+json`, `*+xml` | `text`, decoded as UTF-8 |
  | `image/*` | base64 `image` |
  | `audio/*` | base64 `audio` |
  | Anything else, or none (including other binary types and multipart) | Not supported: an error message, with `isError: true` |

- **Errors:** a non-2xx backend status, a body over `maxResponseBodySize`, and connection or timeout failures are returned as a tool result with `isError: true`. Redirects are not followed.
- **Validation:** tool arguments are not validated against the input schema; the backend is expected to reject invalid input.
- **Startup:** the server fails to start if a configured endpoint is not in the spec, has no `operationId`, shares its `operationId` with another, or cannot be exposed (non-JSON request body, cookie parameter, a parameter named `body`, or two parameters with the same name).
- **Other:** a single spec and a fixed `downstreamHost` (no service discovery). Every caller sees every tool. Config and spec changes need a restart.
