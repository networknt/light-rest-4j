# Specification handlers

## HSTS for favicon and other browser responses (Checkmarx A02)

HSTS is a policy for the entire public host. Configure the existing
`com.networknt.header.HeaderHandler` in the consuming application so the policy
covers the favicon, API/specification responses, redirects and errors. Setting
it only in `FaviconHandler` leaves the other response paths uncovered.

The [HTTPS-only values example](examples/hsts/values.yml) enables the existing
header middleware with `Strict-Transport-Security: max-age=31536000`. Merge its
entries into the application's `values.yml`, preserving any existing response
header updates. Add the `com.networknt:header` dependency to the application,
using the same version as its light-4j modules. The specification library does
not add this dependency or activate a host-wide policy for applications.

Register `com.networknt.header.HeaderHandler@header` in `handler.handlers` and
put `header` before handlers that can finish a response in every browser-facing
route's `exec` list. Include it in `handler.defaultHandlers` before the existing
not-found terminal handler as well; a named chain is not automatically applied
to all routes. In particular, the favicon route must include it:

```yaml
handler.handlers:
  # Preserve the application's other registered handlers.
  - com.networknt.header.HeaderHandler@header
  - com.networknt.specification.FaviconHandler@favicon

handler.paths:
  # Merge into the existing paths; do not replace the application's routing table.
  - path: '/favicon.ico'
    method: 'GET'
    exec:
      - header
      - favicon
```

For an application assembled through `HandlerProvider`/`service.yml`, wrap the
complete routing/error handler with `HeaderHandler`, so unmatched routes and
exception responses also pass through it. `FaviconHstsConfigurationTest` tests
this composition using the exact values example, an actual TLS listener, the
real favicon handler, and normal/redirect/not-found/exception paths.

This profile is for a public HTTPS-only listener with a valid certificate.
`HeaderHandler` is generic: it does not check the request scheme. Do not attach
this HSTS configuration to a publicly accessible plaintext HTTP listener. If HTTP
is needed for redirection, keep that listener's redirect handler outside the
HSTS chain and advertise HSTS on its HTTPS destination. With TLS offload, ensure
the trusted edge emits or preserves exactly one HSTS header on the public HTTPS
response and prevents untrusted direct access to the origin; do not infer trust
from a client-supplied forwarded-protocol header.

The example intentionally omits `includeSubDomains` and `preload`. Enabling either
requires a separate domain-wide review. HSTS is learned from a successful secure
connection; first-visit protection additionally needs an appropriate preload
arrangement. See [RFC 6797](https://www.rfc-editor.org/rfc/rfc6797.html).

Before partner scan closure, capture the public HTTPS responses for
`/favicon.ico`, a normal route, a redirect and error/not-found routes. Confirm
exactly one intended HSTS value, verify the deployed listener/edge configuration,
and rerun the scan against the actual deployed artifact. The library test and
this example do not establish the partner's deployed configuration.
