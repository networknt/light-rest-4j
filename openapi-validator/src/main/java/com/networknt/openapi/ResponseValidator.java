package com.networknt.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.*;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.status.Status;
import java.util.Optional;

public class ResponseValidator {
    private final SchemaValidator schemaValidator;
    private final SchemaValidatorsConfig config;
    static final String VALIDATOR_RESPONSE_CONTENT_UNEXPECTED = "ERR";
    static final String JSON_MEDIA_TYPE = "application/json";
    static final String GOOD_STATUS_CODE = "200";
    static final String DEFAULT_STATUS_CODE = "default";

    /**
     * Construct a new request validator with the given schema validator.
     */
    public ResponseValidator(SchemaValidatorsConfig config) {
        this.schemaValidator = new SchemaValidator(OpenApiHelper.openApi3);
        this.config = config;
    }

    public ResponseValidator() {
        this.schemaValidator = new SchemaValidator(OpenApiHelper.openApi3);
        this.config = new SchemaValidatorsConfig();
    }

    /**
     * validate a given response content object with status code "200" and media content type "application/json"
     * uri, httpMethod, JSON_MEDIA_TYPE("200"), DEFAULT_MEDIA_TYPE("application/json") is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri uri of the request
     * @param httpMethod eg. "put" or "get"
     * @return Status
     */
    public Status validateResponseContent(Object responseContent, String uri, String httpMethod) {
        return validateResponseContent(responseContent, uri, httpMethod, GOOD_STATUS_CODE);
    }

    /**
     * validate a given response content object with media content type "application/json"
     * uri, httpMethod, statusCode, DEFAULT_MEDIA_TYPE is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri uri of the request
     * @param httpMethod eg. "put" or "get"
     * @param statusCode eg. 200, 400
     * @return Status
     */
    public Status validateResponseContent(Object responseContent, String uri, String httpMethod, String statusCode) {
        return validateResponseContent(responseContent, uri, httpMethod, statusCode, JSON_MEDIA_TYPE);
    }

    /**
     * validate a given response content object with schema coordinate (uri, httpMethod, statusCode, mediaTypeName)
     * uri, httpMethod, statusCode, mediaTypeName is to locate the schema to validate
     * @param responseContent response content needs to be validated
     * @param uri uri of the request
     * @param httpMethod eg. "put" or "get"
     * @param statusCode eg. 200, 400
     * @param mediaTypeName eg. "application/json"
     * @return Status
     */
    public Status validateResponseContent(Object responseContent, String uri, String httpMethod, String statusCode, String mediaTypeName) {
        OpenApiOperation operation = getOpenApiOperation(uri, httpMethod);
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
     * @return Status
     */
    public Status validateResponseContent(Object responseContent, OpenApiOperation openApiOperation, String statusCode, String mediaTypeName) {

        JsonNode schema = getContentSchema(openApiOperation, statusCode, mediaTypeName);
        //if cannot find schema based on status code, try to get from "default"
        if(schema == null) {
            schema = getContentSchema(openApiOperation, DEFAULT_STATUS_CODE, mediaTypeName);
        }
        if (responseContent != null && schema == null) {
            return new Status(VALIDATOR_RESPONSE_CONTENT_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }
        config.setTypeLoose(false);
        return schemaValidator.validate(responseContent, schema, config);
    }

    /**
     * locate operation based on uri and httpMethod
     * @param uri uri of the request
     * @param httpMethod http method of the request
     * @return OpenApiOperation the wrapper of an api operation
     */
    private OpenApiOperation getOpenApiOperation(String uri, String httpMethod) {
        NormalisedPath requestPath = new ApiNormalisedPath(uri);
        Optional<NormalisedPath> maybeApiPath = OpenApiHelper.findMatchingApiPath(requestPath);
        if (!maybeApiPath.isPresent()) {
            return null;
        }

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = OpenApiHelper.openApi3.getPath(openApiPathString.original());

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
}
