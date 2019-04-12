package com.networknt.openapi.parameter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import com.networknt.client.Http2Client;
import com.networknt.exception.ClientException;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.openapi.OpenApiHandlerTest;
import com.networknt.utility.StringUtils;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

public class IntegrationTest {
    static final Logger logger = LoggerFactory.getLogger(OpenApiHandlerTest.class);
    static final String EXPECTED_ARRAY_RESULT="3-4-5";
    static final String EXPECTED_MAP_RESULT="id-name-001-Dog";

    static Undertow server = null;

    @BeforeClass
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = setupRoutings();
            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(handler);
            handler = openApiHandler;
            server = Undertow.builder()
                    .addHttpListener(8080, "localhost")
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

    private static void send(HttpServerExchange exchange, String value) {
    	exchange.getResponseSender().send(StringUtils.isNotBlank(value)?value:"failed");
    }
    
    @SuppressWarnings("unchecked")
    private static void addToList(List<String> list, Object result) {
    	if (result instanceof Collection) {
    		list.addAll((Collection<String>)result);
    	}
    }
    
    @SuppressWarnings("unchecked")
    private static void addToMap(Map<String, String> map, Object result) {
    	if (result instanceof Map) {
    		map.putAll((Map<String, String>)result);
    	}
    }    
    
	static RoutingHandler setupRoutings() {
    	String valueDelimiter = "-";
    	
        return Handlers.routing()
                .add(Methods.GET, "/pets", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToList(resultList, exchange.getQueryParameters().get("limit"));
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange).get("id_form"));
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange).get("id_sd"));
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange).get("id_pd"));
                	
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange).get("id_do"));
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange).get("id_fo"));
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange).get("id_fno"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                	
                });
    }
    
    @Test
    public void test_default_query_param_deserialization() throws Exception {
    	runTest("/pets?limit=3&limit=4&limit=5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_array_no_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id_form=3,4,5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_array_spaceDelimited_no_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id_sd=3%204%205", EXPECTED_ARRAY_RESULT);
    } 
    
    @Test
    public void test_array_pipeDelimited_no_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id_pd=3%7C4%7C5", EXPECTED_ARRAY_RESULT);
    } 
    
    @Test
    public void test_object_deepObject_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id_do[id]=001&id_do[name]=Dog", EXPECTED_MAP_RESULT);
    }   
    
    @Test
    public void test_object_form_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id=001&name=Dog", EXPECTED_MAP_RESULT);
    }  
    
    @Test
    public void test_object_form_no_explode_query_param_deserialization() throws Exception {
    	runTest("/pets?id_fno=id,001,name,Dog", EXPECTED_MAP_RESULT);
    }     
    
    public void runTest(String requestPath, String expectedValue) throws Exception {
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
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(requestPath);
                    request.getRequestHeaders().put(Headers.HOST, "localhost");
                    connection.sendRequest(request, client.createClientCallback(reference, latch, ""));
                }
            });
            latch.await(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("IOException: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        int statusCode = reference.get().getResponseCode();
        Assert.assertEquals(200, statusCode);
        if(statusCode == 200) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            Assert.assertNotNull(body);
            Assert.assertEquals(expectedValue, body);
        }
    }    
}
