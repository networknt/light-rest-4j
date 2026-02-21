/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import com.networknt.client.Http2Client;
import com.networknt.client.simplepool.SimpleConnectionState;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.status.Status;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

public class OpenApiHandlerMultipleSpecsTest {
    static final Logger logger = LoggerFactory.getLogger(OpenApiHandlerTest.class);

    static Undertow server = null;

    @BeforeAll
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            OpenApiHandler openApiHandler = new OpenApiHandler("openapi-handler-multiple");
            openApiHandler.setNext(handler);
            handler = openApiHandler;
            server = Undertow.builder()
                    .addHttpListener(7080, "localhost")
                    .setHandler(handler)
                    .build();
            server.start();
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if(server != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server.stop();
            logger.info("The server is stopped.");
        }
    }

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/pets", exchange -> exchange.getResponseSender().send("get"))
                .add(Methods.POST, "/petstore/pets", exchange -> {
                    Map<String, Object> auditInfo = exchange.getAttachment(AttachmentConstants.AUDIT_INFO);
                    if(auditInfo != null) {
                        exchange.getResponseSender().send("withAuditInfo");
                    } else {
                        exchange.getResponseSender().send("withoutAuditInfo");
                    }
                })
                .add(Methods.DELETE, "/petstore/pets", exchange -> exchange.getResponseSender().send("deleted"));
    }

    @Test
    public void testWrongPathWithoutBasePath() throws Exception {
        // this path is not in petstore swagger specification. get error
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken token;

        try {

            token = client.borrow(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY);

        } catch (Exception e) {

            throw new ClientException(e);

        }

        final ClientConnection connection = (ClientConnection) token.getRawConnection();
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/get").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        String responseBody = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        System.out.println("statusCode = " + statusCode + " responseBody = " + responseBody);
        Assertions.assertEquals(404, statusCode);
        if(statusCode == 404) {
            Status status = Config.getInstance().getMapper().readValue(responseBody, Status.class);
            Assertions.assertNotNull(status);
            Assertions.assertEquals("ERR10007", status.getCode());
        }
    }

    @Test
    public void testWrongPathWithBasePath() throws Exception {
        // this path is not in petstore swagger specification. get error
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken token;

        try {

            token = client.borrow(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY);

        } catch (Exception e) {

            throw new ClientException(e);

        }

        final ClientConnection connection = (ClientConnection) token.getRawConnection();
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/market/get").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        String responseBody = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        System.out.println("statusCode = " + statusCode + " responseBody = " + responseBody);
        Assertions.assertEquals(404, statusCode);
        if(statusCode == 404) {
            Status status = Config.getInstance().getMapper().readValue(responseBody, Status.class);
            Assertions.assertNotNull(status);
            Assertions.assertEquals("ERR10007", status.getCode());
        }
    }

    @Test
    public void testWrongMethod() throws Exception {
        // this path is not in petstore swagger specification. get error
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken token;

        try {

            token = client.borrow(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY);

        } catch (Exception e) {

            throw new ClientException(e);

        }

        final ClientConnection connection = (ClientConnection) token.getRawConnection();
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets").setMethod(Methods.DELETE);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assertions.assertEquals(405, statusCode);
        if(statusCode == 405) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assertions.assertNotNull(status);
            Assertions.assertEquals("ERR10008", status.getCode());
        }
    }

    @Test
    public void testRightRequest() throws Exception {
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken token;

        try {

            token = client.borrow(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY);

        } catch (Exception e) {

            throw new ClientException(e);

        }

        final ClientConnection connection = (ClientConnection) token.getRawConnection();

        try {
            String post = "post";
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/petstore/pets");
                    request.getRequestHeaders().put(Headers.HOST, "localhost");
                    request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
                    connection.sendRequest(request, client.createClientCallback(reference, latch, post));
                }
            });
            latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("IOException: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assertions.assertEquals(200, statusCode);
        if(statusCode == 200) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            Assertions.assertNotNull(body);
            Assertions.assertEquals("withAuditInfo", body);
        }
    }

    @Test
    public void testMapUtils() {
        Map<String, String> preferredMap = new HashMap<>();
        Map<String, String> alternativeMap = new HashMap<>();

        preferredMap.put("a", "1");
        preferredMap.put("b", "2");

        alternativeMap.put("a", "11");
        alternativeMap.put("b", "22");
        alternativeMap.put("c", "33");

        Map<String, ?> mergedMap = OpenApiHandler.mergeMaps(preferredMap, alternativeMap);

        assertEquals(3, mergedMap.size());
        assertEquals("1", mergedMap.get("a"));
        assertEquals("2", mergedMap.get("b"));
        assertEquals("33", mergedMap.get("c"));

        Map<String, Object> nonNullMap = OpenApiHandler.nonNullMap(null);

        assertNotNull(nonNullMap);

    }

}
