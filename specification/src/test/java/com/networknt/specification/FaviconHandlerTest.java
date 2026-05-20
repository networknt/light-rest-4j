package com.networknt.specification;

import com.networknt.client.Http2Client;
import com.networknt.client.simplepool.SimpleConnectionState;
import com.networknt.exception.ClientException;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class FaviconHandlerTest {
    private static final Logger logger = LoggerFactory.getLogger(FaviconHandlerTest.class);
    private static final HttpString X_CONTENT_TYPE_OPTIONS = new HttpString("X-Content-Type-Options");
    private static Undertow server = null;

    @BeforeAll
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            server = Undertow.builder()
                    .addHttpListener(7081, "localhost")
                    .setHandler(handler)
                    .build();
            server.start();
        }
    }

    @AfterAll
    public static void tearDown() {
        if(server != null) {
            server.stop();
            logger.info("The server is stopped.");
        }
    }

    static RoutingHandler getTestHandler() {
        return Handlers.routing().add(Methods.GET, "/favicon.ico", new FaviconHandler());
    }

    @Test
    public void testFaviconSecurityHeaders() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken token;
        try {
            token = client.borrow(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY);
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final ClientConnection connection = (ClientConnection) token.getRawConnection();
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/favicon.ico").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            client.restore(token);
        }

        ClientResponse response = reference.get();
        Assertions.assertEquals(200, response.getResponseCode());
        Assertions.assertEquals("image/x-icon", response.getResponseHeaders().getFirst(Headers.CONTENT_TYPE));
        Assertions.assertEquals("nosniff", response.getResponseHeaders().getFirst(X_CONTENT_TYPE_OPTIONS));
    }
}
