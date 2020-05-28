package com.networknt.openapi.parameter;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

public class IntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(OpenApiHandlerTest.class);
    private static final String EXPECTED_ARRAY_RESULT="3-4-5";
    private static final String EXPECTED_MAP_RESULT="id-name-001-Dog";
    private static final String EXPECTED_NEGATIVE_RESULT = "failed";

    private static Undertow server = null;
    
    

    @BeforeClass
    public static void setUp() {
        if(server == null) {
            logger.info("starting server");
            HttpHandler handler = setupRoutings();
            
            OpenApiHandler openApiHandler = new OpenApiHandler();
            openApiHandler.setNext(handler);
            
            ParameterHandler parameterHandler = new ParameterHandler();
            parameterHandler.setNext(openApiHandler);
            
            handler = parameterHandler;
            
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
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange,true).get("id_form"));
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange,true).get("id_sd"));
                	addToList(resultList, OpenApiHandler.getQueryParameters(exchange,true).get("id_pd"));
                	
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange,true).get("id_do"));
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange,true).get("id_fo"));
                	addToMap(resultMap, OpenApiHandler.getQueryParameters(exchange,true).get("id_fno"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                	
                }).add(Methods.GET, "/pets_simple_array/{petId}", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_label_array_ep/{petId}", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_label_array_no_ep/{petId}", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_matrix_array_ep/{petId}", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_matrix_array_no_ep/{petId}", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_simple_obj_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_simple_obj_no_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_label_obj_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_label_obj_no_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_matrix_obj_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_matrix_obj_no_ep/{petId}", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_matrix_pm/{petId}", exchange -> {
                	send(exchange, (String)OpenApiHandler.getPathParameters(exchange, true).get("petId"));
                }).add(Methods.GET, "/pets_header_array", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getHeaderParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_header_obj_ep", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getHeaderParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_header_obj_no_ep", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getHeaderParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_cookie_array", exchange -> {
                	List<String> resultList = new ArrayList<>();
                	
                	addToList(resultList, OpenApiHandler.getCookieParameters(exchange, true).get("petId"));
                	
                	if (!resultList.isEmpty()) {
                		send(exchange, String.join(valueDelimiter, resultList));
                	}else {
                		send(exchange, null);
                	}
                }).add(Methods.GET, "/pets_cookie_obj_no_ep", exchange -> {
                	Map<String, String> resultMap = new HashMap<>();
                	
                	addToMap(resultMap, OpenApiHandler.getCookieParameters(exchange, true).get("petId"));
                	
                	if (!resultMap.isEmpty()) {
                		send(exchange, String.format("id-name-%s-%s", resultMap.get("id"), resultMap.get("name")));
                	}else {
                		send(exchange, null);
                	}
                });
    }
    
    @Test
    public void test_array_default_query_param_deserialization() throws Exception {
    	runTest("/pets?limit=3&limit=4&limit=5", EXPECTED_ARRAY_RESULT);
    }
	
	@Test
	/** negative test: query params are case sensitive */
	public void test_mixed_case_array_default_query_param_deserialization() throws Exception {
		runTest("/pets?LIMIT=3&LIMIT=4&LIMIT=5", EXPECTED_NEGATIVE_RESULT);
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
    
    @Test
    public void test_array_path_param_deserialization() throws Exception {
    	runTest("/pets_simple_array/3,4,5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_object_simple_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_simple_obj_ep/id=001,name=Dog", EXPECTED_MAP_RESULT);
    }
    
    @Test
    public void test_object_simple_no_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_simple_obj_no_ep/id,001,name,Dog", EXPECTED_MAP_RESULT);
    }
    
    @Test
    public void test_array_label_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_label_array_ep/.3.4.5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_array_label_no_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_label_array_no_ep/.3,4,5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_object_label_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_label_obj_ep/.id=001.name=Dog", EXPECTED_MAP_RESULT);
    }
    
    @Test
    public void test_object_label_no_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_label_obj_no_ep/.id,001,name,Dog", EXPECTED_MAP_RESULT);
    }
    
    @Test
    public void test_primitive_matrix_path_param_deserialization() throws Exception {
    	runTest("/pets_matrix_pm/;petId=5", "5");
    }
    
    @Test
    public void test_array_matrix_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_matrix_array_ep/;petId=3;petId=4;petId=5", EXPECTED_ARRAY_RESULT);
    }
    
    @Test
    public void test_array_matrix_no_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_matrix_array_no_ep/;petId=3,4,5", EXPECTED_ARRAY_RESULT);
    }  
    
    @Test
    public void test_object_matrix_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_matrix_obj_ep/;id=001;name=Dog", EXPECTED_MAP_RESULT);
    }
    
    //@Test
    public void test_object_matrix_no_explode_path_param_deserialization() throws Exception {
    	runTest("/pets_matrix_obj_no_ep/;petId=id,001,name,Dog", EXPECTED_MAP_RESULT);
    } 
    
    @Test
    public void test_array_header_param_deserialization() throws Exception {
    	Map<String, String> headers = new HashMap<>();
    	headers.put("petId", "3,4,5");
    	
    	runTest("/pets_header_array", EXPECTED_ARRAY_RESULT, headers, Collections.emptyMap());
    }
	
	@Test
	public void test_array_mixed_case_header_param_deserialization() throws Exception {
		Map<String, String> headers = new HashMap<>();
		headers.put("PeTiD", "3,4,5");
		
		runTest("/pets_header_array", EXPECTED_ARRAY_RESULT, headers, Collections.emptyMap());
	}
    
    @Test
    public void test_object_simple_explode_header_param_deserialization() throws Exception {
    	Map<String, String> headers = new HashMap<>();
    	headers.put("petId", "id=001,name=Dog");
    	
    	runTest("/pets_header_obj_ep", EXPECTED_MAP_RESULT, headers, Collections.emptyMap());
    }
	
	@Test
	public void test_object_simple_explode_mixed_case_header_param_deserialization() throws Exception {
		Map<String, String> headers = new HashMap<>();
		headers.put("PeTiD", "id=001,name=Dog");
		
		runTest("/pets_header_obj_ep", EXPECTED_MAP_RESULT, headers, Collections.emptyMap());
	}
    
    @Test
    public void test_object_simple_no_explode_header_param_deserialization() throws Exception {
    	Map<String, String> headers = new HashMap<>();
    	headers.put("petId", "id,001,name,Dog");
    	runTest("/pets_header_obj_no_ep", EXPECTED_MAP_RESULT, headers, Collections.emptyMap());
    }
	
	@Test
	public void test_object_simple_no_explode_mixed_case_header_param_deserialization() throws Exception {
		Map<String, String> headers = new HashMap<>();
		headers.put("PeTiD", "id,001,name,Dog");
		runTest("/pets_header_obj_no_ep", EXPECTED_MAP_RESULT, headers, Collections.emptyMap());
	}
	
	@Test
    public void test_array_cookie_param_deserialization() throws Exception {
    	Map<String, String> cookies = new HashMap<>();
    	cookies.put("petId", "3,4,5");
    	runTest("/pets_cookie_array", EXPECTED_ARRAY_RESULT, Collections.emptyMap(), cookies);
    }
	
	@Test
	/*** negative test: cookie params are case sensitive */
	public void test_array_mixed_case_cookie_param_deserialization() throws Exception {
		Map<String, String> cookies = new HashMap<>();
		cookies.put("petid", "3,4,5");
		runTest("/pets_cookie_array", EXPECTED_NEGATIVE_RESULT, Collections.emptyMap(), cookies);
	}
    
    @Test
    public void test_object_simple_no_explode_cookie_param_deserialization() throws Exception {
    	Map<String, String> cookies = new HashMap<>();
    	cookies.put("petId", "id,001,name,Dog");
    	runTest("/pets_cookie_obj_no_ep", EXPECTED_MAP_RESULT, Collections.emptyMap(), cookies);
    }
    
    public void runTest(String requestPath, String expectedValue, Map<String, String> headers, Map<String, String> cookies) throws Exception {
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
                    
                    if (!headers.isEmpty()) {
                    	headers.entrySet().forEach(entry->request.getRequestHeaders().put(new HttpString(entry.getKey()), entry.getValue()));
                    }
                    
                    if (!cookies.isEmpty()) {
                    	List<String> cookieItems = new ArrayList<>();
                    	cookies.entrySet().forEach(entry->cookieItems.add(String.format("%s=%s", entry.getKey(), entry.getValue())));
                    	
                    	 request.getRequestHeaders().put(Headers.COOKIE, String.join(";", cookieItems));
                    }
                    
                    connection.sendRequest(request, client.createClientCallback(reference, latch));
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
    
    public void runTest(String requestPath, String expectedValue) throws Exception {
    	runTest(requestPath, expectedValue, Collections.emptyMap(), Collections.emptyMap());
    }    
}
