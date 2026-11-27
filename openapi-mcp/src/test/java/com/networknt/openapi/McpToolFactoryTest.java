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

import com.networknt.config.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolFactoryTest {

    // Each kind of endpoint that fails startup, plus an endpoint that is not in the spec.
    // Each case is the /pets@post operation and the exposed endpoint; /pets@get (listPets) is also exposed.
    static Stream<Arguments> endpointsThatFailStartup() {
        return Stream.of(
                Arguments.of("{}", "/pets@post", "has no operationId"),
                Arguments.of("{'operationId': ''}", "/pets@post", "operationId  is not a valid tool name"),
                Arguments.of("{'operationId': 'create pet'}", "/pets@post", "operationId create pet is not a valid tool name"),
                Arguments.of("{'operationId': 'créerAnimal'}", "/pets@post", "operationId créerAnimal is not a valid tool name"),
                Arguments.of("{'operationId': '" + "a".repeat(129) + "'}", "/pets@post", "is not a valid tool name: it must be 1 to 128 characters"),
                Arguments.of("{'operationId': 'listPets'}", "/pets@post", "operationId listPets is also used by another exposed endpoint"),
                Arguments.of("{'operationId': 'createPet', 'requestBody': {'content': {'application/xml': {}}}}",
                        "/pets@post", "its requestBody offers no application/json content type [application/xml]"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'body', 'in': 'query'}]}",
                        "/pets@post", "parameter name body is reserved"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'id', 'in': 'path'}, {'name': 'id', 'in': 'query'}]}",
                        "/pets@post", "two parameters are named id"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'session', 'in': 'cookie'}]}",
                        "/pets@post", "cookie parameter session is not supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'Accept-Encoding', 'in': 'header'}]}",
                        "/pets@post", "header parameter Accept-Encoding is not supported: the header is set by openapi-mcp or the HTTP client"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'X-Forwarded-For', 'in': 'header'}]}",
                        "/pets@post", "header parameter X-Forwarded-For is not supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'host', 'in': 'header'}]}",
                        "/pets@post", "header parameter host is not supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'ids', 'in': 'query', 'explode': false}]}",
                        "/pets@post", "query parameter ids has explode false; only the default explode true is supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'ids', 'in': 'query', 'style': 'pipeDelimited'}]}",
                        "/pets@post", "query parameter ids has style pipeDelimited; only the default style form is supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'X-Ids', 'in': 'header', 'explode': true}]}",
                        "/pets@post", "header parameter X-Ids has explode true; only the default explode false is supported"),
                Arguments.of("{'operationId': 'createPet', 'parameters': [{'name': 'filter', 'in': 'query', 'content': {'application/json': {}}}]}",
                        "/pets@post", "query parameter filter defines content, which is not supported"),
                Arguments.of("{'operationId': 'createPet'}", "/pets/{id}@get", "it is not in the spec"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource
    void endpointsThatFailStartup(String operation, String endpoint, String problem) {
        OpenApiHelper spec = spec("{'paths': {'/pets': {'get': {'operationId': 'listPets'}, 'post': " + operation + "}}}");
        ConfigException e = assertThrows(ConfigException.class,
                () -> McpToolFactory.create(spec, List.of("/pets@get", endpoint)));
        assertTrue(e.getMessage().startsWith("openapi-mcp cannot expose " + endpoint + ": "), e.getMessage());
        assertTrue(e.getMessage().contains(problem), e.getMessage());
    }

    @Test
    void endpointWithBasePathAndParameterRefs() {
        OpenApiHelper spec = spec("""
                {'servers': [{'url': 'https://pets.example.com/v1/'}],
                 'paths': {'/pets/{petId}': {
                   'parameters': [{'$ref': '#/components/parameters/PetId'}, {'name': 'X-Trace', 'in': 'header', 'schema': {'type': 'string'}}],
                   'put': {'operationId': 'updatePet',
                           'parameters': [{'name': 'X-Trace', 'in': 'header', 'required': true, 'schema': {'type': 'integer'}}],
                           'requestBody': {'description': 'The pet.', 'content': {'application/json': {'schema': {'type': 'object'}}}}}}},
                 'components': {'parameters': {'PetId': {'name': 'petId', 'in': 'path', 'required': true, 'schema': {'type': 'string'}}}}}""");
        McpTool tool = McpToolFactory.create(spec, List.of("/v1/pets/{petId}@PUT")).get(0);

        assertEquals("PUT", tool.method());
        assertEquals("/v1/pets/{petId}", tool.path());
        assertEquals(List.of(new McpTool.Parameter("petId", "path"), new McpTool.Parameter("X-Trace", "header")), tool.parameters());
        assertTrue(tool.hasBody());
        assertEquals(json("""
                {'type': 'object',
                 'properties': {'petId': {'type': 'string'}, 'X-Trace': {'type': 'integer'}, 'body': {'type': 'object', 'description': 'The pet.'}},
                 'required': ['petId', 'X-Trace']}"""), tool.definition().get("inputSchema"));
    }

    @Test
    void longestToolName() {
        String name = "admin.pets-v2_" + "x".repeat(114);
        OpenApiHelper spec = spec("{'paths': {'/pets': {'get': {'operationId': '" + name + "'}}}}");
        assertEquals(name, McpToolFactory.create(spec, List.of("/pets@get")).get(0).name());
    }

    @Test
    void pathTemplateWithoutPathParameter() {
        OpenApiHelper spec = spec("""
                {'paths': {'/pets/{petId}/photos/{photoId}': {'get': {'operationId': 'getPhoto', 'parameters': [
                   {'name': 'petId', 'in': 'path', 'required': true, 'schema': {'type': 'string'}},
                   {'name': 'photoId', 'in': 'query', 'schema': {'type': 'string'}}]}}}}""");
        ConfigException e = assertThrows(ConfigException.class,
                () -> McpToolFactory.create(spec, List.of("/pets/{petId}/photos/{photoId}@get")));
        assertEquals("openapi-mcp cannot expose /pets/{petId}/photos/{photoId}@get: its path has {photoId}, but no path parameter photoId",
                e.getMessage());
    }

    @Test
    void defaultSerializationWrittenOut() {
        OpenApiHelper spec = spec("""
                {'paths': {'/pets/{petId}': {'get': {'operationId': 'getPet', 'parameters': [
                   {'name': 'petId', 'in': 'path', 'required': true, 'style': 'simple', 'explode': false, 'schema': {'type': 'string'}},
                   {'name': 'tags', 'in': 'query', 'style': 'form', 'explode': true, 'schema': {'type': 'array', 'items': {'type': 'string'}}},
                   {'name': 'X-Ids', 'in': 'header', 'style': 'simple', 'explode': false, 'schema': {'type': 'string'}}]}}}}""");
        McpTool tool = McpToolFactory.create(spec, List.of("/pets/{petId}@get")).get(0);
        assertEquals(List.of(new McpTool.Parameter("petId", "path"), new McpTool.Parameter("tags", "query"), new McpTool.Parameter("X-Ids", "header")),
                tool.parameters());
    }

    @Test
    void ignoredHeaderParameters() {
        OpenApiHelper spec = spec("""
                {'paths': {'/pets': {'get': {'operationId': 'listPets', 'parameters': [
                   {'name': 'Authorization', 'in': 'header', 'required': true, 'schema': {'type': 'string'}},
                   {'name': 'accept', 'in': 'header', 'schema': {'type': 'string'}},
                   {'name': 'Content-Type', 'in': 'header', 'schema': {'type': 'string'}},
                   {'name': 'Authorization', 'in': 'query', 'schema': {'type': 'string'}},
                   {'name': 'X-Locale', 'in': 'header', 'schema': {'type': 'string'}}]}}}}""");
        McpTool tool = McpToolFactory.create(spec, List.of("/pets@get")).get(0);

        assertEquals(List.of(new McpTool.Parameter("Authorization", "query"), new McpTool.Parameter("X-Locale", "header")), tool.parameters());
        assertEquals(json("{'type': 'object', 'properties': {'Authorization': {'type': 'string'}, 'X-Locale': {'type': 'string'}}}"),
                tool.definition().get("inputSchema"));
    }

    @Test
    void schemaRefs() {
        OpenApiHelper spec = spec("""
                {'paths': {'/pets': {'post': {'operationId': 'createPet', 'requestBody': {'content': {'application/json': {'schema': {
                   'type': 'object',
                   'example': {'$ref': 'x.json'},
                   'x-note': {'$ref': 'y.json'},
                   'properties': {
                     '$ref': {'type': 'string', 'default': {'$ref': 'z.json'}},
                     'example': {'$ref': '#/components/schemas/A'},
                     'tags': {'type': 'array', 'items': {'$ref': '#/components/schemas/A'}, 'enum': [{'$ref': 'w.json'}]}}}}}}}}},
                 'components': {'schemas': {'A': {'allOf': [{'$ref': '#/components/schemas/B'}]}, 'B': {'type': 'string'}}}}""");
        McpTool tool = McpToolFactory.create(spec, List.of("/pets@post")).get(0);

        assertEquals(json("""
                {'type': 'object',
                 'properties': {'body': {
                   'type': 'object',
                   'example': {'$ref': 'x.json'},
                   'x-note': {'$ref': 'y.json'},
                   'properties': {
                     '$ref': {'type': 'string', 'default': {'$ref': 'z.json'}},
                     'example': {'$ref': '#/$defs/A'},
                     'tags': {'type': 'array', 'items': {'$ref': '#/$defs/A'}, 'enum': [{'$ref': 'w.json'}]}}}},
                 '$defs': {'A': {'allOf': [{'$ref': '#/$defs/B'}]}, 'B': {'type': 'string'}}}"""), tool.definition().get("inputSchema"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"#/components/schemas/A/properties/n", "#/components/schemas/Missing", "#/components/parameters/P"})
    void schemaRefsThatFailStartup(String ref) {
        OpenApiHelper spec = spec("""
                {'paths': {'/pets': {'post': {'operationId': 'createPet', 'requestBody': {'content': {'application/json': {'schema': {
                   'type': 'object', 'properties': {'n': {'$ref': '%s'}}}}}}}}},
                 'components': {'schemas': {'A': {'type': 'object', 'properties': {'n': {'type': 'string'}}}},
                                'parameters': {'P': {'name': 'p', 'in': 'query'}}}}""".formatted(ref));
        ConfigException e = assertThrows(ConfigException.class, () -> McpToolFactory.create(spec, List.of("/pets@post")));
        assertEquals("openapi-mcp cannot expose /pets@post: its schema $ref " + ref
                + " is not a reference to a component schema (#/components/schemas/<name>)", e.getMessage());
    }

    private static OpenApiHelper spec(String json) {
        return new OpenApiHelper("{'openapi': '3.0.0', 'info': {'title': 't', 'version': '1'}, ".replace('\'', '"') + json.substring(1).replace('\'', '"'));
    }

    private static JsonNode json(String json) {
        return JsonMapper.builder().build().readTree(json.replace('\'', '"'));
    }
}
