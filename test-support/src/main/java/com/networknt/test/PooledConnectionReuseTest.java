package com.networknt.test;

import com.networknt.client.Http2Client;
import io.undertow.Undertow;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xnio.OptionMap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Inherited by each consumer module to qualify reuse under its own client config. */
public abstract class PooledConnectionReuseTest {
    @Test
    @Timeout(15)
    public void reusesOnePooledSocketAcrossTwoCompletedRequests() throws Exception {
        Undertow server = Undertow.builder().addHttpListener(0, "localhost")
                .setHandler(exchange -> exchange.getResponseSender().send("ok")).build();
        server.start();
        var address = HttpClientFixture.address(server);
        var client = Http2Client.getInstance();
        ClientConnection first = null;
        try {
            for (int i = 0; i < 2; i++) {
                var token = client.borrow(address, Http2Client.WORKER, Http2Client.SSL,
                        Http2Client.BUFFER_POOL, OptionMap.EMPTY);
                try {
                    assertEquals(address, token.uri(), "Pool token must retain the listener URI used by teardown");
                    var connection = (ClientConnection) token.getRawConnection();
                    if (first == null) first = connection;
                    else assertSame(first, connection, "Second borrow must reuse the same socket");
                    var request = new ClientRequest().setPath("/").setMethod(Methods.GET);
                    request.getRequestHeaders().put(Headers.HOST, "localhost");
                    var response = new AtomicReference<ClientResponse>();
                    var latch = new CountDownLatch(1);
                    HttpClientFixture.send(connection, request, client.createClientCallback(response, latch));
                    var completed = HttpClientFixture.awaitResponse(response, latch);
                    assertEquals(200, completed.getResponseCode());
                    assertEquals("ok", completed.getAttachment(Http2Client.RESPONSE_BODY));
                } finally {
                    HttpClientFixture.restore(client, token);
                }
            }
            HttpClientFixture.closeConnections(address);
            assertFalse(first.isOpen(), "Fixture teardown must close the reused socket");
        } finally {
            HttpClientFixture.closeConnections(address);
            server.stop();
        }
    }
}
