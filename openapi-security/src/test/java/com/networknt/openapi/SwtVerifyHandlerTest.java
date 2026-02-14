package com.networknt.openapi;

import com.networknt.body.BodyConverter;
import com.networknt.client.Http2Client;
import com.networknt.client.simplepool.SimpleConnectionState;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.status.Status;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

@Ignore
public class SwtVerifyHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(SwtVerifyHandlerTest.class);

    static Undertow server = null;

    @BeforeClass
    public static void setUp() {
        if (server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            SwtVerifyHandler swtVerifyHandler = new SwtVerifyHandler();
            swtVerifyHandler.setNext(handler);
            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(swtVerifyHandler);
            server = Undertow.builder()
                    .addHttpListener(7080, "localhost")
                    .setHandler(openApiHandler)
                    .build();
            server.start();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (server != null) {
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
                .add(Methods.POST, "/oauth/introspection", exchange -> {
                    logger.trace("handling /oauth/introspection");
                    FormParserFactory formParserFactory = FormParserFactory.builder().build();
                    FormDataParser parser = formParserFactory.createParser(exchange);
                    String token = null;
                    if (parser != null) {
                        FormData formData = parser.parseBlocking();
                        token = (String)BodyConverter.convert(formData).get("token");
                    }
                    // get the clientId and clientSecret from the basic authorization header.
                    String authorization = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                    String credentials = authorization.substring(6);
                    int pos = credentials.indexOf(':');
                    if (pos == -1) {
                        credentials = new String(org.apache.commons.codec.binary.Base64.decodeBase64(credentials), UTF_8);
                    }

                    pos = credentials.indexOf(':');
                    if (pos != -1) {
                        String clientId = credentials.substring(0, pos);
                        String clientSecret = credentials.substring(pos + 1);
                        System.out.println("token = " + token + " clientId = " + clientId + " clientSecret = " + clientSecret);
                    }
                    String responseBody = "{\"active\":false}";
                    switch (token) {
                        case "valid_token":
                            responseBody = "{  \"active\": true,\"client_id\": \"bb8293f6-ceef-4e7a-90c8-1492e97df19f\",\"token_type\": \"refresh_token\",\"scope\": \"openid profile read:pets\",\"sub\": \"cn=odicuser,dc=example,dc=com\",\"exp\": \"86400\",\"iat\": \"1506513918\",\"iss\": \"https://wa.example.com\" }";
                            break;
                        case "invalid_scope":
                            responseBody = "{  \"active\": true,\"client_id\": \"bb8293f6-ceef-4e7a-90c8-1492e97df19f\",\"token_type\": \"refresh_token\",\"scope\": \"openid profile\",\"sub\": \"cn=odicuser,dc=example,dc=com\",\"exp\": \"86400\",\"iat\": \"1506513918\",\"iss\": \"https://wa.example.com\" }";
                            break;
                    }
                    exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
                    exchange.getResponseSender().send(responseBody);
                })
                .add(Methods.GET, "/v1/pets/{petId}", exchange -> {
                    Map<String, Object> examples = new HashMap<>();
                    examples.put("application/xml", StringEscapeUtils.unescapeHtml4("&lt;Pet&gt;  &lt;id&gt;123456&lt;/id&gt;  &lt;name&gt;doggie&lt;/name&gt;  &lt;photoUrls&gt;    &lt;photoUrls&gt;string&lt;/photoUrls&gt;  &lt;/photoUrls&gt;  &lt;tags&gt;  &lt;/tags&gt;  &lt;status&gt;string&lt;/status&gt;&lt;/Pet&gt;"));
                    examples.put("application/json", StringEscapeUtils.unescapeHtml4("{  &quot;photoUrls&quot; : [ &quot;aeiou&quot; ],  &quot;name&quot; : &quot;doggie&quot;,  &quot;id&quot; : 123456789,  &quot;category&quot; : {    &quot;name&quot; : &quot;aeiou&quot;,    &quot;id&quot; : 123456789  },  &quot;tags&quot; : [ {    &quot;name&quot; : &quot;aeiou&quot;,    &quot;id&quot; : 123456789  } ],  &quot;status&quot; : &quot;aeiou&quot;}"));
                    if (examples.size() > 0) {
                        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
                        exchange.getResponseSender().send((String) examples.get("application/json"));
                    } else {
                        exchange.endExchange();
                    }
                })
                .add(Methods.GET, "/v1/pets", exchange -> exchange.getResponseSender().send("get"));
    }

    @Test
    public void testWithCorrectScopeInIdToken() throws Exception {
        logger.trace("starting testWithCorrectScopeInIdToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer valid_token");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testWithCorrectScopeInIdTokenAndClientIdSecretInHeader() throws Exception {
        logger.trace("starting testWithCorrectScopeInIdTokenAndClientIdSecretInHeader");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer valid_token");
            request.getRequestHeaders().put(new HttpString("swt-client"), "new-client");
            request.getRequestHeaders().put(new HttpString("swt-secret"), "new-secret");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInIdToken() throws Exception {
        logger.trace("starting testUnmatchedScopeInIdToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer invalid_scope");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(403, statusCode);
        if (statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10005", status.getCode());
        }
    }

    /**
     * Ignore this test case as it is slow sometimes. Need to investigate.
     * @throws Exception
     */
    @Test
    @Ignore
    public void testWithCorrectScopeInScopeToken() throws Exception {
        logger.trace("starting testWithCorrectScopeInScopeToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer invalid_scope");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer valid_token");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInScopeToken() throws Exception {
        logger.trace("starting testUnmatchedScopeInScopeToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer invalid_scope");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer invalid_scope");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(403, statusCode);
        if (statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10006", status.getCode());
        }
    }

    @Test
    public void testH2CDisabledRequest() throws Exception {
        logger.trace("starting testH2CDisabledRequest");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer valid_token");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer valid_token");
            request.getRequestHeaders().put(Headers.CONNECTION, "upgrade");
            request.getRequestHeaders().put(Headers.UPGRADE, "foo/2");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(405, statusCode);
        if (statusCode == 405) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10008", status.getCode());
        }
    }

    @Test
    public void testEmptyAuthorizationHeader() throws Exception {
        logger.trace("starting testEmptyAuthorizationHeader");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {

            client.restore(token);

        }
        int statusCode = reference.get().getResponseCode();
        logger.debug("statusCode = " + statusCode);
        String responseBody = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        logger.debug("responseBody = " + responseBody);
        Assert.assertEquals(401, statusCode);
        if (statusCode == 401) {
            Status status = Config.getInstance().getMapper().readValue(responseBody, Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10000", status.getCode());
        }
    }

}
