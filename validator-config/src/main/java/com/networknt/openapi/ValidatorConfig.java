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
import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import com.networknt.config.schema.ArrayField;
import com.networknt.config.schema.BooleanField;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.OutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Validator configuration class that maps to validator.yml properties
 *
 * @author Steve Hu
 */
@ConfigSchema(
        configKey = "openapi-validator",
        configName = "openapi-validator",
        configDescription = "Default openapi-validator configuration",
        outputFormats = {OutputFormat.JSON_SCHEMA, OutputFormat.YAML}
)
public class ValidatorConfig {
    public static final String CONFIG_NAME = "openapi-validator";
    private static final Logger logger = LoggerFactory.getLogger(ValidatorConfig.class);
    private static final String ENABLED = "enabled";
    private static final String LOG_ERROR = "logError";
    private static final String LEGACY_PATH_TYPE = "legacyPathType";
    private static final String SKIP_BODY_VALIDATION = "skipBodyValidation";
    private static final String VALIDATE_RESPONSE = "validateResponse";
    private static final String HANDLE_NULLABLE_FIELD = "handleNullableField";
    private static final String SKIP_PATH_PREFIXES = "skipPathPrefixes";

    private Map<String, Object> mappedConfig;
    private final Config config;

    @BooleanField(
            configFieldName = ENABLED,
            externalizedKeyName = ENABLED,
            externalized = true,
            defaultValue = "true",
            description = "Enable request validation. Response validation is not done on the server but client."
    )
    boolean enabled;

    @BooleanField(
            configFieldName = LOG_ERROR,
            externalizedKeyName = LOG_ERROR,
            externalized = true,
            defaultValue = "true",
            description = "Log error message if validation error occurs"
    )
    boolean logError;

    @BooleanField(
            configFieldName = LEGACY_PATH_TYPE,
            externalizedKeyName = LEGACY_PATH_TYPE,
            externalized = true,
            defaultValue = "false",
            description = "By default, the json-schema-validator will return the error message using JSON_POINTER path type. If you want\n" +
                    "to make sure that the error message is the same as the older version, you can set the legacyPathType to true."
    )
    boolean legacyPathType;

    @BooleanField(
            configFieldName = SKIP_BODY_VALIDATION,
            externalizedKeyName = SKIP_BODY_VALIDATION,
            externalized = true,
            defaultValue = "false",
            description = "Skip body validation set to true if used in light-router, light-proxy and light-spring-boot."
    )
    boolean skipBodyValidation;

    @BooleanField(
            configFieldName = VALIDATE_RESPONSE,
            externalizedKeyName = VALIDATE_RESPONSE,
            externalized = true,
            defaultValue = "false",
            description = "Enable response validation."
    )
    boolean validateResponse;

    @BooleanField(
            configFieldName = HANDLE_NULLABLE_FIELD,
            externalizedKeyName = HANDLE_NULLABLE_FIELD,
            externalized = true,
            defaultValue = "true",
            description = "When a field is set as nullable in the OpenAPI specification, the schema validator validates that it is nullable\n" +
                    "however continues with validation against the nullable field\n" +
                    "\n" +
                    "If handleNullableField is set to true && incoming field is nullable && value is field: null --> succeed\n" +
                    "If handleNullableField is set to false && incoming field is nullable && value is field: null --> it is up to the type\n" +
                    "validator using the SchemaValidator to handle it."
    )
    boolean handleNullableField;

    @ArrayField(
            configFieldName = SKIP_PATH_PREFIXES,
            externalizedKeyName = SKIP_PATH_PREFIXES,
            externalized = true,
            description = "Define a list of path prefixes to skip the validation to ease the configuration for the\n" +
                    "handler.yml so that users can define some endpoints without validation even through it uses\n" +
                    "the default chain. This is particularly useful in the light-gateway use case as the same\n" +
                    "instance might be shared with multiple consumers and providers with different validation\n" +
                    "requirement. The format is a list of strings separated with commas or a JSON list in\n" +
                    "values.yml definition from config server, or you can use yaml format in this file.",
            items = String.class
    )
    private List<String> skipPathPrefixes;

