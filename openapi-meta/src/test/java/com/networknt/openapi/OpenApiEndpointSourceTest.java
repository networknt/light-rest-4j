package com.networknt.openapi;

import com.networknt.handler.config.EndpointSource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class OpenApiEndpointSourceTest {

    @Test
    public void testFindBasePath() {
        OpenApiEndpointSource source = new OpenApiEndpointSource();
        String basePath = source.findBasePath();
        Assert.assertEquals("/v1", basePath);
    }

    @Test
    public void testPetstoreEndpoints() {
        OpenApiEndpointSource source = new OpenApiEndpointSource();
        Iterable<EndpointSource.Endpoint> endpoints = source.listEndpoints();

        // Extract a set of string representations of endpoints
        Set<String> endpointStrings = StreamSupport
            .stream(endpoints.spliterator(), false)
            .map(Object::toString)
            .collect(Collectors.toSet());

        // Assert that we got what we wanted
        Assert.assertEquals(
            new HashSet<>(Arrays.asList(
                "/v1/pets@get",
                "/v1/pets@post",
                "/v1/pets/{petId}@get",
                "/v1/pets/{petId}@delete"
            )),
            endpointStrings
        );
    }

}
