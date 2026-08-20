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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.OpenApi3;
import com.networknt.oas.model.impl.OpenApi3Impl;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.NonValidationKeyword;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.path.PathType;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Validate a value against the schema defined in an OpenAPI specification.
 * <p>
 * Supports validation of properties and request/response bodies, and supports schema references.
 *
 * @author Steve Hu
 */
public class SchemaValidator {
    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);
    private static final String COMPONENTS_FIELD = "components";
    static final String VALIDATOR_SCHEMA_INVALID_JSON = "ERR11003";
    static final String VALIDATOR_SCHEMA = "ERR11004";

    private final JsonNode jsonNode;
    private final PathType pathType;
    private final Map<ValidationProfile, SchemaRegistry> registries = new ConcurrentHashMap<>();

    /**
     * Build a new validator with no API specification.
     * <p>
     * This will not perform any validation of $ref references that reference local schemas.
     *
     */
    public SchemaValidator() {
        this(null);
    }

    /**
     * Build a new validator with an API specification.
     * <p>
     * This will not perform any validation of $ref references that reference local schemas.
     *
     */
    public SchemaValidator(final OpenApi3 api) {
        this(api, false);
    }

    /**
     * Build a new validator for the given API specification.
     *
     * @param api The API to build the validator for. If provided, is used to retrieve schemas in components
     *            for use in references.
     */
    public SchemaValidator(final OpenApi3 api, final boolean legacyPathType) {
        this.jsonNode = api == null ? null : Overlay.toJson((OpenApi3Impl)api).get(COMPONENTS_FIELD);
        this.pathType = legacyPathType ? PathType.LEGACY : PathType.JSON_POINTER;
    }

    /**
     * Validate the given value against the given property schema.
     *
     * @param value The value to validate
     * @param schema The property schema to validate the value against
     * @param typeLoose Whether string values may be coerced while checking their type
     * @param nullableKeywordEnabled Whether the OpenAPI nullable keyword is enabled
     *
     * @return A status containing error code and description
     */
    public Status validate(final JsonNode value, final JsonNode schema, boolean typeLoose, boolean nullableKeywordEnabled) {
        return validate(value, schema, typeLoose, nullableKeywordEnabled, pathType);
    }

    /**
     * Validate the given value against the given property schema.
     *
     * @param value The value to validate
     * @param schema The property schema to validate the value against
     * @param typeLoose Whether string values may be coerced while checking their type
     * @param nullableKeywordEnabled Whether the OpenAPI nullable keyword is enabled
     * @param validationPathType The path format used in validation errors
     * @return A status containing error code and description
     */
    public Status validate(final JsonNode value, final JsonNode schema, boolean typeLoose,
                           boolean nullableKeywordEnabled, PathType validationPathType) {
        return validate(value, schema, typeLoose, nullableKeywordEnabled, validationPathType, false);
    }

    Status validate(final JsonNode value, final JsonNode schema, boolean typeLoose,
                    boolean nullableKeywordEnabled, PathType validationPathType,
                    boolean customMessageKeywordEnabled) {
        return doValidate(value, schema, typeLoose, nullableKeywordEnabled, validationPathType,
                customMessageKeywordEnabled, null);
    }

    /**
     * Validate the given value against the given property schema.
     *
     * @param value The value to validate
     * @param schema The property schema to validate the value against
     * @param at The property name being validated
     * @return Status object
     */
    public Status validate(final JsonNode value, final JsonNode schema, String at) {
        NodePath instanceLocation = new NodePath(pathType);
        if (at != null) {
            instanceLocation = instanceLocation.append(at);
        }
        return doValidate(value, schema, true, false, pathType, false, instanceLocation);
    }

    /**
     * Validate the given value against the given property schema.
     *
     * @param value The value to validate
     * @param schema The property schema to validate the value against
     * @param instanceLocation The location being validated
     * @return Status object
     */
    public Status validate(final JsonNode value, final JsonNode schema, NodePath instanceLocation) {
        PathType validationPathType = instanceLocation == null ? pathType : instanceLocation.getPathType();
        return doValidate(value, schema, true, false, validationPathType, false, instanceLocation);
    }

    private Status doValidate(final JsonNode value, final JsonNode schema, boolean typeLoose,
                              boolean nullableKeywordEnabled, PathType validationPathType,
                              boolean customMessageKeywordEnabled, NodePath instanceLocation) {
        requireNonNull(schema, "A schema is required");

        List<Error> processingReport = null;
        try {
            if(jsonNode != null) {
                ((ObjectNode)schema).set(COMPONENTS_FIELD, jsonNode);
            }
            Schema jsonSchema = getRegistry(typeLoose, nullableKeywordEnabled, validationPathType,
                    customMessageKeywordEnabled).getSchema(schema);
            processingReport = jsonSchema.validate(value);
        } catch (Exception e) {
            logger.error("Unable to validate the value against the OpenAPI schema", e);
            return new Status(VALIDATOR_SCHEMA_INVALID_JSON, e.getMessage());
        }

        if(processingReport != null && !processingReport.isEmpty()) {
            Error error = processingReport.get(0);
            return new Status(VALIDATOR_SCHEMA, formatError(error, instanceLocation));
        }

        return null;
    }

    private SchemaRegistry getRegistry(boolean typeLoose, boolean nullableKeywordEnabled, PathType validationPathType,
                                       boolean customMessageKeywordEnabled) {
        ValidationProfile profile = new ValidationProfile(typeLoose, nullableKeywordEnabled, validationPathType,
                customMessageKeywordEnabled);
        return registries.computeIfAbsent(profile, this::createRegistry);
    }

    private SchemaRegistry createRegistry(ValidationProfile profile) {
        SchemaRegistryConfig.Builder registryConfigBuilder = SchemaRegistryConfig.builder()
                .typeLoose(profile.typeLoose)
                .pathType(profile.pathType);
        if (profile.customMessageKeywordEnabled) {
            registryConfigBuilder.errorMessageKeyword("message");
        }
        SchemaRegistryConfig registryConfig = registryConfigBuilder.build();
        Dialect dialect = Dialects.getDraft202012();
        if (profile.nullableKeywordEnabled) {
            dialect = Dialect.builder(dialect)
                    .keyword(new NonValidationKeyword("nullable"))
                    .build();
        }
        return SchemaRegistry.withDefaultDialect(dialect,
                builder -> builder.schemaRegistryConfig(registryConfig));
    }

    private String formatError(Error error, NodePath instanceLocation) {
        if (instanceLocation == null || error.getInstanceLocation() == null) {
            return error.toString();
        }
        NodePath combinedLocation = instanceLocation;
        NodePath errorLocation = error.getInstanceLocation();
        for (int i = 0; i < errorLocation.getNameCount(); i++) {
            Object element = errorLocation.getElement(i);
            combinedLocation = element instanceof Number
                    ? combinedLocation.append(((Number) element).intValue())
                    : combinedLocation.append(String.valueOf(element));
        }
        return combinedLocation + ": " + error.getMessage();
    }

    private static final class ValidationProfile {
        private final boolean typeLoose;
        private final boolean nullableKeywordEnabled;
        private final PathType pathType;
        private final boolean customMessageKeywordEnabled;

        private ValidationProfile(boolean typeLoose, boolean nullableKeywordEnabled, PathType pathType,
                                  boolean customMessageKeywordEnabled) {
            this.typeLoose = typeLoose;
            this.nullableKeywordEnabled = nullableKeywordEnabled;
            this.pathType = requireNonNull(pathType, "A validation path type is required");
            this.customMessageKeywordEnabled = customMessageKeywordEnabled;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ValidationProfile)) return false;
            ValidationProfile that = (ValidationProfile) object;
            return typeLoose == that.typeLoose
                    && nullableKeywordEnabled == that.nullableKeywordEnabled
                    && customMessageKeywordEnabled == that.customMessageKeywordEnabled
                    && pathType == that.pathType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeLoose, nullableKeywordEnabled, pathType, customMessageKeywordEnabled);
        }
    }
}
