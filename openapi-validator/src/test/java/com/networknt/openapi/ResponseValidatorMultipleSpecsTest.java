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
    private OpenApiHelper previousHelper;
    private Map<String, OpenApiHelper> previousHelpers;
    private ResponseValidator petstore;
    private ResponseValidator market;

    @BeforeEach
    void loadMultipleSpecs() {
        previousHelper = OpenApiHandler.helper;
        previousHelpers = OpenApiHandler.helperMap;
        // Model a fresh multi-spec process, independent of other tests' static state.
        OpenApiHandler.helper = null;
        new OpenApiHandler("openapi-handler-multiple");
        assertNull(OpenApiHandler.helper);
        petstore = validator(OpenApiHandler.helperMap.get("/petstore"));
        market = validator(OpenApiHandler.helperMap.get("/market"));
    }

    private ResponseValidator validator(OpenApiHelper helper) {
        return new ResponseValidator(new SchemaValidator(helper.openApi3, false), ValidatorConfig.load());
    }

    @AfterEach
    void restoreHelpers() {
        OpenApiHandler.helper = previousHelper;
        OpenApiHandler.helperMap = previousHelpers;
    }

    @Test
    void publicUriOverloadsResolveEachMappedSpecification() {
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get"));
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get", "200"));
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get", "200", "application/json"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get", "200"));
        assertNull(market.validateResponseContent("[]", "/market/store/products", "get", "200", "application/json"));
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
        OpenApiHandler.helper = OpenApiHandler.helperMap.get("/petstore");
        OpenApiHandler.helperMap = null;
        assertNull(petstore.validateResponseContent("[]", "/petstore/pets", "get"));
    }
}
