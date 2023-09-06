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

import com.networknt.client.Http2Client;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.status.Status;
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
import org.apache.commons.text.StringEscapeUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
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
 * Created by steve on 01/09/16.
 */
public class JwtVerifyHandlerTest {
    static final Logger logger = LoggerFactory.getLogger(JwtVerifyHandlerTest.class);

    static Undertow server = null;

    @BeforeClass
    public static void setUp() {
        if (server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            JwtVerifyHandler jwtVerifyHandler = new JwtVerifyHandler();
            jwtVerifyHandler.setNext(handler);
            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(jwtVerifyHandler);
            server = Undertow.builder()
                    .addHttpListener(7081, "localhost")
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
        logger.trace("Start testWithCorrectScopeInIdToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTgwNTEzNjU1MSwianRpIjoiV0Z1VVZneE83dmxKUm5XUlllMjE1dyIsImlhdCI6MTQ4OTc3NjU1MSwibmJmIjoxNDg5Nzc2NDMxLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJ3cml0ZTpwZXRzIiwicmVhZDpwZXRzIl19.ZDlD_JbtHMqfx8EWOlOXI0zFGjB_pJ6yXWpxoE03o2yQnCUq1zypaDTJWSiy-BPIiQAxwDV09L3SN7RsOcgJ3y2LLFhgqIXhcHoePxoz52LPOeeiihG2kcrgBm-_VMq0uUykLrD-ljSmmSm1Hai_dx0WiYGAEJf-TiD1mgzIUTlhogYrjFKlp2NaYHxr7yjzEGefKv4DWdjtlEMmX_cXkqPgxra_omzyxeWE-n0b7f_r7Hr5HkxnmZ23gkZcvFXfVWKEp2t0_dYmNCbSVDavAjNanvmWsNThYNglFRvF0lm8kl7jkfMO1pTa0WLcBLvOO2y_jRWjieFCrc0ksbIrXA");
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
     * Test comma seperated scopes with the key 'scp'
     */
    @Test
    public void testWithCorrectCommaSeperatedScpClaimScopeInIdToken() throws Exception {
        logger.trace("Start testWithCorrectCommaSeperatedScpClaimScopeInIdToken");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTkxOTc4NjIxNiwianRpIjoicGlxZjE4QlhvbzFja0lraFJNMEVSQSIsImlhdCI6MTYwNDQyNjIxNiwibmJmIjoxNjA0NDI2MDk2LCJ2ZXJzaW9uIjoiMS4wIiwiY2xpZW50X2lkIjoiZjdkNDIzNDgtYzY0Ny00ZWZiLWE1MmQtNGM1Nzg3NDIxZTczIiwic2NwIjpbIndyaXRlOnBldHMiLCJyZWFkOnBldHMiXX0.U1JoPHEMXqbmCa_3aABzIN4DKmX702Q7BQxyzmr7yCLWRD4jqAvacdk-F6_mdYmxBXF8KY-sEO4AifaJA2picr5DPIabbbDtSEr9vrd3HaDMXx3c5CyiTg1U0DVmJh__OP8GJdVVhKVnrj36bvY_xLrWE9cbgTnRp4XMhWwHYHTVGNo6qP_vRRRMTZD4elNlbMxLnmfIy0XM5k3WuoEteJnRnG9ePJaGnRGcd6WkYWW1lfFD-YO2seNA4eegzouaV1qc7dpmYXuaXo5DvUr-oHcj1Aj6Q7XBAoZsXWbS8LhHH-dn_bJFzJnWA420p6wrEeJKDhdm4AWtzrAKxRQpYw");
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
     * Test space seperated scopes with the key 'scp'
     */
    @Test
    public void testWithCorrectSpaceSeperatedScpClaimScopeInIdToken() throws Exception {
        logger.trace("Start testWithCorrectSpaceSeperatedScpClaimScopeInIdToken");
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
     * Test space seperated scopes with the key 'scope'
     */
    @Test
    public void testWithCorrectSpaceSeperatedScopeClaimScopeInIdToken() throws Exception {
        logger.trace("Start testWithCorrectSpaceSeperatedScopeClaimScopeInIdToken");
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
        logger.trace("Start testUnmatchedScopeInIdToken");
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
        logger.trace("Start testWithCorrectScopeInScopeToken");
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
        logger.trace("Start testUnmatchedScopeInScopeToken");
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
        logger.trace("Start testH2CDisabledRequest");
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

    @Test
    public void testEmptyAuthorizationHeader() throws Exception {
        logger.trace("Start testEmptyAuthorizationHeader");
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
            ClientRequest request = new ClientRequest().setPath("/v1/pets/111").setMethod(Methods.GET);
            request.getRequestHeaders().put(Headers.HOST, "localhost");
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "");
            connection.sendRequest(request, client.createClientCallback(reference, latch));
            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
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
