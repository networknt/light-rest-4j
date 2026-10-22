package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.utility.Constants;
import io.undertow.Undertow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiBasePathTest {
    private final OpenApiHelper previousHelper = OpenApiHandler.helper;
    private final Map<String, OpenApiHelper> previousHelpers = OpenApiHandler.helperMap;
    private Undertow server;
    private URI address;

    @AfterEach
    void restoreConfiguration() {
        if (server != null) server.stop();
        Config.getInstance().clear();
        Config.getInstance().setClassLoader(OpenApiBasePathTest.class.getClassLoader());
        OpenApiHandler.helper = previousHelper;
        OpenApiHandler.helperMap = previousHelpers;
    }

    private void start(String servers, String fallback, boolean multiple, boolean missingSpec) {
        String spec = """
                openapi: 3.0.0
                info:
                  title: Base path regression
                  version: '1'
                """ + servers + """
                paths:
                  /pets:
                    get:
                      responses:
                        '200':
                          description: OK
                  /pets/{petId}:
                    get:
                      responses:
                        '200':
                          description: OK
                """;
        Config.getInstance().clear();
        Config.getInstance().setClassLoader(new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String file = name.startsWith("config/") ? name.substring(7) : name;
                if (file.matches("openapi\\.(yaml|yml|json)")) {
                    return !missingSpec && file.endsWith(".yaml")
                            ? new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8)) : null;
                }
                return super.getResourceAsStream(name);
            }
        });
        Config.getInstance().putInConfigCache("handler", Map.of("basePath", fallback));
        Config.getInstance().putInConfigCache("openapi-handler", Map.of(
                "multipleSpec", multiple, "ignoreInvalidPath", false,
                "pathSpecMapping", Map.of("/pets", "openapi")));
        OpenApiHandler handler = new OpenApiHandler();
        handler.setNext(exchange -> exchange.getResponseSender().send(
                (String) exchange.getAttachment(AttachmentConstants.AUDIT_INFO).get(Constants.ENDPOINT_STRING)));
        server = Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(handler).build();
        server.start();
        int port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        address = URI.create("http://127.0.0.1:" + port);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(address.resolve(path)).timeout(Duration.ofSeconds(10))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void absentServersDoNotInheritPetstorePrefix() throws Exception {
        start("", "/", false, false);
        assertEquals("/", OpenApiHandler.helper.basePath);
        assertEquals("/pets@get", get("/pets").body());
        assertEquals(404, get("/v1/pets").statusCode());
        assertEquals("/pets/{petId}@get", get("/pets/v1").body());
    }

    @Test
    void emptyServersUseRootFallback() throws Exception {
        start("servers: []\n", "/", false, false);
        assertEquals("/pets@get", get("/pets").body());
        assertEquals(404, get("/v1/pets").statusCode());
    }

    @Test
    void hostOnlyServerUsesRootFallback() throws Exception {
        start("servers:\n  - url: 'https://example.com'\n", "/", false, false);
        assertEquals("/pets@get", get("/pets").body());
    }

    @Test
    void explicitRootServerDoesNotDoubleEndpointSlash() throws Exception {
        start("servers:\n  - url: '/'\n", "/", false, false);
        assertEquals("/pets@get", get("/pets").body());
    }

    @Test
    void explicitBasePathRemainsAuthoritative() throws Exception {
        start("servers:\n  - url: '/api'\n", "/fallback", false, false);
        assertEquals("/api", OpenApiHandler.helper.basePath);
        assertEquals("/api/pets@get", get("/api/pets").body());
    }

    @Test
    void configuredFallbackIsNoLongerMaskedByInjection() throws Exception {
        start("", "/configured", false, false);
        assertEquals("/configured", OpenApiHandler.helper.basePath);
        assertEquals("/configured/pets@get", get("/configured/pets").body());
    }

    @Test
    void missingServiceSpecStillServesAdminEndpointsAtRoot() throws Exception {
        start("", "/", false, true);
        assertEquals("/", OpenApiHandler.helper.basePath);
        assertEquals("/adm/server/info@get", get("/adm/server/info").body());
        assertEquals(404, get("/pets").statusCode());
        assertEquals(404, get("/v1/adm/server/info").statusCode());
    }

    @Test
    void multipleSpecRootDoesNotDoubleEndpointSlash() throws Exception {
        start("servers:\n  - url: '/'\n", "/", true, false);
        assertEquals("/pets@get", get("/pets").body());
        assertEquals("/adm/server/info@get", get("/adm/server/info").body());
    }
}
