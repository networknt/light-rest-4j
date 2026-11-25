package com.networknt.openapi;

import com.networknt.config.ConfigException;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.MediaType;
import com.networknt.oas.model.OpenApi3;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.RequestBody;
import com.networknt.oas.model.impl.MediaTypeImpl;
import com.networknt.oas.model.impl.OpenApi3Impl;
import com.networknt.oas.model.impl.ParameterImpl;
import com.networknt.oas.model.impl.RequestBodyImpl;
import com.networknt.utility.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the MCP tools for the exposed endpoints of a spec. An endpoint that cannot be exposed fails startup with
 * a {@link ConfigException} naming the endpoint and the problem.
 */
final class McpToolFactory {
    private static final String SCHEMA_REF = "#/components/schemas/";
    private static final Set<String> LOCATIONS = Set.of("path", "query", "header");
    private static final Set<String> RESERVED_PARAMETER_NAMES = Set.of("body");
    // Header parameters that OpenAPI says SHALL be ignored, in lowercase.
    private static final Set<String> IGNORED_HEADER_PARAMETERS = Set.of("accept", "content-type", "authorization");
    // Schema keywords whose values are data, not schemas, so a $ref key in them is not a reference.
    private static final Set<String> DATA_KEYWORDS = Set.of("example", "examples", "default", "enum", "const");
    // Schema keywords whose values map names to schemas, so every value is a schema whatever its name.
    private static final Set<String> SCHEMA_MAP_KEYWORDS = Set.of("properties", "patternProperties", "$defs", "definitions", "dependentSchemas");

    // The parsed spec.
    private final OpenApi3 openApi3;
    // The base path of the spec's first servers URL, without a trailing /.
    private final String basePath;
    // The component schemas as written, with their $refs, for copying into a tool's $defs.
    private final JsonNode schemas;

    /**
     * Creates a factory for the tools of a spec.
     *
     * @param helper the parsed OpenAPI specification
     */
    private McpToolFactory(OpenApiHelper helper) {
        this.openApi3 = helper.openApi3;
        this.basePath = StringUtils.stripEnd(helper.basePath, "/");
        this.schemas = Overlay.toJson((OpenApi3Impl) openApi3).path("components").path("schemas");
    }

