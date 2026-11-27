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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import com.networknt.config.schema.ArrayField;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.IntegerField;
import com.networknt.config.schema.OutputFormat;
import com.networknt.config.schema.StringField;
import com.networknt.server.ModuleRegistry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Configuration of the {@link OpenApiMcpHandler}, loaded from openapi-mcp.yaml.
 */
@ConfigSchema(
        configKey = "openapi-mcp",
        configName = "openapi-mcp",
        configDescription = "Configuration of the openapi-mcp handler, which exposes the endpoints of an OpenAPI specification as MCP tools.",
        outputFormats = {OutputFormat.JSON_SCHEMA, OutputFormat.YAML}
)
public class OpenApiMcpConfig {
    public static final String CONFIG_NAME = "openapi-mcp";
    private static final String DOWNSTREAM_HOST = "downstreamHost";
    private static final String ENDPOINTS = "endpoints";
    private static final String MAX_RESPONSE_BODY_SIZE = "maxResponseBodySize";

    @StringField(
            configFieldName = DOWNSTREAM_HOST,
            externalizedKeyName = DOWNSTREAM_HOST,
            description = "Base URL of the backend API, e.g. https://pets.example.com. The endpoint path is appended to it."
    )
    private String downstreamHost;

    @ArrayField(
            configFieldName = ENDPOINTS,
            externalizedKeyName = ENDPOINTS,
            items = String.class,
            description = "Endpoints exposed as MCP tools, in the light-4j endpoint format {path}@{method}, e.g. /v1/pets/{petId}@get.\n" +
                    "The path includes the base path of the spec's first servers URL. Tools are listed in this order.\n" +
                    "The format is a YAML list, or a JSON array or comma-separated string in values.yml."
    )
    private List<String> endpoints = List.of();

    @IntegerField(
            configFieldName = MAX_RESPONSE_BODY_SIZE,
            externalizedKeyName = MAX_RESPONSE_BODY_SIZE,
            defaultValue = "1048576",
            min = 1,
            max = Integer.MAX_VALUE - 1,
            description = "Maximum size of a backend response body, in bytes. A larger body is replaced by an error message."
    )
    private int maxResponseBodySize = 1048576;

    private OpenApiMcpConfig(Map<String, Object> mappedConfig) {
        if (mappedConfig == null) return;
        Object value = mappedConfig.get(DOWNSTREAM_HOST);
        if (value != null) downstreamHost = value.toString().trim();
        endpoints = toList(mappedConfig.get(ENDPOINTS));
        value = mappedConfig.get(MAX_RESPONSE_BODY_SIZE);
        if (value != null) maxResponseBodySize = Config.loadIntegerValue(MAX_RESPONSE_BODY_SIZE, value);
    }

    /**
     * Loads openapi-mcp.yaml and registers it with the module registry.
     *
     * @return the configuration
     * @throws ConfigException if downstreamHost is not an absolute http or https URL without a query, fragment or
     *                         user info, or maxResponseBodySize is not between 1 and Integer.MAX_VALUE - 1
     */
    public static OpenApiMcpConfig load() {
        OpenApiMcpConfig config = new OpenApiMcpConfig(Config.getInstance().getJsonMapConfig(CONFIG_NAME));
        config.validate();
        ModuleRegistry.registerModule(CONFIG_NAME, OpenApiMcpConfig.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
        return config;
    }

    private void validate() {
        URI uri;
        try {
            uri = downstreamHost == null ? null : new URI(downstreamHost);
        } catch (URISyntaxException e) {
            uri = null;
        }
        if (uri == null || uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ConfigException("openapi-mcp " + DOWNSTREAM_HOST + " must be an absolute http or https URL, e.g. https://pets.example.com: " + downstreamHost);
        }
        // The endpoint path is appended to it, so it cannot end in a query or fragment.
        if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new ConfigException("openapi-mcp " + DOWNSTREAM_HOST + " must not have a query, fragment or user info: " + downstreamHost);
        }
        // The response body is read up to one byte over the limit.
        if (maxResponseBodySize < 1 || maxResponseBodySize == Integer.MAX_VALUE) {
            throw new ConfigException("openapi-mcp " + MAX_RESPONSE_BODY_SIZE + " must be between 1 and " + (Integer.MAX_VALUE - 1) + ": " + maxResponseBodySize);
        }
    }

    private static List<String> toList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        String s = value.toString().trim();
        if (s.isEmpty()) return List.of();
        if (s.startsWith("[")) {
            try {
                return Config.getInstance().getMapper().readerFor(new TypeReference<List<String>>() {})
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readValue(s);
            } catch (JsonProcessingException | RuntimeException e) {
                throw new ConfigException("openapi-mcp " + ENDPOINTS + " is not a valid JSON array of strings: " + s);
            }
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(e -> !e.isEmpty()).toList();
    }

    public String getDownstreamHost() {
        return downstreamHost;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }

    public int getMaxResponseBodySize() {
        return maxResponseBodySize;
    }
}
