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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.networknt.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.networknt.handler.config.EndpointSource;

public class OpenApiEndpointSourceTest {
    private static OpenApiHelper helper;
	private static final Set<String> EXPECTED = new HashSet<>(Arrays.asList(
            "/v1/pets@get",
            "/v1/pets@post",
            "/v1/pets/{petId}@get",
            "/v1/pets/{petId}@delete"
        ));

    static {
        String spec = Config.getInstance().getStringFromFile("openapi.yaml");
        helper = new OpenApiHelper(spec);
    }

    @Test
    public void testFindBasePath() {
        OpenApiEndpointSource source = new OpenApiEndpointSource(helper);
        String basePath = source.findBasePath();
        Assertions.assertEquals("/v1", basePath);
    }

    @Test
    public void testPetstoreEndpoints() {
        OpenApiEndpointSource source = new OpenApiEndpointSource(helper);
        Iterable<EndpointSource.Endpoint> endpoints = source.listEndpoints();

        // Extract a set of string representations of endpoints
        Set<String> endpointStrings = StreamSupport
            .stream(endpoints.spliterator(), false)
            .map(Object::toString)
            .collect(Collectors.toSet());

        for (String endpoint: EXPECTED) {
        	assertTrue(endpointStrings.contains(endpoint));
        }

    }

}