    /**
     * Builds the tools for the endpoints, in the same order.
     *
     * @param helper    the parsed OpenAPI specification
     * @param endpoints the exposed endpoints, as {path}@{method}, where the path includes the base path of the spec
     * @return the tools
     */
    static List<McpTool> create(OpenApiHelper helper, List<String> endpoints) {
        McpToolFactory factory = new McpToolFactory(helper);
        Map<String, McpTool> tools = new LinkedHashMap<>();
        for (String endpoint : endpoints) {
            McpTool tool = factory.convertEndpointToTool(endpoint);
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw error(endpoint, "operationId " + tool.name() + " is also used by another exposed endpoint");
            }
        }
        return List.copyOf(tools.values());
    }

    /**
     * Builds the tool for an exposed endpoint.
     *
     * @param endpoint the exposed endpoint, as {path}@{method}, where the path includes the base path of the spec
     * @return the tool
     * @throws ConfigException if the endpoint cannot be exposed
     */
    private McpTool convertEndpointToTool(final String endpoint) {
        final var openapiPath = StringUtils.substringBeforeLast(endpoint, "@");
        final var openapiMethod = StringUtils.substringAfterLast(endpoint, "@").toLowerCase(Locale.ROOT);
        final var pathItem = openapiPath.startsWith(basePath) ? openApi3.getPath(openapiPath.substring(basePath.length())) : null;
        final var operation = pathItem == null ? null : pathItem.getOperation(openapiMethod);
        mcpEndpointCheck(operation, endpoint);

        return new OpenApiMcpToolBuilder(schemas, endpoint, openapiPath, openapiMethod, operation)
                .withRequestBody(operation.getRequestBody())
                .withParameters(parameters(pathItem, operation))
                .build();
    }

    /**
     * Builds the tool for one operation: its definition for tools/list, and what McpToolCaller needs to call the
     * backend. Call withRequestBody and withParameters, then build. A builder builds one tool.
     */
    private static class OpenApiMcpToolBuilder {
        final JsonNode schemas;
        final Map<String, Object> toolDefinition = new LinkedHashMap<>();
        final List<McpTool.Parameter> toolParameters = new ArrayList<>();
        final String toolName;
        final String endpoint;
        final String openapiPath;
        final String openapiMethod;
        boolean hasBody = false;
        final ObjectNode toolProperties = JsonNodeFactory.instance.objectNode();
        final ArrayNode toolRequiredFields = JsonNodeFactory.instance.arrayNode();
        final ObjectNode toolDefs = JsonNodeFactory.instance.objectNode();

        /**
         * Starts a tool for an operation. The tool's name is the operationId, and its description is the operation's
         * description, if it has one.
         *
         * @param schemas       the component schemas of the spec, for copying into the tool's $defs
         * @param endpoint      the exposed endpoint, as {path}@{method}, used in error messages
         * @param openapiPath   the endpoint path, including the base path, with a {name} placeholder for each path parameter
         * @param openapiMethod the HTTP method, in lowercase
         * @param operation     the operation, which has an operationId
         */
        OpenApiMcpToolBuilder(final JsonNode schemas, final String endpoint, final String openapiPath,
                              final String openapiMethod, final Operation operation) {
            this.schemas = schemas;
            this.endpoint = endpoint;
            this.openapiPath = openapiPath;
            this.openapiMethod = openapiMethod;
            this.toolName = operation.getOperationId();
            this.toolDefinition.put("name", toolName);
            if (operation.getDescription() instanceof String description)
                this.toolDefinition.put("description", description);
        }

        /**
         * Adds the request body, if the operation has one, as the body property of the input schema.
         *
         * @param requestBody the operation's request body, which may be absent
         * @return this builder
         * @throws ConfigException if the request body does not offer application/json
         */
        OpenApiMcpToolBuilder withRequestBody(RequestBody requestBody) {
            this.hasBody = Overlay.isPresent((RequestBodyImpl) requestBody);
            if (this.hasBody) {
                this.resolveMcpToolBody(requestBody);
            }
            return this;
        }

        /**
         * Adds each parameter as a top-level property of the input schema.
         *
         * @param parameters the path item and operation parameters, merged
         * @return this builder
         * @throws ConfigException if a parameter is not in the path, query or header, is named body, or has the
         *                         same name as another parameter
         */
        OpenApiMcpToolBuilder withParameters(Collection<Parameter> parameters) {
            for (Parameter parameter : parameters) {
                this.resolveMcpToolParameter(parameter);
            }
            return this;
        }

        /**
         * Converts the OpenAPI parameter into an MCP tool parameter.
         *
         * @param parameter the OpenAPI parameter
         */
        private void resolveMcpToolParameter(final Parameter parameter) {
            String parameterIn = parameter.getIn();
            String parameterName = parameter.getName();
            mcpParameterCheck(toolProperties, endpoint, parameterIn, parameterName);

            JsonNode schema = Overlay.toJson((ParameterImpl) parameter).path("schema");
            var resolvedParameter = property(schema);

            if (parameter.getDescription() instanceof String description)
                resolvedParameter.put("description", description);

            toolProperties.set(parameterName, resolvedParameter);
            if (Boolean.TRUE.equals(parameter.getRequired()))
                toolRequiredFields.add(parameterName);

            toolParameters.add(new McpTool.Parameter(parameterName, parameterIn));
        }

        /**
         * Converts the OpenAPI request body into an MCP tool body.
         * The OpenAPI request body is expected to be of type application/json.
         *
         * @param openapiRequestBody the OpenAPI request body
         */
        private void resolveMcpToolBody(final RequestBody openapiRequestBody) {

            // Right now we only support application/json body types
            MediaType json = openapiRequestBody.getContentMediaTypes().get("application/json");
            if (json == null) {
                throw error(endpoint, "its requestBody offers no application/json content type " + openapiRequestBody.getContentMediaTypes().keySet());
            }

            var schema = Overlay.toJson((MediaTypeImpl) json).path("schema");
            var resolvedBody = property(schema);

            if (openapiRequestBody.getDescription() instanceof String description)
                resolvedBody.put("description", description);

            toolProperties.set("body", resolvedBody);

            if (Boolean.TRUE.equals(openapiRequestBody.getRequired()))
                toolRequiredFields.add("body");
        }

        /**
         * A property of the input schema for a parameter or request body schema.
         *
         * @param schema the schema, or a missing node if there is none
         * @return a copy of the schema, or an empty schema if there is none
         */
        private ObjectNode property(JsonNode schema) {
            return schema.isObject() ? copy(schema) : JsonNodeFactory.instance.objectNode();
        }

        /**
         * Copies a schema as written, except that each #/components/schemas/X reference becomes #/$defs/X, and X
         * (with whatever it references) is copied into toolDefs. OpenAPI limits schema names to A-Z, a-z, 0-9, ., -
         * and _, so X needs no JSON pointer escaping.
         *
         * @param schema the schema to copy
         * @return the copy
         * @throws ConfigException if the schema has a $ref that is not to a component schema
         */
        private ObjectNode copy(JsonNode schema) {
            ObjectNode copy = (ObjectNode) schema.deepCopy();
            rewriteRefs(copy);
            return copy;
        }

        /**
         * Rewrites the $refs of a schema and of the schemas in it. The values of {@link #DATA_KEYWORDS} and of
         * x- extensions are data, so they are left as they are.
         *
         * @param schema the schema, which is changed
         */
        private void rewriteRefs(ObjectNode schema) {
            JsonNode ref = schema.get("$ref");
            if (ref != null && ref.isString()) schema.put("$ref", defsRef(ref.stringValue()));
            for (Map.Entry<String, JsonNode> field : schema.properties()) {
                String keyword = field.getKey();
                if (keyword.equals("$ref") || DATA_KEYWORDS.contains(keyword) || keyword.startsWith("x-")) continue;
                if (SCHEMA_MAP_KEYWORDS.contains(keyword)) {
                    field.getValue().values().forEach(this::rewriteRefsIn);
                } else {
                    rewriteRefsIn(field.getValue());
                }
            }
        }

        /**
         * Rewrites the $refs of a schema, or of each schema in an array (e.g. allOf); other values have none.
         *
         * @param node the schema or array, which is changed
         */
        private void rewriteRefsIn(JsonNode node) {
            if (node.isObject()) rewriteRefs((ObjectNode) node);
            else if (node.isArray()) node.values().forEach(this::rewriteRefsIn);
        }

        /**
         * The $defs reference for a component schema reference, copying the schema into toolDefs the first time.
         *
         * @param ref the $ref value
         * @return #/$defs/X for #/components/schemas/X
         * @throws ConfigException if the $ref is not to a component schema, e.g. to a property inside one
         */
        private String defsRef(String ref) {
            String name = ref.startsWith(SCHEMA_REF) ? ref.substring(SCHEMA_REF.length()) : "";
            if (name.contains("/") || !schemas.has(name)) {
                throw error(endpoint, "its schema $ref " + ref + " is not a reference to a component schema (" + SCHEMA_REF + "<name>)");
            }
            if (!toolDefs.has(name)) {
                toolDefs.putNull(name); // stops recursive schemas from being copied again
                toolDefs.set(name, copy(schemas.get(name)));
            }
            return "#/$defs/" + name;
        }

        /**
         * Finishes the input schema and builds the tool. The input schema's required list and $defs are left out if
         * they are empty.
         *
         * @return the tool
         */
        McpTool build() {
            ObjectNode inputSchema = JsonNodeFactory.instance.objectNode()
                    .put("type", "object")
                    .set("properties", toolProperties);
            if (!toolRequiredFields.isEmpty())
                inputSchema.set("required", toolRequiredFields);
            if (!toolDefs.isEmpty())
                inputSchema.set("$defs", toolDefs);
            toolDefinition.put("inputSchema", inputSchema);
            return new McpTool(toolName, openapiMethod.toUpperCase(Locale.ROOT), openapiPath, toolParameters, hasBody, toolDefinition);
        }
    }

    /**
     * Checks to see if a given operation is able to convert to an MCP schema.
     * Any additional endpoint checks should be here.
     *
     * @param operation - The operation being validated
     * @param endpoint - The OpenAPI endpoint the operation belongs to.
     */
    private static void mcpEndpointCheck(final Operation operation, final String endpoint) {

        if (operation == null) {
            throw error(endpoint, "it is not in the spec");
        }

        if (operation.getOperationId() == null) {
            throw error(endpoint, "it has no operationId");
        }
    }

    /**
     * Checks to see if a given parameter name is able to convert to an MCP schema.
     * Any additional parameter checks should be here.
     *
     * @param existingProperties - Existing properties already added to the new MCP tool.
     * @param newParameterEndpoint - The OpenAPI endpoint the parameter can be found in.
     * @param newParameterLocation - Location of where the new parameter is found (i.e. path, query, etc.)
     * @param newPropertyName - The parameter name we are validating.
     */
    private static void mcpParameterCheck(
            final ObjectNode existingProperties,
            final String newParameterEndpoint,
            final String newParameterLocation,
            final String newPropertyName
    ) {
        // We only support a set of locations for parameters
        if (!LOCATIONS.contains(newParameterLocation))
            throw error(newParameterEndpoint, newParameterLocation + " parameter " + newPropertyName + " is not supported");

        // Make sure we are not using any reserved field names.
        if (RESERVED_PARAMETER_NAMES.contains(newPropertyName))
            throw error(newParameterEndpoint, "parameter name " + newPropertyName + " is reserved for the request body");

        // No duplicates
        if (existingProperties.has(newPropertyName))
            throw error(newParameterEndpoint, "two parameters are named " + newPropertyName);
    }


    /**
     * The parameters of an operation. An operation parameter replaces a path item parameter with the same name and
     * location, as in OpenAPI. Header parameters named Accept, Content-Type or Authorization are left out, because
     * OpenAPI says they SHALL be ignored; Authorization in particular is the forwarded token.
     *
     * @param pathItem  the path item
     * @param operation the operation
     * @return the parameters, path item parameters first
     */
    private static Collection<Parameter> parameters(Path pathItem, Operation operation) {
        Map<String, Parameter> parameters = new LinkedHashMap<>();
        for (List<Parameter> list : List.of(pathItem.getParameters(), operation.getParameters())) {
            for (Parameter parameter : list)
                parameters.put(parameter.getIn() + " " + parameter.getName(), parameter);
        }
        parameters.values().removeIf(parameter -> "header".equals(parameter.getIn())
                && IGNORED_HEADER_PARAMETERS.contains(parameter.getName().toLowerCase(Locale.ROOT)));
        return parameters.values();
    }

    /**
     * The startup error for an endpoint that cannot be exposed.
     *
     * @param endpoint the exposed endpoint, as {path}@{method}
     * @param problem  why it cannot be exposed
     * @return the exception to throw
     */
    private static ConfigException error(String endpoint, String problem) {
        return new ConfigException("openapi-mcp cannot expose " + endpoint + ": " + problem);
    }

}
