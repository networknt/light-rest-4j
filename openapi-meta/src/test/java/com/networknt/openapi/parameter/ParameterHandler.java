package com.networknt.openapi.parameter;

import static io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY;

import java.util.Map;

import com.networknt.handler.Handler;
import com.networknt.handler.MiddlewareHandler;
import com.networknt.utility.ModuleRegistry;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathTemplateMatcher;

/**
 * Simulate com.networknt.handler.Handler.start()
 * @author Daniel Zhao
 *
 */
public class ParameterHandler implements MiddlewareHandler {
	private static PathTemplateMatcher<String> pathTemplateMatcher = new PathTemplateMatcher<>();
	private volatile HttpHandler next;
	
	static {
        pathTemplateMatcher.add("/pets", "0");
        pathTemplateMatcher.add("/pets/{petId}", "1");
        pathTemplateMatcher.add("/pets_simple_array/{petId}", "2");
        pathTemplateMatcher.add("/pets_simple_obj_ep/{petId}", "3");
        pathTemplateMatcher.add("/pets_simple_obj_no_ep/{petId}", "4");
        pathTemplateMatcher.add("/pets_label_array_ep/{petId}", "5");
        pathTemplateMatcher.add("/pets_label_array_no_ep/{petId}", "6");
        pathTemplateMatcher.add("/pets_label_obj_ep/{petId}", "7");
        pathTemplateMatcher.add("/pets_label_obj_no_ep/{petId}", "8");        
        pathTemplateMatcher.add("/pets_matrix_array_ep/{petId}", "9");
        pathTemplateMatcher.add("/pets_matrix_array_no_ep/{petId}", "10");
        pathTemplateMatcher.add("/pets_matrix_obj_ep/{petId}", "11");
        pathTemplateMatcher.add("/pets_matrix_obj_no_ep/{petId}", "12");
        pathTemplateMatcher.add("/pets_matrix_pm/{petId}", "13");
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		PathTemplateMatcher.PathMatchResult<String> result = pathTemplateMatcher.match(exchange.getRequestPath());
		
		if (result != null) {
			exchange.putAttachment(ATTACHMENT_KEY,
					new io.undertow.util.PathTemplateMatch(result.getMatchedTemplate(), result.getParameters()));
			for (Map.Entry<String, String> entry : result.getParameters().entrySet()) {
				exchange.addQueryParam(entry.getKey(), entry.getValue());
				
				exchange.addPathParam(entry.getKey(), entry.getValue());
			}
		}
		
		Handler.next(exchange, next);
	}

	@Override
	public HttpHandler getNext() {
		return next;
	}

	@Override
	public MiddlewareHandler setNext(HttpHandler next) {
		Handlers.handlerNotNull(next);
		this.next = next;
		return this;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void register() {
		 ModuleRegistry.registerModule(ParameterHandler.class.getName(), null, null);
	}

}
