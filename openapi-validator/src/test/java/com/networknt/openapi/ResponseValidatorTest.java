package com.networknt.openapi;

import com.networknt.body.BodyHandler;
import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import com.networknt.httpstring.AttachmentConstants;
import com.networknt.status.Status;
import com.networknt.exception.ClientException;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.networknt.openapi.ValidatorHandlerTest.sendResponse;

public class ResponseValidatorTest {
    static Map<String, Object> responses = Config.getInstance().getJsonMapConfig("responses");
    static Undertow server = null;
    static final Logger logger = LoggerFactory.getLogger(ResponseValidatorTest.class);
    @Before
    public void setUp() {
        if(server == null) {
            logger.info("starting server");
            OpenApiHandler openApiHandler = new OpenApiHandler();
            BodyHandler bodyHandler = new BodyHandler();

            TestValidateResponseHandler testValidateResponseHandler = new TestValidateResponseHandler();
            HttpHandler handler = Handlers.routing()
                    .add(Methods.GET, "/v1/todoItems", testValidateResponseHandler);
            ValidatorHandler validatorHandler = new ValidatorHandler();
            validatorHandler.setNext(handler);
            handler = validatorHandler;

            bodyHandler.setNext(handler);
            handler = bodyHandler;

            openApiHandler.setNext(handler);
            handler = openApiHandler;

            server = Undertow.builder()
                    .addHttpListener(7080, "localhost")
                    .setHandler(handler)
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

    @Test
    public void testValidateResponseContentWithExchange() throws InterruptedException, ClientException, URISyntaxException, TimeoutException, ExecutionException {
        ClientRequest clientRequest = new ClientRequest();
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response1");
        Assert.assertTrue(future.get(3, TimeUnit.SECONDS).getResponseCode() == 200);
    }

    @Test
    public void testValidateResponseContentWithExchangeError() throws InterruptedException, ClientException, URISyntaxException, TimeoutException, ExecutionException {
        ClientRequest clientRequest = new ClientRequest();
        CompletableFuture<ClientResponse> future = sendResponse(clientRequest, "response2");
        Assert.assertTrue(future.get(3, TimeUnit.SECONDS).getResponseCode() > 300);
    }

    public class TestValidateResponseHandler implements LightHttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {

            String responseBody = null;
            if(exchange.getAttachment(AttachmentConstants.REQUEST_BODY) != null) {
                responseBody = Config.getInstance().getMapper().writeValueAsString(exchange.getAttachment(AttachmentConstants.REQUEST_BODY));
            }
            final SchemaValidator schemaValidator = new SchemaValidator(OpenApiHandler.helper.openApi3, false);
            ResponseValidator validator = new ResponseValidator(schemaValidator, ValidatorHandler.config);
            Status status = validator.validateResponseContent(responseBody, exchange);
            if(status == null) {
                exchange.getResponseSender().send("good");
            } else {
                exchange.setStatusCode(400);
                exchange.getResponseSender().send("bad");
            }
        }
    }
}
