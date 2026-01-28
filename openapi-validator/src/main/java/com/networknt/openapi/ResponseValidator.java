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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.config.Config;
import com.networknt.dump.StoreResponseStreamSinkConduit;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.*;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.status.Status;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.networknt.openapi.ValidatorHandler.*;
import static java.util.Objects.requireNonNull;

public class ResponseValidator {
    private final SchemaValidator schemaValidator;
    private final ValidatorConfig config;
    private final SchemaValidatorsConfig schemaValidatorsConfig;
    private static final String VALIDATOR_RESPONSE_CONTENT_UNEXPECTED = "ERR11018";
    private static final String REQUIRED_RESPONSE_HEADER_MISSING = "ERR11019";
    private static final String CONTENT_TYPE_MISMATCH = "ERR10015";

    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String GOOD_STATUS_CODE = "200";
    private static final String DEFAULT_STATUS_CODE = "default";
    private static final Logger logger = LoggerFactory.getLogger(ResponseValidator.class);

    public ResponseValidator(SchemaValidator schemaValidator, ValidatorConfig config) {
        this.schemaValidator = requireNonNull(schemaValidator, "A schema validator is required");
        this.config = config;
        this.schemaValidatorsConfig = new SchemaValidatorsConfig();
    }

    /**
     * validate a given response content object with status code "200" and media content type "application/json"
     * uri, httpMethod, JSON_MEDIA_TYPE("200"), DEFAULT_MEDIA_TYPE("application/json") is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param exchange HttpServerExchange in handler
     * @return Status return null if no validation errors
     */
    public Status validateResponseContent(String responseContent, HttpServerExchange exchange) {
        return validateResponseContent(responseContent, exchange.getRequestURI(), exchange.getRequestMethod().toString().toLowerCase(), String.valueOf(exchange.getStatusCode()));
    }

    /**
     * validate a given response content object with status code "200" and media content type "application/json"
     * uri, httpMethod, JSON_MEDIA_TYPE("200"), DEFAULT_MEDIA_TYPE("application/json") is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri original uri of the request
     * @param httpMethod eg. "put" or "get"
     * @return Status return null if no validation errors
     */
    public Status validateResponseContent(String responseContent, String uri, String httpMethod) {
        return validateResponseContent(responseContent, uri, httpMethod, GOOD_STATUS_CODE);
    }

    /**
     * validate a given response content object with media content type "application/json"
     * uri, httpMethod, statusCode, DEFAULT_MEDIA_TYPE is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri original uri of the request
     * @param httpMethod eg. "put" or "get"
     * @param statusCode eg. 200, 400
     * @return Status return null if no validation errors
     */
    public Status validateResponseContent(String responseContent, String uri, String httpMethod, String statusCode) {
        return validateResponseContent(responseContent, uri, httpMethod, statusCode, JSON_MEDIA_TYPE);
    }

