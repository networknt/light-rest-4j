package com.networknt.openapi;

import com.networknt.client.Http2Client;
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

/**
 * This is a test class that focuses the multiple specifications with scope verification. It has a customized
 * openapi-handler.yml config file injected during the setup.
 *
 *
 */
public class JwtVerifierHandlerMultipleSpecsTest {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifierHandlerMultipleSpecsTest.class);

    static Undertow server1 = null;
    static Undertow server2 = null;
    @BeforeClass
    public static void setUp() {
        if (server1 == null) {
            logger.info("starting server1");
            HttpHandler handler = getTestHandler();
            JwtVerifyHandler jwtVerifyHandler = new JwtVerifyHandler();
            jwtVerifyHandler.setNext(handler);
            OpenApiHandler openApiHandler = new OpenApiHandler(OpenApiHandlerConfig.load("openapi-handler-multiple"));
            openApiHandler.setNext(jwtVerifyHandler);
            server1 = Undertow.builder()
                    .addHttpListener(7081, "localhost")
                    .setHandler(openApiHandler)
                    .build();
            server1.start();
        }

        if (server2 == null) {
            logger.info("starting server2");
            HttpHandler handler = getJwksHandler();
            server2 = Undertow.builder()
                    .addHttpListener(7082, "localhost")
                    .setHandler(handler)
                    .build();
            server2.start();
        }

    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (server1 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server1.stop();
            logger.info("The server1 is stopped.");
        }
        if (server2 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server2.stop();
            logger.info("The server2 is stopped.");
        }

    }

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/petstore/pets/{petId}", exchange -> {
                    Map<String, Object> examples = new HashMap<>();
                    examples.put("application/xml", StringEscapeUtils.unescapeHtml4("&lt;Pet&gt;  &lt;id&gt;123456&lt;/id&gt;  &lt;name&gt;doggie&lt;/name&gt;  &lt;photoUrls&gt;    &lt;photoUrls&gt;string&lt;/photoUrls&gt;  &lt;/photoUrls&gt;  &lt;tags&gt;  &lt;/tags&gt;  &lt;status&gt;string&lt;/status&gt;&lt;/Pet&gt;"));
                    examples.put("application/json", StringEscapeUtils.unescapeHtml4("{  &quot;photoUrls&quot; : [ &quot;aeiou&quot; ],  &quot;name&quot; : &quot;doggie&quot;,  &quot;id&quot; : 123456789,  &quot;category&quot; : {    &quot;name&quot; : &quot;aeiou&quot;,    &quot;id&quot; : 123456789  },  &quot;tags&quot; : [ {    &quot;name&quot; : &quot;aeiou&quot;,    &quot;id&quot; : 123456789  } ],  &quot;status&quot; : &quot;aeiou&quot;}"));
                    if(examples.size() > 0) {
                        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
                        exchange.getResponseSender().send((String)examples.get("application/json"));
                    } else {
                        exchange.endExchange();
                    }
                })
                .add(Methods.GET, "/petstore/pets", exchange -> exchange.getResponseSender().send("get"));
    }

    static RoutingHandler getJwksHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/oauth2/N2CMw0HGQXeLvC1wBfln2A/keys", exchange -> {
                    exchange.getResponseHeaders().add(new HttpString("Content-Type"), "application/json");
                    exchange.getResponseSender().send("{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"Tj_l_tIBTginOtQbL0Pv5w\",\"n\":\"0YRbWAb1FGDpPUUcrIpJC6BwlswlKMS-z2wMAobdo0BNxNa7hG_gIHVPkXu14Jfo1JhUhS4wES3DdY3a6olqPcRN1TCCUVHd-1TLd1BBS-yq9tdJ6HCewhe5fXonaRRKwutvoH7i_eR4m3fQ1GoVzVAA3IngpTr4ptnM3Ef3fj-5wZYmitzrRUyQtfARTl3qGaXP_g8pHFAP0zrNVvOnV-jcNMKm8YZNcgcs1SuLSFtUDXpf7Nr2_xOhiNM-biES6Dza1sMLrlxULFuctudO9lykB7yFh3LHMxtIZyIUHuy0RbjuOGC5PmDowLttZpPI_j4ynJHAaAWr8Ddz764WdQ\",\"e\":\"AQAB\"}]}");
                });

    }

    @Test
    public void testWithCorrectScopeInIdToken() throws Exception {
        logger.trace("testWithCorrectScopeInIdToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTc4MDgxMywianRpIjoiWXkyN3VMa2FhaXlBRzZoazR5a2JtZyIsImlhdCI6MTcwNDQyMDgxMywibmJmIjoxNzA0NDIwNjkzLCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoic3RldmUiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInJvbGVzIjoidXNlciIsInNjb3BlIjpbIndyaXRlOnBldHMiLCJyZWFkOnBldHMiXX0.o4WIuyAZ1SBsWSDfMnOjPtB9fuf53boMLlxAAfoZBYD33BlN5FZI4tA59KDxIH39dnwCpsr4Bsx3jT2FMZ_zvXdH1PLZGYnVQN9u5nKXjvfEEJgHHN8KY8lWMdLjLYIZgPLXpeOIiU0SXoF2-mEB_Pb4FqfaF4vPySGjLygMX6AHqTRlVXVWgrITDWxJfaBF8iCQ3K4FZyHNvMqyn2QpUwj9QI7_yccCTi2sWPxp2J4HcBj2CHmiM1RfWmwCyeOkw5rhQBrJThAjal6eqSUtJoiZ5XKiD3VQBzsL0Vhemk5xUenyCzmCCAC-T4geQ-rpTSRAKB5b2918yK5sxBrQ0A");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        String responseBody = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        System.out.println("statusCode = " + statusCode + " responseBody = " + responseBody);
        Assert.assertEquals(200, statusCode);
        if(statusCode == 200) {
            Assert.assertNotNull(responseBody);
        }
    }

    /**
     * Test comma seperated scopes with the key 'scp'
     */
    @Test
    public void testWithCorrectCommaSeperatedScpClaimScopeInIdToken() throws Exception {
        logger.trace("testWithCorrectCommaSeperatedScpClaimScopeInIdToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTc4MTE4NywianRpIjoiNWxIOXE4SThKMUtpRTNOSFNsaWxGQSIsImlhdCI6MTcwNDQyMTE4NywibmJmIjoxNzA0NDIxMDY3LCJ2ZXJzaW9uIjoiJzEuMCciLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzMiLCJzY3AiOlsid3JpdGU6cGV0cyIsInJlYWQ6cGV0cyJdfQ.Xii98zA5XwxBUTDvGSjimjCOcaCTy_4QbJc1kReIxKDVoO_knRbJ_3AzOsm1KSTINrCG3Yh8DuvMFQHKPbDntQtu-yqsY_kFHyFn8m71LsId5Giatf7WWRZQXcLdxFZH9VZWyhQutHXC0bsVJIhlQMDEHVgZ9LDUXvq4Et6zCCa8BkWLEy1joS-Cd748M9Atq2n-ZKdLxC4gRpAch5aUXwDz_oTyA2y4ZVEJQkekU4yjcB08s4jkECSoZfzekhXpbunXj36qtkdAIPqpXzD-p5qWiUgRRj_eZYvuIyh82emvxLIb28pe9Zru3laVSma7HpqDdNHEvEeVyFh8ALIkaQ");
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
        if(statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    /**
     * Test space seperated scopes with the key 'scp'
     */
    @Test
    public void testWithCorrectSpaceSeperatedScpClaimScopeInIdToken() throws Exception {
        logger.trace("testWithCorrectSpaceSeperatedScpClaimScopeInIdToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMTI3NCwianRpIjoiM0FXbDQzTDBkVUZrdW5HQkpZXzBvUSIsImlhdCI6MTcwNDQ2MTI3NCwibmJmIjoxNzA0NDYxMTU0LCJ2ZXJzaW9uIjoiJzEuMCciLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzMiLCJzY3AiOiJ3cml0ZTpwZXRzIHJlYWQ6cGV0cyJ9.ptFGE2t1zpayz8vnBrrOuYiB3TxvzFDRf9P5DtGE8uml4QPoiG2Fvr_0-ngVr3ibgrGGxCTVMr55YEVxALjJk77VwiQt3fcG5ptfJkWWfSwBfcGZ3Ee5qdCVoVv4Ww7ir-8eLFIKiqNdAgPoUjj6cizLjGDg-6mCEzaK3xp3XOSdb96esMx85VDYf711kwY1XC_wthCKB9P8eN_vk_Qfy2wWbEWthiJYMxLAD-WSyOIHK7C2K_CYQ5naLlX3z5pzYoDUiIZEly8J775AcbmvG2gmlD9Ixc_xyv5jD0dnWrm4YUp4_P6k4t840gy1-9Hc1XGuMiRS2WacOjPZ-8nYKA");
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
        if(statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    /**
     * Test space seperated scopes with the key 'scope'
     */
    @Test
    public void testWithCorrectSpaceSeperatedScopeClaimScopeInIdToken() throws Exception {
        logger.trace("testWithCorrectSpaceSeperatedScopeClaimScopeInIdToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMTY0OSwianRpIjoiS3hubEZGc1BmS3BkcFpDTTBRTWpVdyIsImlhdCI6MTcwNDQ2MTY0OSwibmJmIjoxNzA0NDYxNTI5LCJ2ZXJzaW9uIjoiJzEuMCciLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzMiLCJzY29wZSI6IndyaXRlOnBldHMgcmVhZDpwZXRzIn0.f-fazxnL4lO3epAFsu7nUyNzP6J66o4gIuLWQI4Unfb-B4d1RZfkHrmINsE_AMonspf8FLFExGlDCdoLzHBH60kauzye51HIFNsLA8LIWrpCJ-4PNdQ7moHd8d_7xkyY3L4mhXv6zbd81W5JxHiDhsdixjM0ioDGEQhwuCdJNNuYloSJtITu1IOl9JLc_h1t_vEbVGr9midrp2bpu7vOltYx_waHSjCKuwus7PeERk9MAyHWnnV576imxRef2QY6i6Vl5Uni_MbC2dSFSq3nAWyWKBhUlVasmh9xLw5JhaWHxoPqyDxe5E9Q_w2t1XqpCqv5s0y5pnCBSO4AhNw9Pg");
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
        if(statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInIdToken() throws Exception {
        logger.trace("testUnmatchedScopeInIdToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMjA1MCwianRpIjoiN2Jmd2pONjl3djRoSmlPbHJ2TEpSQSIsImlhdCI6MTcwNDQ2MjA1MCwibmJmIjoxNzA0NDYxOTMwLCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoiZXJpYyIsInVzZXJfdHlwZSI6IkVNUExPWUVFIiwiY2xpZW50X2lkIjoiZjdkNDIzNDgtYzY0Ny00ZWZiLWE1MmQtNGM1Nzg3NDIxZTcyIiwicm9sZXMiOiJ1c2VyIiwic2NvcGUiOlsiQVRNUDEwMDAudyIsIkFUTVAxMDAwLnIiXX0.b0Tm8kUpbc0fuL60EVpr5WSXLbikqAl4AR1DGMrORGEFw7FXYR0mLhs7aCmvoyZLdTrNFVsq_nWGxjdK3Fh3SY04hdBTf_rhoDMqgx1isIT7-oLWLLzX1hWOiDNZA2FtHwXMfytJeK19nmgaHVsQyd4rq7gdYaWFGL4Wz4XQnk11MSDoYeZAVl1a4QfN3yZ1qrzFegR50cK7_JOsa1R4TETRBwdCJWXCBXCcxC6f6UDGCENbEhbCC_VLKWo3DCKp8KJ7HnLGDovl3XkZP1spNu9oZMhf--fakw8H9c0Hg8DUxCHyT0tc1o-jPM4KDNwOKzSCGar8xrRL-6cyFOmjGw");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(403, statusCode);
        if(statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10005", status.getCode());
        }
    }

    @Test
    public void testWithCorrectScopeInScopeToken() throws Exception {
        logger.trace("testWithCorrectScopeInScopeToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMjE5MiwianRpIjoiLU42Um4zdng5LUxiLUJkaXZUZ0I3QSIsImlhdCI6MTcwNDQ2MjE5MiwibmJmIjoxNzA0NDYyMDcyLCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoic3RldmUiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInJvbGVzIjoidXNlciIsInNjb3BlIjpbIndyaXRlOnBldHMiLCJyZWFkOnBldHMiXX0.QvOeXHZIJuEMKqABtgvkDA0EpktvrCuOIlMbO4sVJq9epLDPCki3d0ZRJrJt3dFMq9kTcG6909iWVHAdAsBa2GBgyMugkd9JGJICf1Tk40YCMDJaXLtw9pSOBx1WcaYpwVBAZvvtUEY6j6P8jdZnx8j5iWLUOlsh2L35bGJIC6GJpOgr0kd4MeC4ePh5-6M8s5DI2TxQltILqoCk-uUOusLSiAei3L7NMzNjtcS9khhnZ52BSAOUMnkC4F_gTWRi5tXcV8sB0StorHdIPCCjZwNsvdIlPsZJqRMgQqn4cUzx3Y-la9tCHtRIoTSMYsQeA4QtDRSfGSzPdTpisV6nDg");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMjM1OSwianRpIjoiZDVYTnREWk9YeENxNVZDRU5mLW5CZyIsImlhdCI6MTcwNDQ2MjM1OSwibmJmIjoxNzA0NDYyMjM5LCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoic3RldmUiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInJvbGVzIjoidXNlciIsInNjb3BlIjpbIndyaXRlOnBldHMiLCJyZWFkOnBldHMiXX0.WmTPRlbezRj1HkxTlaP20h4lDvTpsGJJZkoynKCSlEfAYwuIiKXanv6jhy_u5xyWIri-TKZKkDBiDMWUCUss4BwvTDJwwJyeDnx5nreypmL-sc_ijfFoBP8mz6ZrkT8QCaC5jpnMRo4AelfNHdVqMHonco08_uSNxvNNOgGXNleRb5NsdYqdJ5Rhfyytg70bJzw7Up4uh-wEuJk-_gg-OUyppmhddZ83UEmmk5hGssjDNlHZ1UzIUl2AZGvCwkXSNv_vgkDKGSOG_XLvJ10Zcv28Fg6CPpWJFIru5RsaZtJHCK_kQ4oGkyKMIB9zyvheG93LJUOBxKg8qVVxry_E4g");
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
        if(statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInScopeToken() throws Exception {
        logger.trace("testUnmatchedScopeInScopeToken starts");
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7081"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/petstore/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMjE5MiwianRpIjoiLU42Um4zdng5LUxiLUJkaXZUZ0I3QSIsImlhdCI6MTcwNDQ2MjE5MiwibmJmIjoxNzA0NDYyMDcyLCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoic3RldmUiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInJvbGVzIjoidXNlciIsInNjb3BlIjpbIndyaXRlOnBldHMiLCJyZWFkOnBldHMiXX0.QvOeXHZIJuEMKqABtgvkDA0EpktvrCuOIlMbO4sVJq9epLDPCki3d0ZRJrJt3dFMq9kTcG6909iWVHAdAsBa2GBgyMugkd9JGJICf1Tk40YCMDJaXLtw9pSOBx1WcaYpwVBAZvvtUEY6j6P8jdZnx8j5iWLUOlsh2L35bGJIC6GJpOgr0kd4MeC4ePh5-6M8s5DI2TxQltILqoCk-uUOusLSiAei3L7NMzNjtcS9khhnZ52BSAOUMnkC4F_gTWRi5tXcV8sB0StorHdIPCCjZwNsvdIlPsZJqRMgQqn4cUzx3Y-la9tCHtRIoTSMYsQeA4QtDRSfGSzPdTpisV6nDg");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer eyJraWQiOiJUal9sX3RJQlRnaW5PdFFiTDBQdjV3IiwiYWxnIjoiUlMyNTYifQ.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MjAxOTgyMjA1MCwianRpIjoiN2Jmd2pONjl3djRoSmlPbHJ2TEpSQSIsImlhdCI6MTcwNDQ2MjA1MCwibmJmIjoxNzA0NDYxOTMwLCJ2ZXJzaW9uIjoiJzEuMCciLCJ1c2VyX2lkIjoiZXJpYyIsInVzZXJfdHlwZSI6IkVNUExPWUVFIiwiY2xpZW50X2lkIjoiZjdkNDIzNDgtYzY0Ny00ZWZiLWE1MmQtNGM1Nzg3NDIxZTcyIiwicm9sZXMiOiJ1c2VyIiwic2NvcGUiOlsiQVRNUDEwMDAudyIsIkFUTVAxMDAwLnIiXX0.b0Tm8kUpbc0fuL60EVpr5WSXLbikqAl4AR1DGMrORGEFw7FXYR0mLhs7aCmvoyZLdTrNFVsq_nWGxjdK3Fh3SY04hdBTf_rhoDMqgx1isIT7-oLWLLzX1hWOiDNZA2FtHwXMfytJeK19nmgaHVsQyd4rq7gdYaWFGL4Wz4XQnk11MSDoYeZAVl1a4QfN3yZ1qrzFegR50cK7_JOsa1R4TETRBwdCJWXCBXCcxC6f6UDGCENbEhbCC_VLKWo3DCKp8KJ7HnLGDovl3XkZP1spNu9oZMhf--fakw8H9c0Hg8DUxCHyT0tc1o-jPM4KDNwOKzSCGar8xrRL-6cyFOmjGw");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(403, statusCode);
        if(statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10006", status.getCode());
        }
    }

}
