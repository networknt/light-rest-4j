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
import org.apache.commons.codec.binary.Base64;
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

public class UnifiedSecurityHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(UnifiedSecurityHandlerTest.class);

    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final boolean enableHttp2 = server.getServerConfig().isEnableHttp2();
    static final boolean enableHttps = server.getServerConfig().isEnableHttps();
    static final int httpPort = server.getServerConfig().getHttpPort();
    static final int httpsPort = server.getServerConfig().getHttpsPort();
    static final String url = enableHttp2 || enableHttps ? "https://localhost:" + httpsPort : "http://localhost:" + httpPort;

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
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
    private static String encodeCredentialsFullFormat(String username, String password, String separator) {
        String cred;
        if(password != null) {
            cred = username + separator + password;
        } else {
            cred = username;
        }
        String encodedValue;
        byte[] encodedBytes = Base64.encodeBase64(cred.getBytes(UTF_8));
        encodedValue = new String(encodedBytes, UTF_8);
        return encodedValue;
    }

    private static String encodeCredentials(String username, String password) {
        return encodeCredentialsFullFormat(username, password, ":");
    }

    /**
     * Send a request without authorization header but put the request path prefix into the anonymousPrefixes list. We are expecting
     * 200 status code as all the security methods bypassed.
     *
     * @throws Exception
     */
    @Test
    public void testAnonymousPrefix() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/dogs/111").setMethod(Methods.GET);
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
        Assert.assertEquals(200, statusCode);
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    /**
     * Test basic header to ensure that BasicAuthHandler is invoked and the validation is done correctly. For this request,
     * we have passed the right credentials to the basic header and we are expecting 200 response.
     */
    @Test
    public void testWithBasicHeader() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/salesforce").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "BASIC " + encodeCredentials("user1", "user1pass"));
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
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    /**
     * Test basic authentication with a wrong password and expecting an error message with 401 status code.
     * @throws Exception
     */
    @Test
    public void testWithBasicHeaderWithWrongPassword() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/salesforce").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "BASIC " + encodeCredentials("user1", "wrong"));
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
        logger.debug("statusCode = " + statusCode);
        logger.debug("responseBody = " + responseBody);
        Assert.assertEquals(401, statusCode);
        if (statusCode == 401) {
            Assert.assertTrue(responseBody.contains("INVALID_USERNAME_OR_PASSWORD"));
        }
    }

    /**
     * Test basic authentication with a basic prefix and a space for the authorization header. Expecting an error message with
     * status code 401.
     * @throws Exception
     */
    @Test
    public void testBasicWithSpace() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/salesforce").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "BASIC ");
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
        logger.debug("statusCode = " + statusCode);
        logger.debug("responseBody = " + responseBody);
        Assert.assertEquals(401, statusCode);
        if (statusCode == 401) {
            Assert.assertTrue(responseBody.contains("INVALID_AUTHORIZATION_HEADER"));
        }
    }

    /**
     * Test apikey header to ensure that ApiKeyHandler is invoked and expect the right response.
     */
    @Test
    public void testWithApiKeyHeader() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/test1").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(new HttpString("x-gateway-apikey"), "abcdefg");
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
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    /**
     * Test apikey header to ensure that ApiKeyHandler is invoked and expect an error response as the apikey is wrong
     */
    @Test
    public void testWithApiKeyHeaderWongKey() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/test1").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(new HttpString("x-gateway-apikey"), "wrong");
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
        logger.debug("statusCode = " + statusCode);
        logger.debug("responseBody = " + responseBody);
        Assert.assertEquals(401, statusCode);
        if (statusCode == 401) {
            Assert.assertTrue(responseBody.contains("API_KEY_MISMATCH"));
        }
    }

    /**
     * Test a path that is not configured in the pathPrefixAuths. Expect an error response.
     * @throws Exception
     */
    @Test
    public void testWrongPathNotConfigured() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/wrong").setMethod(Methods.GET);
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
        String responseBody = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        logger.debug("statusCode = " + statusCode);
        logger.debug("responseBody = " + responseBody);
        Assert.assertEquals(400, statusCode);
        if (statusCode == 400) {
            Assert.assertTrue(responseBody.contains("MISSING_PATH_PREFIX_AUTH"));
        }
    }


    /**
     * Test space separated scopes with the key 'scp'
     */
    @Test
    public void testWithCorrectSpaceSeparatedScpClaimScopeInIdToken() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTkxOTc4NjIxNiwianRpIjoiNjRoVWRiNWJ0OFYzRkJzOFZDNXZRQSIsImlhdCI6MTYwNDQyNjIxNiwibmJmIjoxNjA0NDI2MDk2LCJ2ZXJzaW9uIjoiMS4wIiwiY2xpZW50X2lkIjoiZjdkNDIzNDgtYzY0Ny00ZWZiLWE1MmQtNGM1Nzg3NDIxZTczIiwic2NwIjoid3JpdGU6cGV0cyByZWFkOnBldHMifQ.MPRUBQRbN-13poJ1XV0jHuJgGbOuOglojDzQScEo7WU2UwHseLl_HaZqPEHm-eW8AAmLZ_tzKlchAJR7OVP3CPWgsQNrb2uR3uf4dgSBdD2ZmMPdT1m6KAFhNVzwsEx3vdweL6OlZMm3x03nz3eIRKW8gdGoeTq08HGOzTjKpsFYVMSgdv6nf0HfyOeg5dhByVsdqnhKdig3bMyaHo4HlKQfN-eSaMusG9QPDbQoP0IBWRrFlv63iNrEm5EX9zx6K81awWR7K5Iu_WIJkGZU_Fm0qHee9Ur4_1OdXLOLRKIvNE150jS7vX_a0YGHteLgkvAjs_AtVaUzVnAnHE46lw");
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
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }


    /**
     * Test space separated scopes with the key 'scope'
     */
    @Test
    public void testWithCorrectSpaceSeparatedScopeClaimScopeInIdToken() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTkxOTc4NjIxNiwianRpIjoid1RtZFhWRE83VHVaaWw1TG5YYks1USIsImlhdCI6MTYwNDQyNjIxNiwibmJmIjoxNjA0NDI2MDk2LCJ2ZXJzaW9uIjoiMS4wIiwiY2xpZW50X2lkIjoiZjdkNDIzNDgtYzY0Ny00ZWZiLWE1MmQtNGM1Nzg3NDIxZTczIiwic2NvcGUiOiJ3cml0ZTpwZXRzIHJlYWQ6cGV0cyJ9.P4WSx19ueJDKDZBLvy_esrvQpGaeKwHpCnXtf7o89XXKkpRlAyFlJj4bkclHi8H-gi1g8xqnna2ygKVQUbcjzPDt2ks8ZpZTqRAeYQP6dWJZXEww_VV_DSJZTLNq_zjN9JGllvO5A3C3SdV536V0P7w249mSL4JXFaAwdMgnmnneTdP54qyaGH9w0QYjffdx8ODG8JMq-YY434jQ8q81hXKxu5OF1kOpGSqA7bJ3_kAtx5aYPtoxOv4xwv_-ear2meKbMTo0yKVNIhXI6GlfUEiJ1tgZ0Ni89XBxTMaEy7I0t3rvB0ko9ONTyOtnH3cLdwQeqnTP6-TMps1WUuxYxQ");
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
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInIdToken() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiTVJiZHdlQ295eG13a2ZUM3lVWGloQSIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6ImVyaWMiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInNjb3BlIjpbIkFUTVAxMDAwLnciLCJBVE1QMTAwMC5yIl19.VOEggO6UIMHNJLrxShGivCh7sGyHiz7h9FqDjlKwywGP9xKbVTTODy2-FitUaS1Y2vjiHlJ0TNyxmj1SO11YwYnJlW1zn-6vfKWKI70DyvRwsvSX_8Z2fj0jPUiBqezwKRtLCHSsmiEpMrW6YQHYw0qzZ9kkMhiH2uFpZNCekOQWL1piRn1xVQkUmeFiTDvJQESHadFzw-9x0klO7-SxgKeHHDroxnpbLv2j795oMTB1gM_wJP6HO_M-gK6N1Uh6zssfnbyFReRNWkhZFOp3Y8DvwpfKhqXIVGUc_5WsO9M-y66icClVNl5zwLSmjsrNtqZkmeBCwQ6skBnRLfMocQ");
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
        if (statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10005", status.getCode());
        }
    }

    @Test
    public void testWithCorrectScopeInScopeToken() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiV0Z1VVZneE83dmxKUm5XUlllMjE1dyIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJ3cml0ZTpwZXRzIiwicmVhZDpwZXRzIl19.ZDlD_JbtHMqfx8EWOlOXI0zFGjB_pJ6yXWpxoE03o2yQnCUq1zypaDTJWSiy-BPIiQAxwDV09L3SN7RsOcgJ3y2LLFhgqIXhcHoePxoz52LPOeeiihG2kcrgBm-_VMq0uUykLrD-ljSmmSm1Hai_dx0WiYGAEJf-TiD1mgzIUTlhogYrjFKlp2NaYHxr7yjzEGefKv4DWdjtlEMmX_cXkqPgxra_omzyxeWE-n0b7f_r7Hr5HkxnmZ23gkZcvFXfVWKEp2t0_dYmNCbSVDavAjNanvmWsNThYNglFRvF0lm8kl7jkfMO1pTa0WLcBLvOO2y_jRWjieFCrc0ksbIrXA");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiV0Z1VVZneE83dmxKUm5XUlllMjE1dyIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJ3cml0ZTpwZXRzIiwicmVhZDpwZXRzIl19.ZDlD_JbtHMqfx8EWOlOXI0zFGjB_pJ6yXWpxoE03o2yQnCUq1zypaDTJWSiy-BPIiQAxwDV09L3SN7RsOcgJ3y2LLFhgqIXhcHoePxoz52LPOeeiihG2kcrgBm-_VMq0uUykLrD-ljSmmSm1Hai_dx0WiYGAEJf-TiD1mgzIUTlhogYrjFKlp2NaYHxr7yjzEGefKv4DWdjtlEMmX_cXkqPgxra_omzyxeWE-n0b7f_r7Hr5HkxnmZ23gkZcvFXfVWKEp2t0_dYmNCbSVDavAjNanvmWsNThYNglFRvF0lm8kl7jkfMO1pTa0WLcBLvOO2y_jRWjieFCrc0ksbIrXA");
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
        if (statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInScopeToken() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiTVJiZHdlQ295eG13a2ZUM3lVWGloQSIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6ImVyaWMiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInNjb3BlIjpbIkFUTVAxMDAwLnciLCJBVE1QMTAwMC5yIl19.VOEggO6UIMHNJLrxShGivCh7sGyHiz7h9FqDjlKwywGP9xKbVTTODy2-FitUaS1Y2vjiHlJ0TNyxmj1SO11YwYnJlW1zn-6vfKWKI70DyvRwsvSX_8Z2fj0jPUiBqezwKRtLCHSsmiEpMrW6YQHYw0qzZ9kkMhiH2uFpZNCekOQWL1piRn1xVQkUmeFiTDvJQESHadFzw-9x0klO7-SxgKeHHDroxnpbLv2j795oMTB1gM_wJP6HO_M-gK6N1Uh6zssfnbyFReRNWkhZFOp3Y8DvwpfKhqXIVGUc_5WsO9M-y66icClVNl5zwLSmjsrNtqZkmeBCwQ6skBnRLfMocQ");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiTVJiZHdlQ295eG13a2ZUM3lVWGloQSIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6ImVyaWMiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInNjb3BlIjpbIkFUTVAxMDAwLnciLCJBVE1QMTAwMC5yIl19.VOEggO6UIMHNJLrxShGivCh7sGyHiz7h9FqDjlKwywGP9xKbVTTODy2-FitUaS1Y2vjiHlJ0TNyxmj1SO11YwYnJlW1zn-6vfKWKI70DyvRwsvSX_8Z2fj0jPUiBqezwKRtLCHSsmiEpMrW6YQHYw0qzZ9kkMhiH2uFpZNCekOQWL1piRn1xVQkUmeFiTDvJQESHadFzw-9x0klO7-SxgKeHHDroxnpbLv2j795oMTB1gM_wJP6HO_M-gK6N1Uh6zssfnbyFReRNWkhZFOp3Y8DvwpfKhqXIVGUc_5WsO9M-y66icClVNl5zwLSmjsrNtqZkmeBCwQ6skBnRLfMocQ");
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
        if (statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10006", status.getCode());
        }
    }

    @Test
    public void testH2CDisabledRequest() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI("http://localhost:7080"), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        try {
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiTVJiZHdlQ295eG13a2ZUM3lVWGloQSIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6ImVyaWMiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInNjb3BlIjpbIkFUTVAxMDAwLnciLCJBVE1QMTAwMC5yIl19.VOEggO6UIMHNJLrxShGivCh7sGyHiz7h9FqDjlKwywGP9xKbVTTODy2-FitUaS1Y2vjiHlJ0TNyxmj1SO11YwYnJlW1zn-6vfKWKI70DyvRwsvSX_8Z2fj0jPUiBqezwKRtLCHSsmiEpMrW6YQHYw0qzZ9kkMhiH2uFpZNCekOQWL1piRn1xVQkUmeFiTDvJQESHadFzw-9x0klO7-SxgKeHHDroxnpbLv2j795oMTB1gM_wJP6HO_M-gK6N1Uh6zssfnbyFReRNWkhZFOp3Y8DvwpfKhqXIVGUc_5WsO9M-y66icClVNl5zwLSmjsrNtqZkmeBCwQ6skBnRLfMocQ");
            request.getRequestHeaders().put(HttpStringConstants.SCOPE_TOKEN, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiTVJiZHdlQ295eG13a2ZUM3lVWGloQSIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6ImVyaWMiLCJ1c2VyX3R5cGUiOiJFTVBMT1lFRSIsImNsaWVudF9pZCI6ImY3ZDQyMzQ4LWM2NDctNGVmYi1hNTJkLTRjNTc4NzQyMWU3MiIsInNjb3BlIjpbIkFUTVAxMDAwLnciLCJBVE1QMTAwMC5yIl19.VOEggO6UIMHNJLrxShGivCh7sGyHiz7h9FqDjlKwywGP9xKbVTTODy2-FitUaS1Y2vjiHlJ0TNyxmj1SO11YwYnJlW1zn-6vfKWKI70DyvRwsvSX_8Z2fj0jPUiBqezwKRtLCHSsmiEpMrW6YQHYw0qzZ9kkMhiH2uFpZNCekOQWL1piRn1xVQkUmeFiTDvJQESHadFzw-9x0klO7-SxgKeHHDroxnpbLv2j795oMTB1gM_wJP6HO_M-gK6N1Uh6zssfnbyFReRNWkhZFOp3Y8DvwpfKhqXIVGUc_5WsO9M-y66icClVNl5zwLSmjsrNtqZkmeBCwQ6skBnRLfMocQ");
            request.getRequestHeaders().put(Headers.CONNECTION, "upgrade");
            request.getRequestHeaders().put(Headers.UPGRADE, "foo/2");
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
        if (statusCode == 405) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10008", status.getCode());
        }
    }

}