    /**
     * validate a given response content object with schema coordinate (uri, httpMethod, statusCode, mediaTypeName)
     * uri, httpMethod, statusCode, mediaTypeName is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri original uri of the request
     * @param httpMethod eg. "put" or "get"
     * @param statusCode eg. 200, 400
     * @param mediaTypeName eg. "application/json"
     * @return Status return null if no validation errors
     */
    public Status validateResponseContent(String responseContent, String uri, String httpMethod, String statusCode, String mediaTypeName) {
        OpenApiOperation operation = null;
        try {
            operation = getOpenApiOperation(uri, httpMethod);
        } catch (URISyntaxException e) {
            logger.error(e.getMessage());
            return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, httpMethod, uri);
        }
        if(operation == null) {
            return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, httpMethod, uri);
        }
        return validateResponseContent(responseContent, operation, statusCode, mediaTypeName);
    }

    /**
     * validate a given response content object
     * @param responseContent response content needs to be validated
     * @param openApiOperation OpenApi Operation which is located by uri and httpMethod
     * @param statusCode eg. 200, 400
     * @param mediaTypeName eg. "application/json"
     * @return Status return null if no validation errors
     */
    public Status validateResponseContent(String responseContent, OpenApiOperation openApiOperation, String statusCode, String mediaTypeName) {
        JsonNode schema = getContentSchema(openApiOperation, statusCode, mediaTypeName);
        //if cannot find schema based on status code, try to get from "default"
        if(schema == null || schema.isMissingNode()) {
            // if corresponding response exist but also does not contain any schema, pass validation
            if (openApiOperation.getOperation().getResponses().containsKey(String.valueOf(statusCode))) {
                return null;
            }
            schema = getContentSchema(openApiOperation, DEFAULT_STATUS_CODE, mediaTypeName);
            // if default also does not contain any schema, pass validation
            if (schema == null || schema.isMissingNode()) return null;
        }
        if ((responseContent != null && schema == null) ||
                (responseContent == null && schema != null)) {
            return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }
        schemaValidatorsConfig.setTypeLoose(false);
        schemaValidatorsConfig.setHandleNullableField(config.isHandleNullableField());

        JsonNode responseNode;
        responseContent = responseContent.trim();
        if(responseContent.startsWith("{") || responseContent.startsWith("[")) {
            try {
                responseNode = Config.getInstance().getMapper().readTree(responseContent);
            } catch (Exception e) {
                return new Status(CONTENT_TYPE_MISMATCH, "application/json");
            }
        } else {
            return new Status(CONTENT_TYPE_MISMATCH, "application/json");
        }
        return schemaValidator.validate(responseNode, schema, schemaValidatorsConfig);
    }

    /**
     * try to convert a string with json style to a structured object.
     * @param s
     * @return Object
     */
    private Object convertStrToObjTree(String s) {
        Object contentObj = null;
        if (s != null) {
            s = s.trim();
            try {
                if (s.startsWith("{")) {
                    contentObj = Config.getInstance().getMapper().readValue(s, new TypeReference<HashMap<String, Object>>() {
                    });
                } else if (s.startsWith("[")) {
                    contentObj = Config.getInstance().getMapper().readValue(s, new TypeReference<List<Object>>() {
                    });
                } else {
                    logger.error("cannot deserialize json str: {}", s);
                    return null;
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        return contentObj;
    }

    /**
     * locate operation based on uri and httpMethod
     * @param uri original uri of the request
     * @param httpMethod http method of the request
     * @return OpenApiOperation the wrapper of an api operation
     */
    private OpenApiOperation getOpenApiOperation(String uri, String httpMethod) throws URISyntaxException {
        String uriWithoutQuery = new URI(uri).getPath();
        NormalisedPath requestPath = new ApiNormalisedPath(uriWithoutQuery, OpenApiHandler.helper.basePath);
        Optional<NormalisedPath> maybeApiPath = OpenApiHandler.helper.findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            return null;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = OpenApiHandler.helper.openApi3.getPath(openApiPathString.original());

        final Operation operation = path.getOperation(httpMethod);
        return new OpenApiOperation(openApiPathString, path, httpMethod, operation);
    }

    private JsonNode getContentSchema(OpenApiOperation operation, String statusCode, String mediaTypeStr) {
        Schema schema;
        Optional<Response> response = Optional.ofNullable(operation.getOperation().getResponse(String.valueOf(statusCode)));
        if(response.isPresent()) {
            Optional<MediaType> mediaType = Optional.ofNullable(response.get().getContentMediaType(mediaTypeStr));
            if(mediaType.isPresent()) {
                schema = mediaType.get().getSchema();
                JsonNode schemaNode = schema == null ? null : Overlay.toJson((SchemaImpl)schema);
                return schemaNode;
            }
        }
        return null;
    }

    public Status validateResponse(HttpServerExchange exchange, OpenApiOperation openApiOperation) {
        requireNonNull(exchange, "An exchange is required");
        requireNonNull(openApiOperation, "An OpenAPI operation is required");
        String statusCode = String.valueOf(exchange.getStatusCode());
        Status status = validateHeaders(exchange, openApiOperation, statusCode);
        if(status != null) return status;

        byte[] responseBody = exchange.getAttachment(StoreResponseStreamSinkConduit.RESPONSE);
        String mediaType = exchange.getResponseHeaders().get(Headers.CONTENT_TYPE) == null ? "" : exchange.getResponseHeaders().get(Headers.CONTENT_TYPE).getFirst();
        String body = responseBody == null ? null : new String(responseBody);
        status = validateResponseContent(body, openApiOperation, statusCode, mediaType);

        return status;
    }

    private Status validateHeaders(HttpServerExchange exchange, OpenApiOperation operation, String statusCode) {
        Optional<Response> response = Optional.ofNullable(operation.getOperation().getResponse(statusCode));
        if(response.isPresent()) {
            Map<String, Header> headerMap = response.get().getHeaders();
            Optional<Status> optional = headerMap.entrySet()
                    .stream()
                    //based on OpenAPI specification, ignore "Content-Type" header
                    //If a response header is defined with the name "Content-Type", it SHALL be ignored. - https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.0.md#responseObject
                    .filter(entry -> !Headers.CONTENT_TYPE_STRING.equals(entry.getKey()))
                    .map(p -> validateHeader(exchange, p.getKey(), p.getValue()))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            }
        }
        return null;
    }

    private Status validateHeader(HttpServerExchange exchange, String headerName, Header operationHeader) {
        final HeaderValues headerValues = exchange.getResponseHeaders().get(headerName);
        SchemaValidatorsConfig schemaValidatorsConfig = new SchemaValidatorsConfig();
        //header won't tell if it's a real string or not. needs trying to convert.
        schemaValidatorsConfig.setTypeLoose(true);
        schemaValidatorsConfig.setHandleNullableField(config.isHandleNullableField());
        if ((headerValues == null || headerValues.isEmpty())) {
            if(Boolean.TRUE.equals(operationHeader.getRequired())) {
                return new Status(REQUIRED_RESPONSE_HEADER_MISSING, headerName);
            }
        } else {
            Optional<Status> optional = headerValues
                    .stream()
                    .map((v) -> schemaValidator.validate(new TextNode(v), Overlay.toJson((SchemaImpl)operationHeader.getSchema()),  schemaValidatorsConfig))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            }
        }
        return null;
    }
}