    private ValidatorConfig(String configName) {
        config = Config.getInstance();
        mappedConfig = config.getJsonMapConfigNoCache(configName);
        setConfigData();
        setConfigList();
    }
    private ValidatorConfig() {
        this(CONFIG_NAME);
    }

    public static ValidatorConfig load(String configName) {
        return new ValidatorConfig(configName);
    }

    public static ValidatorConfig load() {
        return new ValidatorConfig();
    }

    public void reload() {
        mappedConfig = config.getJsonMapConfigNoCache(CONFIG_NAME);
        setConfigData();
        setConfigList();
    }

    public void reload(String configName) {
        mappedConfig = config.getJsonMapConfigNoCache(configName);
        setConfigData();
        setConfigList();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogError() { return logError; }

    public void setLogError(boolean logError) { this.logError = logError; }

    public boolean isLegacyPathType() {
        return legacyPathType;
    }

    public void setLegacyPathType(boolean legacyPathType) {
        this.legacyPathType = legacyPathType;
    }

    public boolean isSkipBodyValidation() {
        return skipBodyValidation;
    }

    public void setSkipBodyValidation(boolean skipBodyValidation) {
        this.skipBodyValidation = skipBodyValidation;
    }

    public boolean isValidateResponse() {
        return validateResponse;
    }

    public void setValidateResponse(boolean validateResponse) {
        this.validateResponse = validateResponse;
    }

	public boolean isHandleNullableField() {
		return handleNullableField;
	}

	public void setHandleNullableField(boolean handleNullableField) {
		this.handleNullableField = handleNullableField;
	}

    public List<String> getSkipPathPrefixes() {
        return skipPathPrefixes;
    }

    public void setSkipPathPrefixes(List<String> skipPathPrefixes) {
        this.skipPathPrefixes = skipPathPrefixes;
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }

    Config getConfig() {
        return config;
    }

    private void setConfigData() {
        if(getMappedConfig() != null) {
            Object object = getMappedConfig().get(ENABLED);
            if(object != null) enabled = Config.loadBooleanValue(ENABLED, object);
            object = getMappedConfig().get(LOG_ERROR);
            if(object != null) logError = Config.loadBooleanValue(LOG_ERROR, object);
            object = getMappedConfig().get(LEGACY_PATH_TYPE);
            if(object != null) legacyPathType = Config.loadBooleanValue(LEGACY_PATH_TYPE, object);
            object = getMappedConfig().get(SKIP_BODY_VALIDATION);
            if(object != null) skipBodyValidation = Config.loadBooleanValue(SKIP_BODY_VALIDATION, object);
            object = getMappedConfig().get(VALIDATE_RESPONSE);
            if(object != null) validateResponse = Config.loadBooleanValue(VALIDATE_RESPONSE, object);
            object = getMappedConfig().get(HANDLE_NULLABLE_FIELD);
            if(object != null) handleNullableField = Config.loadBooleanValue(HANDLE_NULLABLE_FIELD, object);
        }
    }

    private void setConfigList() {
        if (mappedConfig != null && mappedConfig.get(SKIP_PATH_PREFIXES) != null) {
            Object object = mappedConfig.get(SKIP_PATH_PREFIXES);
            skipPathPrefixes = new ArrayList<>();
            if(object instanceof String) {
                String s = (String)object;
                s = s.trim();
                if(logger.isTraceEnabled()) logger.trace("s = " + s);
                if(s.startsWith("[")) {
                    // json format
                    try {
                        skipPathPrefixes = Config.getInstance().getMapper().readValue(s, new TypeReference<List<String>>() {});
                    } catch (Exception e) {
                        throw new ConfigException("could not parse the skipPathPrefixes json with a list of strings.");
                    }
                } else {
                    // comma separated
                    skipPathPrefixes = Arrays.asList(s.split("\\s*,\\s*"));
                }
            } else if (object instanceof List) {
                List prefixes = (List)object;
                prefixes.forEach(item -> {
                    skipPathPrefixes.add((String)item);
                });
            } else {
                throw new ConfigException("skipPathPrefixes must be a string or a list of strings.");
            }
        }
    }

}
