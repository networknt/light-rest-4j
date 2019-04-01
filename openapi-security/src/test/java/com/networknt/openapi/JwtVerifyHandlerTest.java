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
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = getTestHandler();
            JwtVerifyHandler jwtVerifyHandler = new JwtVerifyHandler();
            jwtVerifyHandler.setNext(handler);
            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(jwtVerifyHandler);
            server = Undertow.builder()
                    .addHttpListener(8080, "localhost")
                    .setHandler(openApiHandler)
                    .build();
            server.start();
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

    static RoutingHandler getTestHandler() {
        return Handlers.routing()
                .add(Methods.GET, "/v1/pets/{petId}", exchange -> {
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
                .add(Methods.GET, "/v1/pets", exchange -> exchange.getResponseSender().send("get"));
    }

    @Test
    public void testWithRightScopeInIdToken() throws Exception {
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
        if(statusCode == 200) {
            Assert.assertNotNull(reference.get().getAttachment(Http2Client.RESPONSE_BODY));
        }
    }

    @Test
    public void testUnmatchedScopeInIdToken() throws Exception {
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
        if(statusCode == 403) {
            Status status = Config.getInstance().getMapper().readValue(reference.get().getAttachment(Http2Client.RESPONSE_BODY), Status.class);
            Assert.assertNotNull(status);
            Assert.assertEquals("ERR10005", status.getCode());
        }
    }

}
