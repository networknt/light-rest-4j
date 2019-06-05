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

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.body.BodyHandler;
import com.networknt.client.Http2Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.status.Status;
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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by steve on 01/09/16.
 */
public class ValidatorHandlerTest {

    static final Logger logger = LoggerFactory.getLogger(ValidatorHandlerTest.class);

    static Undertow server = null;

    static Map<String, Object> responses = Config.getInstance().getJsonMapConfig("responses");

    static String logFile = "target/test.log";

    File f = new File(logFile);

    @Before
    public void setUp() {
        if(server == null) {
            logger.info("starting server");

            HttpHandler handler = getPetStoreHandler();
            ValidatorHandler validatorHandler = new ValidatorHandler();
            validatorHandler.setNext(handler);
            handler = validatorHandler;

            BodyHandler bodyHandler = new BodyHandler();
            bodyHandler.setNext(handler);
            handler = bodyHandler;

            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(handler);
            handler = openApiHandler;

            server = Undertow.builder()
                    .addHttpListener(8080, "localhost")
                    .setHandler(handler)
                    .build();
            server.start();
            clearLogFile();
        }
    }

    @Before
    public void clearLogFile() {
        synchronized (f) {
            try {
                FileWriter fw = new FileWriter(f, false);
                fw.write("");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @AfterClass
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

    RoutingHandler getPetStoreHandler() {
        ForwardRequestHandler forwardHandler = new ForwardRequestHandler();
        return Handlers.routing()
                .add(Methods.POST, "/v1/pets", exchange -> exchange.getResponseSender().send("addPet"))
                .add(Methods.GET, "/v1/pets/{petId}", exchange -> exchange.getResponseSender().send("getPetById"))
                .add(Methods.DELETE, "/v1/pets/{petId}", exchange -> exchange.getResponseSender().send("deletePetById"))
                .add(Methods.GET, "/v1/todoItems", forwardHandler)
                .add(Methods.GET, "/v1/pets", exchange -> {
                    if (exchange.getQueryParameters() != null && exchange.getQueryParameters().containsKey("arr")) {
                        exchange.getResponseSender().send("getPets" + ", the query parameter = " + exchange.getQueryParameters() + ", length = " + exchange.getQueryParameters().get("arr").size());
                    } else {
                        exchange.getResponseSender().send("getPets");
                    }
                });
    }

    @Test
    public void testInvalidRequstPath() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/api").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(404, statusCode);
        if(statusCode == 404) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10007", status.getCode());
        }
    }

    @Test
    public void testInvalidMethod() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets").setMethod(Methods.DELETE);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(405, statusCode);
        if(statusCode == 405) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10008", status.getCode());
        }
    }

    @Test
    public void testInvalidPost() throws Exception {
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }

        try {
            String post = "{\"name\"x:\"Pinky\", \"photoUrl\": \"http://www.photo.com/1.jpg\"}";
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/post");
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
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(404, statusCode);
        if(statusCode == 404) {
            Status status = Config.getInstance().getMapper().readValue(body, Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10007", status.getCode());
        }
    }

    @Test
    public void testValidPostWithHeaders() throws Exception {
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }

        try {
            String post = "{\"id\":0,\"category\":{\"id\":0,\"name\":\"string\"},\"name\":\"doggie\",\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/v1/pets");
                    request.getRequestHeaders().put(Headers.HOST, "localhost");
                    request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
                    request.getRequestHeaders().put(new HttpString("accessId"), "001");
                    request.getRequestHeaders().put(new HttpString("requestId"), "64");
                    connection.sendRequest(request, client.createClientCallback(reference, latch, post));
                }
            });

            latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("IOException: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(200, statusCode);
        if(statusCode == 200) {
            Assert.assertNotNull(body);
            Assert.assertEquals("addPet", body);
        }
    }

    @Test
    public void testInvalidMaximumHeaders() throws Exception {
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }

        try {
            String post = "{\"id\":0,\"category\":{\"id\":0,\"name\":\"string\"},\"name\":\"doggie\",\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/v1/pets");
                    request.getRequestHeaders().put(Headers.HOST, "localhost");
                    request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
                    request.getRequestHeaders().put(new HttpString("accessId"), "001");
                    request.getRequestHeaders().put(new HttpString("requestId"), "65");  // the maximum for the request is 64 in the spec.
                    connection.sendRequest(request, client.createClientCallback(reference, latch, post));
                }
            });

            latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("IOException: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(400, statusCode);
        if(statusCode == 400) {
            Status status = Config.getInstance().getMapper().readValue(body, Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR11004", status.getCode());
        }
    }

    @Test
    public void testValidPostWithoutHeaders() throws Exception {
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }

        try {
            String post = "{\"id\":0,\"category\":{\"id\":0,\"name\":\"string\"},\"name\":\"doggie\",\"photoUrls\":[\"string\"],\"tags\":[{\"id\":0,\"name\":\"string\"}],\"status\":\"available\"}";
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.POST).setPath("/v1/pets");
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
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(400, statusCode);
        if(statusCode == 400) {
            Status status = Config.getInstance().getMapper().readValue(body, Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR11017", status.getCode());
        }
    }

    @Test
    public void testGetParam() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(200, statusCode);
        if(statusCode == 200) {
            Assert.assertNotNull(body);
            Assert.assertEquals("getPetById", body);
        }
    }

    @Test
    public void testDeleteWithoutHeader() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.DELETE);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(400, statusCode);
        if(statusCode == 400) {
            Status status = Config.getInstance().getMapper().readValue(body, Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR11017", status.getCode());
        }
    }

    @Test
    public void testDeleteWithHeader() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.DELETE);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(new HttpString("key"), "key");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(200, statusCode);
        if(statusCode == 200) {
            Assert.assertNotNull(body);
            Assert.assertEquals("deletePetById", body);
        }
    }

    @Test
    public void testNullValue() throws IOException {
        String json = "{\"selection\": {\"id\": null}}";
        Map<String, Object> map = Config.getInstance().getMapper().readValue(json, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Object> selectionMap = (Map<String, Object>)map.get("selection");
        Assert.assertNull(selectionMap.get("id"));

    }

    /**
     * Pass a query parameter all which is boolean type.
     * @throws Exception
     */
    @Test
    public void testIssue57() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets?all=false").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(new HttpString("accessId"), "accessId");
            request.getRequestHeaders().put(new HttpString("requestId"), "requestId");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(200, statusCode);
    }

    @Test
    public void testQueryParameterArray() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:8080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets?arr=1&arr=2&arr=3").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(new HttpString("accessId"), "accessId");
            request.getRequestHeaders().put(new HttpString("requestId"), "requestId");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertEquals(200, statusCode);
        Assert.assertEquals("getPets, the query parameter = {arr=[1, 2, 3]}, length = 3", body);
    }

    @Test
    public void testResponseContentValidationWithError() throws ClientException, URISyntaxException, ExecutionException, InterruptedException, TimeoutException {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header1"), "header_1");
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response2");
        String statusCode = future.get().getStatus();
        Assert.assertEquals("OK", statusCode);
        List<String> errorLines = getErrorLinesFromLogFile();
        Assert.assertTrue(errorLines.size() > 0);
    }

    @Test
    public void testNoResponseContentValidation() throws ClientException, URISyntaxException, ExecutionException, InterruptedException, TimeoutException {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header1"), "header_1");
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "");
        String statusCode = future.get().getStatus();
        Assert.assertNotEquals("OK", statusCode);
    }

    @Test
    public void testResponseContentValidationWithNoError() throws ClientException, URISyntaxException, ExecutionException, InterruptedException {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header1"), "header_1");
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response1");
        String statusCode = future.get().getStatus();
        Assert.assertEquals("OK", statusCode);
        List<String> errorLines = getErrorLinesFromLogFile();
        Assert.assertTrue(errorLines.size() == 0);
    }

    @Test
    public void testResponseHeaderValidationWithNoError() throws ClientException, URISyntaxException, ExecutionException, InterruptedException {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header1"), "header_1");
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header2"), "123");
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response1");
        String statusCode = future.get().getStatus();
        Assert.assertEquals("OK", statusCode);
        List<String> errorLines = getErrorLinesFromLogFile();
        Assert.assertTrue(errorLines.size() == 0);
    }

    @Test
    public void testResponseHeaderValidationWithError() throws ClientException, URISyntaxException, ExecutionException, InterruptedException {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header1"), "header_1");
        clientRequest.getRequestHeaders().put(new HttpString("todo_Header2"), "header_2");
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response1");
        String statusCode = future.get().getStatus();
        Assert.assertEquals("OK", statusCode);
        List<String> errorLines = getErrorLinesFromLogFile();
        Assert.assertTrue(errorLines.size() > 0);
    }

    @Test
    public void testResponseHeaderRequiredValidationWithError() throws ClientException, URISyntaxException, ExecutionException, InterruptedException {
        ClientRequest clientRequest = new ClientRequest();
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response1");
        String statusCode = future.get().getStatus();
        Assert.assertEquals("OK", statusCode);
        List<String> errorLines = getErrorLinesFromLogFile();
        Assert.assertTrue(errorLines.size() > 0);
    }

    public static CompletableFuture<ClientResponse> sendResponse(ClientRequest request, String response) throws ClientException, URISyntaxException, InterruptedException {
        final Http2Client client = Http2Client.getInstance();
        final ClientConnection connection;
        URI uri = new URI("http://localhost:8080");
        try {
            connection = client.connect(uri, Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        CompletableFuture<ClientResponse> future;
        try {
            request.setPath("/v1/todoItems").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            request.getRequestHeaders().put(Headers.CONTENT_TYPE, "application/json");
            String requestBody = Config.getInstance().getMapper().writeValueAsString(responses.get(response));
            future = client.callService(uri, request, Optional.ofNullable(requestBody));
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        Thread.sleep(1200);
        return future;
    }



    private List<String> readLogFile() {
        File file = new File(logFile);
        List<String> result = new ArrayList<>();
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
            String strLine;
            while (true)   {
                if (!((strLine = br.readLine()) != null)) break;
                result.add(strLine);
            }
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private List<String> getErrorLinesFromLogFile() {
        List<String> lines = readLogFile();
        return lines.stream()
                .filter( s -> s.contains("Response validation error"))
                .collect(Collectors.toList());
    }
}
