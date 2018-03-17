/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

import com.networknt.body.BodyHandler;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.RequestBody;
import com.networknt.status.Status;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Validate a request data against a given API operation defined in the OpenAPI Spec.
 * The specific operation is looked up by API endpoint (path + httpMethod)
 *
 * @author Steve Hu
 */
public class RequestValidator {

    static final Logger logger = LoggerFactory.getLogger(RequestValidator.class);
    static final String VALIDATOR_REQUEST_BODY_UNEXPECTED = "ERR11013";
    static final String VALIDATOR_REQUEST_BODY_MISSING = "ERR11014";
    static final String VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING = "ERR11017";
    static final String VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING = "ERR11000";

    private final SchemaValidator schemaValidator;

    /**
     * Construct a new request validator with the given schema validator.
     *
     * @param schemaValidator The schema validator to use when validating request bodies
     */
    public RequestValidator(final SchemaValidator schemaValidator) {
        this.schemaValidator = requireNonNull(schemaValidator, "A schema validator is required");
    }

    /**
     * Validate the request against the given API operation
     * @param requestPath normalised path
     * @param exchange The HttpServerExchange to validate
     * @param openApiOperation OpenAPI operation
     * @return A validation report containing validation errors
     */
    public Status validateRequest(final NormalisedPath requestPath, HttpServerExchange exchange, OpenApiOperation openApiOperation) {
        requireNonNull(requestPath, "A request path is required");
        requireNonNull(exchange, "An exchange is required");
        requireNonNull(openApiOperation, "An OpenAPI operation is required");

        Status status = validatePathParameters(requestPath, openApiOperation);
        if(status != null) return status;

        status = validateQueryParameters(exchange, openApiOperation);
        if(status != null) return status;

        status = validateHeader(exchange, openApiOperation);
        if(status != null) return status;

        Object body = exchange.getAttachment(BodyHandler.REQUEST_BODY);
        status = validateRequestBody(body, openApiOperation);

        return status;
    }

    private Status validateRequestBody(Object requestBody, final OpenApiOperation openApiOperation) {
        final RequestBody specBody = openApiOperation.getOperation().getRequestBody();

        if (requestBody != null && specBody == null) {
            return new Status(VALIDATOR_REQUEST_BODY_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }

        if (specBody == null || !specBody.isPresent()) {
            return null;
        }

        if (requestBody == null) {
            if (specBody != null && specBody.getRequired()) {
                if(BodyHandler.config.isEnabled()) {
                    // BodyHandler is enable and there is no error returned, that means the request body is empty. This is an validation error.
                    return new Status(VALIDATOR_REQUEST_BODY_MISSING, openApiOperation.getMethod(), openApiOperation.getPathString().original());
                } else {
                    // most likely, the BodyHandler is missing from the request chain and we cannot find the body attachment in the exchange
                    // the second scenario is that application/json is not in the request header and BodyHandler is skipped.
                    logger.warn("Body object doesn't exist in exchange attachment. Most likely the BodyHandler is not in the request chain before RequestValidator or reqeust misses application/json content type header");
                }
            }
            return null;
        }
        return schemaValidator.validate(requestBody, specBody.getContentMediaType("application/json").getSchema().toJson());
    }

    private Status validatePathParameters(final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        Status status = null;
        for (int i = 0; i < openApiOperation.getPathString().parts().size(); i++) {
            if (!openApiOperation.getPathString().isParam(i)) {
                continue;
            }

            final String paramName = openApiOperation.getPathString().paramName(i);
            final String paramValue = requestPath.part(i);

            final Optional<Parameter> parameter = openApiOperation.getOperation().getParameters()
                    .stream()
                    .filter(p -> p.getIn().equalsIgnoreCase("PATH"))
                    .filter(p -> p.getName().equalsIgnoreCase(paramName))
                    .findFirst();

            if (parameter.isPresent()) {
                return schemaValidator.validate(paramValue, parameter.get().toJson());
            }
        }
        return status;
    }

    private Status validateQueryParameters(final HttpServerExchange exchange,
                                           final OpenApiOperation openApiOperation) {
        Optional<Status> optional = openApiOperation
                .getOperation()
                .getParameters()
                .stream()
                .filter(p -> p.getIn().equalsIgnoreCase("QUERY"))
                .map(p -> validateQueryParameter(exchange, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();
        if(optional.isPresent()) {
            return optional.get();
        } else {
            return null;
        }
    }


    private Status validateQueryParameter(final HttpServerExchange exchange,
                                          final OpenApiOperation openApiOperation,
                                          final Parameter queryParameter) {

        final Collection<String> queryParameterValues = exchange.getQueryParameters().get(queryParameter.getName());

        if ((queryParameterValues == null || queryParameterValues.isEmpty())) {
            if(queryParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING, queryParameter.getName(), openApiOperation.getPathString().original());
            }
        } else {

            Optional<Status> optional = queryParameterValues
                    .stream()
                    .map((v) -> schemaValidator.validate(v, queryParameter.toJson()))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            }
        }
        return null;
    }

    private Status validateHeader(final HttpServerExchange exchange,
                                  final OpenApiOperation openApiOperation) {

        // validate path level parameters for headers first.
        Optional<Status> optional = openApiOperation
                .getPathObject()
                .getParameters()
                .stream()
                .filter(p -> p.getIn().equalsIgnoreCase("header"))
                .map(p -> validateHeader(exchange, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();
        if(optional.isPresent()) {
            return optional.get();
        } else {
            // validate operation level parameter for headers second.
            optional = openApiOperation
                    .getOperation()
                    .getParameters()
                    .stream()
                    .filter(p -> p.getIn().equalsIgnoreCase("header"))
                    .map(p -> validateHeader(exchange, openApiOperation, p))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            } else {
                return null;
            }
        }
    }

    private Status validateHeader(final HttpServerExchange exchange,
                                  final OpenApiOperation openApiOperation,
                                  final Parameter headerParameter) {

        final HeaderValues headerValues = exchange.getRequestHeaders().get(headerParameter.getName());
        if ((headerValues == null || headerValues.isEmpty())) {
            if(headerParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING, headerParameter.getName(), openApiOperation.getPathString().original());
            }
        } else {

            Optional<Status> optional = headerValues
                    .stream()
                    .map((v) -> schemaValidator.validate(v, headerParameter.toJson()))
                    .filter(s -> s != null)
                    .findFirst();
            if(optional.isPresent()) {
                return optional.get();
            }
        }
        return null;
    }
}
