package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.httpstring.AttachmentConstants;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiReloadTest {
    private final OpenApiRouting previous = OpenApiHandler.routingSnapshot();
    private volatile String valueType = "string";
    private volatile boolean failLoad;
    private volatile CountDownLatch loading;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void configure() {
        Config.getInstance().clear();
        Config.getInstance().setClassLoader(new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.endsWith("reload-spec.yaml") || name.endsWith("openapi.yaml")) {
                    CountDownLatch blocked = loading;
                    if (blocked != null) {
                        blocked.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("reload timed out");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    if (failLoad) throw new IllegalStateException("intentional spec load failure");
                    return new ByteArrayInputStream(spec().getBytes(StandardCharsets.UTF_8));
                }
                return super.getResourceAsStream(name);
            }
        });
        Config.getInstance().putInConfigCache("handler", Map.of("basePath", "/"));
        Config.getInstance().putInConfigCache("openapi-validator", Map.of(
                "enabled", true, "validateResponse", false, "skipBodyValidation", false));
        configureMode(false);
    }

    private String spec() {
        return """
                openapi: 3.0.0
                info: {title: Reload regression, version: '1'}
                servers: [{url: /api}]
                paths:
                  /items:
                    options:
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: '#/components/schemas/Value'}
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Value'}
                components:
                  schemas:
                    Value:
                      type: object
                      required: [value]
                      properties:
                        value: {type: %s}
                """.formatted(valueType);
    }

    private void configureMode(boolean multiple) {
        Config.getInstance().putInConfigCache("openapi-handler", Map.of(
                "multipleSpec", multiple, "ignoreInvalidPath", false,
                "pathSpecMapping", Map.of("/api/", "reload-spec")));
    }

    private static HttpServerExchange exchange() {
        HttpServerExchange exchange = new HttpServerExchange(null);
        exchange.setRequestURI("/api/items");
        exchange.setRequestPath("/api/items");
        exchange.setRequestMethod(Methods.OPTIONS);
        exchange.getRequestHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, "application/json");
        return exchange;
    }

    @AfterEach
    void restore() {
        release.countDown();
        Config.getInstance().clear();
        Config.getInstance().setClassLoader(getClass().getClassLoader());
        OpenApiHandler.publishRouting(previous);
    }

    @Test
    void validatorConstructedBeforeMetadataRecoversWhenRoutingAppears() throws Exception {
        OpenApiHandler.publishRouting(new OpenApiRouting(null, Map.of()));
        ValidatorHandler validator = new ValidatorHandler();
        assertNull(validator.getRequestValidator("/api/items"));
        OpenApiHandler metadata = new OpenApiHandler();
        AtomicBoolean reached = new AtomicBoolean();
        validator.setNext(e -> reached.set(true));
        metadata.setNext(validator);
        metadata.handleRequest(exchange());
        assertTrue(reached.get());
        ResponseValidator response = validator.getResponseValidator("/api/items");
        assertNull(response.validateResponseContent("{\"value\":\"old\"}", "/api/items", "options"));
        assertEquals("ERR11004", response.validateResponseContent("{\"value\":42}", "/api/items", "options").getCode());
    }

    @Test
    void failedReloadKeepsPreviousGenerationAndRetriesTheSameConfiguration() throws Exception {
        OpenApiHandler metadata = new OpenApiHandler();
        AtomicInteger reached = new AtomicInteger();
        metadata.setNext(e -> reached.incrementAndGet());
        OpenApiRouting old = OpenApiHandler.routingSnapshot();
        configureMode(true);
        failLoad = true;
        assertThrows(RuntimeException.class, () -> metadata.handleRequest(exchange()));
        assertSame(old, OpenApiHandler.routingSnapshot());
        assertThrows(RuntimeException.class, () -> metadata.handleRequest(exchange()));
        assertSame(old, OpenApiHandler.routingSnapshot());
        failLoad = false;
        metadata.handleRequest(exchange());
        assertEquals(1, reached.get());
        assertNotSame(old, OpenApiHandler.routingSnapshot());
        assertNull(OpenApiHandler.routingSnapshot().single());
        assertNotNull(OpenApiHandler.getHelper("/api/items"));
    }

    @Test
    void modeSwitchPublishesOnceAndValidatorRebuildsEvenAfterReadingDuringReload() throws Exception {
        OpenApiHandler metadata = new OpenApiHandler();
        metadata.setNext(e -> {});
        ValidatorHandler validator = new ValidatorHandler();
        ResponseValidator retained = validator.getResponseValidator("/api/items");
        OpenApiRouting old = OpenApiHandler.routingSnapshot();
        configureMode(true);
        valueType = "integer";
        loading = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reload = executor.submit(() -> {
                metadata.handleRequest(exchange());
                return null;
            });
            try {
                assertTrue(loading.await(5, TimeUnit.SECONDS));
                assertSame(old, OpenApiHandler.routingSnapshot());
                assertSame(old.single(), OpenApiHandler.getHelper("/api/items"));
                assertSame(retained, validator.getResponseValidator("/api/items"));
            } finally {
                release.countDown();
            }
            reload.get(10, TimeUnit.SECONDS);
        }
        ResponseValidator updated = validator.getResponseValidator("/api/items");
        assertNotSame(retained, updated);
        assertNull(updated.validateResponseContent("{\"value\":42}", "/api/items", "options"));
        assertEquals("ERR11004", updated.validateResponseContent("{\"value\":\"old\"}", "/api/items", "options").getCode());
        assertNull(retained.validateResponseContent("{\"value\":\"old\"}", "/api/items", "options"));
        configureMode(false);
        metadata.handleRequest(exchange());
        assertNotNull(OpenApiHandler.routingSnapshot().single());
        assertNotNull(validator.getResponseValidator("/api/items"));
    }

    @Test
    void inFlightRequestKeepsTheSchemaGenerationThatResolvedItsOperation() throws Exception {
        OpenApiHandler metadata = new OpenApiHandler();
        metadata.setNext(e -> {});
        HttpServerExchange inFlight = exchange();
        inFlight.putAttachment(AttachmentConstants.REQUEST_BODY, Map.of("value", "old"));
        metadata.handleRequest(inFlight);
        OpenApiRouting old = inFlight.getAttachment(OpenApiHandler.ROUTING);
        valueType = "integer";
        configureMode(true);
        metadata.handleRequest(exchange());
        assertNotSame(old, OpenApiHandler.routingSnapshot());
        ValidatorHandler validator = new ValidatorHandler();
        AtomicBoolean reached = new AtomicBoolean();
        validator.setNext(e -> reached.set(true));
        validator.handleRequest(inFlight);
        assertTrue(reached.get(), "the string request must use its original local reference context");
        assertSame(old.helperFor("/api/items"), OpenApiHandler.getHelper(inFlight));
    }

    @Test
    void optionsWorksInTurkishLocaleForMetadataAndExchangeResponseOverloads() throws Exception {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            OpenApiHandler metadata = new OpenApiHandler();
            AtomicBoolean reached = new AtomicBoolean();
            metadata.setNext(e -> reached.set(true));
            HttpServerExchange exchange = exchange();
            metadata.handleRequest(exchange);
            assertTrue(reached.get());
            ResponseValidator validator = new ValidatorHandler().getResponseValidator("/api/items");
            assertNull(validator.validateResponseContent("{\"value\":\"valid\"}", exchange));
            assertEquals("ERR11004", validator.validateResponseContent("{\"value\":42}", exchange).getCode());
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
