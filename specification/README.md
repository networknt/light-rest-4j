# Specification handlers

## HSTS for favicon and other browser responses (Checkmarx A02)

HSTS is a host-wide policy. Configure the existing HeaderHandler in the consuming
application; setting it only in FaviconHandler leaves other response paths uncovered.
The specification library does not activate host policy or add a header dependency.

The [values example](examples/hsts/values.yml) uses a late root-prefix response rule
so backend headers cannot replace the intended HSTS value. It is for a **dedicated
HTTPS ingress instance**, and disables plaintext HTTP. Do not merge it into a normal
co-located http-sidecar instance that needs localhost:9080 for egress. Use a separate
ingress instance or enforce HSTS at a trusted TLS edge. Generic HeaderHandler does
not distinguish HTTP from HTTPS listeners, so this example cannot enforce TLS-only
HSTS in one dual-listener process.

For a complete registered configuration, see the consuming
[http-sidecar handler configuration](https://github.com/networknt/http-sidecar/blob/2.x/src/main/resources/config/values.yml)
and [HSTS integration guide](https://github.com/networknt/http-sidecar/blob/2.x/config/hsts/README.md).
Keep header first in all applicable routes/chains, including custom fallback/error
handling. Preserve existing handlers and merge existing prefix rules. Add the
com.networknt:header dependency at the same version as the application's light-4j
modules when the application does not already include it. Keep public HTTP redirects
outside the HSTS policy and use a browser-trusted certificate. With TLS offload,
verify the edge policy and prevent untrusted direct-origin access.

HstsConfigurationExampleTest parses the namespaced example and checks its policy
contract without changing other specification tests' default configuration. Actual
handler registration, TLS, proxy and direct-route behavior is tested in http-sidecar;
the example alone does not register middleware in a consuming application.

The policy intentionally omits `includeSubDomains` and `preload`; either needs a
separate domain-wide compatibility review. HSTS is learned over a secure connection,
so first-visit protection requires an appropriate preload arrangement. See
[RFC 6797](https://www.rfc-editor.org/rfc/rfc6797.html).

Before closing the partner finding, deploy the reviewed configuration and capture
public HTTPS headers for the favicon, normal, redirect and error responses. Verify
one intended HSTS value and direct-origin access restrictions, then rerun the scan.
Loopback tests do not establish the partner deployment's state.
