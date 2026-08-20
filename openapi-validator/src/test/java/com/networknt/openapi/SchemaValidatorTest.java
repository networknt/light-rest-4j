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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.path.PathType;
import com.networknt.status.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldKeepLooseAndStrictTypeProfilesSeparate() throws Exception {
        SchemaValidator validator = new SchemaValidator();
        JsonNode integerSchema = MAPPER.readTree("{\"type\":\"integer\"}");

        assertNull(validator.validate(new TextNode("42"), integerSchema, true, false));
        assertNotNull(validator.validate(new TextNode("42"), integerSchema, false, false));
    }

    @Test
    void shouldHonorNullableKeywordProfile() throws Exception {
        SchemaValidator validator = new SchemaValidator();
        JsonNode nullableStringSchema = MAPPER.readTree("{\"type\":\"string\",\"nullable\":true}");

        assertNull(validator.validate(NullNode.getInstance(), nullableStringSchema, false, true));
        assertNotNull(validator.validate(NullNode.getInstance(), nullableStringSchema, false, false));
    }

    @Test
    void shouldPrefixNestedJsonPointerLocation() throws Exception {
        SchemaValidator validator = new SchemaValidator(null, false);
        JsonNode schema = MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"age\":{\"type\":\"integer\"}}}");
        JsonNode value = MAPPER.readTree("{\"age\":\"invalid\"}");

        Status status = validator.validate(value, schema, "body");

        assertNotNull(status);
        assertTrue(status.getDescription().contains("/body/age:"), status.getDescription());
    }

    @Test
    void shouldPreserveLegacyLocationFormat() throws Exception {
        SchemaValidator validator = new SchemaValidator(null, true);
        JsonNode schema = MAPPER.readTree("{\"type\":\"integer\"}");

        Status status = validator.validate(new TextNode("invalid"), schema, "requestId");

        assertNotNull(status);
        assertTrue(status.getDescription().contains("$.requestId:"), status.getDescription());
    }

    @Test
    void shouldAllowStandardNonDefaultDialects() throws Exception {
        SchemaValidator validator = new SchemaValidator();
        JsonNode schema = MAPPER.readTree("{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"integer\"}");

        Status status = validator.validate(new TextNode("invalid"), schema, false, false);

        assertNotNull(status);
        assertEquals(SchemaValidator.VALIDATOR_SCHEMA, status.getCode());
    }

    @Test
    void shouldFailClosedWhenSchemaCannotBeBuilt() throws Exception {
        SchemaValidator validator = new SchemaValidator();
        JsonNode invalidSchema = MAPPER.readTree("{\"type\":\"string\",\"pattern\":\"[\"}");

        Status status = validator.validate(new TextNode("value"), invalidSchema, false, false);

        assertNotNull(status);
        assertEquals(SchemaValidator.VALIDATOR_SCHEMA_INVALID_JSON, status.getCode());
    }

    @Test
    void shouldPreserveCustomValidationMessageBehaviorByDirection() throws Exception {
        SchemaValidator validator = new SchemaValidator();
        JsonNode schema = MAPPER.readTree("{\"type\":\"object\",\"required\":[\"name\"],\"message\":{\"required\":\"A custom validation message\"}}");

        Status requestStatus = validator.validate(MAPPER.createObjectNode(), schema, false, false);
        Status responseStatus = validator.validate(MAPPER.createObjectNode(), schema, false, false,
                PathType.LEGACY, true);

        assertNotNull(requestStatus);
        assertFalse(requestStatus.getDescription().contains("A custom validation message"), requestStatus.getDescription());
        assertNotNull(responseStatus);
        assertTrue(responseStatus.getDescription().contains("A custom validation message"), responseStatus.getDescription());
        assertFalse(responseStatus.getDescription().contains("$: A custom validation message"),
                responseStatus.getDescription());
    }

    @Test
    void shouldAllowResponseValidationToRetainLegacyPaths() throws Exception {
        SchemaValidator validator = new SchemaValidator(null, false);
        JsonNode schema = MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"age\":{\"type\":\"integer\"}}}");
        JsonNode value = MAPPER.readTree("{\"age\":\"invalid\"}");

        Status status = validator.validate(value, schema, false, false, PathType.LEGACY);

        assertNotNull(status);
        assertTrue(status.getDescription().contains("$.age:"), status.getDescription());
    }
}
