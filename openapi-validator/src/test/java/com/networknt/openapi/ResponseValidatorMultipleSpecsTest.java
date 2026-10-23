package com.networknt.openapi;

import com.networknt.status.Status;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseValidatorMultipleSpecsTest {
    private OpenApiRouting previousRouting = OpenApiHandler.routingSnapshot();
    private ResponseValidator petstore;
    private ResponseValidator market;

    @BeforeEach
    void loadMultipleSpecs() {
        previousRouting = OpenApiHandler.routingSnapshot();
        // Model a fresh multi-spec process, independent of other tests' static state.
        new OpenApiHandler("openapi-handler-multiple");
        assertNull(OpenApiHandler.routingSnapshot().single());
        petstore = validator(OpenApiHandler.routingSnapshot().helpers().get("/petstore"));
        market = validator(OpenApiHandler.routingSnapshot().helpers().get("/market"));
    }

    private ResponseValidator validator(OpenApiHelper helper) {
        return new ResponseValidator(new SchemaValidator(helper.openApi3, false), ValidatorConfig.load());
    }

    @AfterEach
    void restoreHelpers() {
        OpenApiHandler.publishRouting(previousRouting);
    }

    @Test
    void publicUriOverloadsResolveEachMappedSpecification() {
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get"));
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get", "200"));
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get", "200", "application/json"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get", "200"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get", "200", "application/json"));
        assertEquals("ERR11004", petstore.validateResponseContent("{}", "/petstore/pets", "get").getCode());
        assertEquals("ERR11004", market.validateResponseContent("{}", "/market/store/products", "get").getCode());
    }

    @Test
    void undefinedMethodReturnsExistingUnexpectedContentStatus() {
        assertEquals("ERR11018", petstore.validateResponseContent("[]", "/petstore/pets", "trace").getCode());
    }

    @Test
    void methodNamesAreCaseInsensitive() {
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "GET"));
        assertEquals("ERR11004", petstore.validateResponseContent("{}", "/petstore/pets", "GET").getCode());
    }

    @Test
    void nonHierarchicalUrisReturnValidationStatus() {
        for (String uri : new String[]{"mailto:x", "urn:test:operation", "", "relative", "http://example.com"}) {
            assertEquals("ERR11018", petstore.validateResponseContent("[]", uri, "get").getCode());
        }
    }

    @Test
    void uriRoutingUsesRawSegments() {
        assertNull(market.validateResponseContent("[]", "/market/store%2Fname/products", "get"));
        assertEquals("ERR11004", market.validateResponseContent("{}", "/market/store%2Fname/products", "get").getCode());
        assertEquals("ERR11018", market.validateResponseContent("[]", "/%6Darket/store/products", "get").getCode());
    }

    @Test
    void aValidatorCannotResolveOperationsFromAnotherSpec() {
        assertEquals("ERR11018", petstore.validateResponseContent("[]", "/market/store/products", "get").getCode());
        assertEquals("ERR11018", petstore.validateResponseContent("[]", "/petstorefoo/pets", "get").getCode());
    }

    @Test
    void invalidContentStillFailsSchemaValidation() {
        Status status = market.validateResponseContent("{}", "/market/store/products", "get");
        assertNotNull(status);
        assertEquals("ERR11004", status.getCode());
    }

    @Test
    void unknownMappingReturnsExistingUnexpectedContentStatus() {
        Status status = petstore.validateResponseContent("[]", "/unknown/pets", "get");
        assertNotNull(status);
        assertEquals("ERR11018", status.getCode());
    }

    @Test
    void unknownPathWithinMappedSpecReturnsExistingUnexpectedContentStatus() {
        Status status = petstore.validateResponseContent("[]", "/petstore/not-found", "get");
        assertNotNull(status);
        assertEquals("ERR11018", status.getCode());
    }

    @Test
    void queryAndAbsoluteUriUseThePathForHelperSelection() {
        assertNull(market.validateResponseContent("[]", "/market/store/products?limit=10", "get"));
        assertNull(market.validateResponseContent("[]", "https://example.com/market/store/products?limit=10", "get"));
    }

    @Test
    void exchangeOverloadUsesTheMappedSpec() {
        HttpServerExchange exchange = new HttpServerExchange(null);
        exchange.setRequestURI("/market/store/products");
        exchange.setRequestMethod(Methods.GET);
        exchange.setStatusCode(200);
        assertNull(market.validateResponseContent("[]", exchange));
    }

    @Test
    void singleSpecModeRemainsSupported() {
        OpenApiHandler.publishRouting(new OpenApiRouting(OpenApiHandler.routingSnapshot().helpers().get("/petstore"), null));
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get"));
    }
}
