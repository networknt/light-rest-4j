package com.networknt.test;

import com.networknt.client.ClientConfig;
import com.networknt.config.Config;
import com.networknt.client.Http2Client;
import com.networknt.client.simplepool.SimpleConnectionState;
import io.undertow.Undertow;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import org.xnio.IoUtils;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test-only HTTP ownership, completion and connection-lifetime rules. */
public final class HttpClientFixture {
    private static final Map<URI, Set<ClientConnection>> CONNECTIONS = new ConcurrentHashMap<>();

    private HttpClientFixture() { }

    public static URI address(Undertow server) {
        int port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        return URI.create("http://localhost:" + port);
    }

    public static void send(ClientConnection connection, ClientRequest request,
                            ClientCallback<ClientExchange> callback) {
        if (Thread.currentThread() == connection.getIoThread()) {
            connection.sendRequest(request, callback);
        } else {
            connection.getIoThread().execute(() -> connection.sendRequest(request, callback));
        }
    }

    public static ClientResponse awaitResponse(AtomicReference<ClientResponse> response,
                                                CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(30, TimeUnit.SECONDS), "HTTP fixture response timed out");
        assertNotNull(response.get(), "HTTP fixture failed before receiving a response");
        return response.get();
    }

    public static void restore(Http2Client client, SimpleConnectionState.ConnectionToken token) {
        if (token == null) return;
        CONNECTIONS.computeIfAbsent(token.uri(), ignored -> ConcurrentHashMap.newKeySet())
                .add((ClientConnection) token.getRawConnection());
        client.restore(token);
    }

    /** Keep reuse during a fixture, then close sockets before its listener stops. */
    public static void closeConnections(URI address) {
        Set<ClientConnection> connections = CONNECTIONS.remove(address);
        if (connections != null) connections.forEach(IoUtils::safeClose);
    }

    @SuppressWarnings("unchecked")
    public static AutoCloseable tokenKeyServer(Undertow server) {
        var config = Config.getInstance();
        Map<String, Object> previous = config.getJsonMapConfig("client");
        var changed = new HashMap<>(previous);
        var oauth = new HashMap<>((Map<String, Object>) previous.get(ClientConfig.OAUTH));
        var token = new HashMap<>((Map<String, Object>) oauth.get(ClientConfig.TOKEN));
        var key = new HashMap<>((Map<String, Object>) token.get(ClientConfig.KEY));
        key.put(ClientConfig.SERVER_URL, address(server).toString());
        token.put(ClientConfig.KEY, key);
        oauth.put(ClientConfig.TOKEN, token);
        changed.put(ClientConfig.OAUTH, oauth);
        config.putInConfigCache("client", changed);
        return () -> config.putInConfigCache("client", previous);
    }
}
