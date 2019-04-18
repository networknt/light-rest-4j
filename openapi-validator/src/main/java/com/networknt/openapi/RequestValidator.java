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

import static java.util.Objects.requireNonNull;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.networknt.body.BodyHandler;
import com.networknt.jsonoverlay.Overlay;
import com.networknt.oas.model.Parameter;
import com.networknt.oas.model.RequestBody;
import com.networknt.oas.model.impl.RequestBodyImpl;
import com.networknt.oas.model.impl.SchemaImpl;
import com.networknt.openapi.parameter.ParameterType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.status.Status;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

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

        Status status = validateRequestParameters(exchange, requestPath, openApiOperation);
        if(status != null) return status;

        Object body = exchange.getAttachment(BodyHandler.REQUEST_BODY);
        // skip the body validation if body parser is not in the request chain.
        if(body == null && ValidatorHandler.config.skipBodyValidation) return null;
        status = validateRequestBody(body, openApiOperation);

        return status;
    }

    private Status validateRequestBody(Object requestBody, final OpenApiOperation openApiOperation) {
        final RequestBody specBody = openApiOperation.getOperation().getRequestBody();

        if (requestBody != null && specBody == null) {
            return new Status(VALIDATOR_REQUEST_BODY_UNEXPECTED, openApiOperation.getMethod(), openApiOperation.getPathString().original());
        }

        if (specBody == null || !Overlay.isPresent((RequestBodyImpl)specBody)) {
            return null;
        }

        if (requestBody == null) {
            if (specBody.getRequired() != null && specBody.getRequired()) {
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
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(false);
        return schemaValidator.validate(requestBody, Overlay.toJson((SchemaImpl)specBody.getContentMediaType("application/json").getSchema()), config);
    }
    
    private Status validateRequestParameters(final HttpServerExchange exchange, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
        Status status = validatePathParameters(exchange, requestPath, openApiOperation);
        if(status != null) return status;

        status = validateQueryParameters(exchange, openApiOperation);
        if(status != null) return status;

        status = validateHeaderParameters(exchange, openApiOperation);
        if(status != null) return status;
        
        status = validateCookieParameters(exchange, openApiOperation);
        if(status != null) return status;  
        
        return null;
    }

    private Status validatePathParameters(final HttpServerExchange exchange, final NormalisedPath requestPath, final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getOperation().getParameters(), ParameterType.PATH);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return result.getStatus();
    	}
    	
    	// validate values that cannot be deserialized or do not need to be deserialized
    	Status status = null;
        for (int i = 0; i < openApiOperation.getPathString().parts().size(); i++) {
            if (!openApiOperation.getPathString().isParam(i)) {
                continue;
            }

            final String paramName = openApiOperation.getPathString().paramName(i);
            final Optional<Parameter> parameter = result.getSkippedParameters()
                    .stream()
                    .filter(p -> p.getName().equalsIgnoreCase(paramName))
                    .findFirst();

            if (parameter.isPresent()) {
	            String paramValue = requestPath.part(i); // If it can't be UTF-8 decoded, use directly.
	            try {
	                paramValue = URLDecoder.decode(requestPath.part(i), "UTF-8");
	            } catch (Exception e) {
	                logger.info("Path parameter cannot be decoded, it will be used directly");
	            }

                return schemaValidator.validate(paramValue, Overlay.toJson((SchemaImpl)(parameter.get().getSchema())));
            }
        }
        return status;
    }

    private Status validateQueryParameters(final HttpServerExchange exchange,
                                           final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getOperation().getParameters(), ParameterType.QUERY);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return result.getStatus();
    	}
    	
    	// validate values that cannot be deserialized or do not need to be deserialized
        Optional<Status> optional = result.getSkippedParameters()
        		.stream()
                .map(p -> validateQueryParameter(exchange, openApiOperation, p))
                .filter(s -> s != null)
                .findFirst();
        
        return optional.orElse(null);
    }


    private Status validateQueryParameter(final HttpServerExchange exchange,
                                          final OpenApiOperation openApiOperation,
                                          final Parameter queryParameter) {

        final Collection<String> queryParameterValues = exchange.getQueryParameters().get(queryParameter.getName());

        if ((queryParameterValues == null || queryParameterValues.isEmpty())) {
            if(queryParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_QUERY_MISSING, queryParameter.getName(), openApiOperation.getPathString().original());
            }
        // Validate the value contains by queryParameterValue, if it is the only elements inside the array deque.
        // Since if the queryParameterValue's length smaller than 2, it means the query parameter is not an array,
        // thus not necessary to apply array validation to this value.
        } else if (queryParameterValues.size() < 2) {

            Optional<Status> optional = queryParameterValues
                    .stream()
                    .map((v) -> schemaValidator.validate(v, Overlay.toJson((SchemaImpl)queryParameter.getSchema())))
                    .filter(s -> s != null)
                    .findFirst();
            
            return optional.orElse(null);
        // Validate the queryParameterValue directly instead of validating its elements, if the length of this array deque larger than 2.
        // Since if the queryParameterValue's length larger than 2, it means the query parameter is an array.
        // thus array validation should be applied, for example, validate the length of the array.
        } else {
            return schemaValidator.validate(queryParameterValues, Overlay.toJson((SchemaImpl)queryParameter.getSchema()));
        }
        return null;
    }

    private Status validateHeaderParameters(final HttpServerExchange exchange,
                                  final OpenApiOperation openApiOperation) {

        // validate path level parameters for headers first.
        Optional<Status> optional = validatePathLevelHeaders(exchange, openApiOperation);
        if(optional.isPresent()) {
            return optional.get();
        } else {
            // validate operation level parameter for headers second.
            optional = validateOperationLevelHeaders(exchange, openApiOperation);
            return optional.orElse(null);
        }
    }
    
    private Optional<Status> validatePathLevelHeaders(final HttpServerExchange exchange, final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getPathObject().getParameters(), ParameterType.HEADER);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return Optional.ofNullable(result.getStatus());
    	}
    	
    	return result.getSkippedParameters().stream()
				        .map(p -> validateHeader(exchange, openApiOperation, p))
				        .filter(s -> s != null)
				        .findFirst();
    }
    
    
    
    private Optional<Status> validateOperationLevelHeaders(final HttpServerExchange exchange, final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getOperation().getParameters(), ParameterType.HEADER);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return Optional.ofNullable(result.getStatus());
    	}
    	
    	return result.getSkippedParameters().stream()
				        .map(p -> validateHeader(exchange, openApiOperation, p))
				        .filter(s -> s != null)
				        .findFirst();
    }  
    
	private Status validateCookieParameters(final HttpServerExchange exchange,
			final OpenApiOperation openApiOperation) {

		// validate path level parameters for cookies first.
		Optional<Status> optional = validatePathLevelCookies(exchange, openApiOperation);
		if (optional.isPresent()) {
			return optional.get();
		} else {
			// validate operation level parameter for cookies second.
			optional = validateOperationLevelCookies(exchange, openApiOperation);
			return optional.orElse(null);
		}
	}
	
    private Optional<Status> validatePathLevelCookies(final HttpServerExchange exchange, final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getPathObject().getParameters(), ParameterType.COOKIE);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return Optional.ofNullable(result.getStatus());
    	}
    	
    	return result.getSkippedParameters().stream()
				        .map(p -> validateHeader(exchange, openApiOperation, p))
				        .filter(s -> s != null)
				        .findFirst();
    }
    
    
    
    private Optional<Status> validateOperationLevelCookies(final HttpServerExchange exchange, final OpenApiOperation openApiOperation) {
    	ValidationResult result = validateDeserializedValues(exchange, openApiOperation.getOperation().getParameters(), ParameterType.COOKIE);
    	
    	if (null!=result.getStatus() || result.getSkippedParameters().isEmpty()) {
    		return Optional.ofNullable(result.getStatus());
    	}
    	
    	return result.getSkippedParameters().stream()
				        .map(p -> validateHeader(exchange, openApiOperation, p))
				        .filter(s -> s != null)
				        .findFirst();
    }	

    private Status validateHeader(final HttpServerExchange exchange,
                                  final OpenApiOperation openApiOperation,
                                  final Parameter headerParameter) {
        final HeaderValues headerValues = exchange.getRequestHeaders().get(new HttpString(headerParameter.getName()));
        if ((headerValues == null || headerValues.isEmpty())) {
            if(headerParameter.getRequired()) {
                return new Status(VALIDATOR_REQUEST_PARAMETER_HEADER_MISSING, headerParameter.getName(), openApiOperation.getPathString().original());
            }
        } else {
            Optional<Status> optional = headerValues
                    .stream()
                    .map((v) -> schemaValidator.validate(v, Overlay.toJson((SchemaImpl)headerParameter.getSchema())))
                    .filter(s -> s != null)
                    .findFirst();
            return optional.orElse(null);
        }
        return null;
    }
    

    private ValidationResult validateDeserializedValues(final HttpServerExchange exchange, final Collection<Parameter> parameters, final ParameterType type) {
    	ValidationResult validationResult = new ValidationResult();
    	
    	parameters.stream()
		        .filter(p -> ParameterType.is(p.getIn(), type))
		        .forEach(p->{
		        	Object deserializedValue = getDeserializedValue(exchange, p.getName(), type);
		        	if (null==deserializedValue) {
		        		validationResult.addSkipped(p);
		        	}else {
		        		Status s = schemaValidator.validate(deserializedValue, Overlay.toJson((SchemaImpl)(p.getSchema())));
		        		validationResult.addStatus(s);
		        	}
		        });
    	
    	return validationResult;
    }
    
    private Object getDeserializedValue(final HttpServerExchange exchange, final String name, final ParameterType type) {
    	if (null!=type && StringUtils.isNotBlank(name)) {
			switch(type){
			case QUERY:
				return OpenApiHandler.getQueryParameters(exchange,true).get(name);
			case PATH:
				return OpenApiHandler.getPathParameters(exchange,true).get(name);
			case HEADER:
				return OpenApiHandler.getHeaderParameters(exchange,true).get(name);
			case COOKIE:
				return OpenApiHandler.getCookieParameters(exchange,true).get(name);
			}   		
    	}
    	
    	return null;
    }
    
    
    class ValidationResult {
    	private Set<Parameter> skippedParameters = new HashSet<>();;
    	private List<Status> statuses = new ArrayList<>();
    	
    	public void addSkipped(Parameter p) {
    		skippedParameters.add(p);
    	}
    	
    	public void addStatus(Status s) {
    		if (null!=s) {
    			statuses.add(s);
    		}
    	}
    	
    	public Set<Parameter> getSkippedParameters(){
    		return Collections.unmodifiableSet(skippedParameters);
    	}
    	
    	public Status getStatus() {
    		return statuses.isEmpty()?null:statuses.get(0);
    	}
    	
    	public List<Status> getAllStatueses(){
    		return Collections.unmodifiableList(statuses);
    	}
    }
}
