package com.networknt.openapi;

import java.util.List;
import java.util.Map;

/**
 * An exposed endpoint: its MCP tool definition, and what is needed to call the backend for it.
 *
 * @param name            the tool name, which is the endpoint's operationId
 * @param method          the HTTP method, in uppercase
 * @param path            the endpoint path, with a {name} placeholder for each path parameter
 * @param parameters      the path, query and header parameters that tool arguments are mapped to
 * @param hasBody         whether the endpoint has an application/json request body
 * @param definition      the tool definition returned by tools/list
 */
record McpTool(String name, String method, String path, List<Parameter> parameters, boolean hasBody,
               Map<String, Object> definition) {

    /**
     * A path, query or header parameter.
     *
     * @param name the parameter name, which is also the tool argument name
     * @param in   path, query or header
     */
    record Parameter(String name, String in) { }
}
