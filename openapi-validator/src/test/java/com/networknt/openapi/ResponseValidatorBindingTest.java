package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseValidatorBindingTest {
    private OpenApiRouting previousRouting = OpenApiHandler.routingSnapshot();
    private ValidatorHandler handler;

    @BeforeEach
    void loadSpecsWithConflictingLocalReferences() {
        previousRouting = OpenApiHandler.routingSnapshot();
        new OpenApiHandler("openapi-handler-response-binding");
        handler = new ValidatorHandler();
    }

    @AfterEach
    void restoreHelpers() {
        OpenApiHandler.publishRouting(previousRouting);
    }

    private static void assertSchema(ResponseValidator validator, String uri, String valid, String invalid) {
        assertNotNull(validator);
        assertNull(validator.validateResponseContent(valid, uri, "GET"));
        Status status = validator.validateResponseContent(invalid, uri, "GET");
        assertNotNull(status, "invalid content must not bypass schema validation");
        assertEquals("ERR11004", status.getCode());
    }

    @Test
    void overlappingMappingsUseTheirOwnLocalReferenceContexts() {
        assertSchema(handler.getResponseValidator("/pet/items"), "/pet/items", "{\"value\":\"dog\"}", "{\"value\":42}");
        assertSchema(handler.getResponseValidator("/petstore/items"), "/petstore/items", "{\"value\":42}", "{\"value\":\"dog\"}");
        assertSchema(handler.getResponseValidator("/petstore/v2/items"), "/petstore/v2/items", "{\"value\":true}", "{\"value\":42}");
    }

    @Test
    void prefixSelectionAgreesWithMetadataInBothInsertionOrders() {
        Map<String, OpenApiHelper> original = OpenApiHandler.helperMap;
        for (boolean reversed : new boolean[]{false, true}) {
            Map<String, OpenApiHelper> ordered = new LinkedHashMap<>();
            if (reversed) ordered.put("/petstore/v2", original.get("/petstore/v2"));
            ordered.put("/pet", original.get("/pet"));
            ordered.put("/petstore", original.get("/petstore"));
            ordered.put("/petstore/v2", original.get("/petstore/v2"));
            OpenApiHandler.publishRouting(new OpenApiRouting(null, ordered));
            ValidatorHandler selected = new ValidatorHandler();
            assertSame(original.get("/petstore/v2"), OpenApiHandler.getHelper("/petstore/v2/items"));
            assertSchema(selected.getResponseValidator("/petstore/v2/items"), "/petstore/v2/items",
                    "{\"value\":true}", "{\"value\":42}");
            assertSame(selected.getRequestValidator("/petstore/items"), selected.getRequestValidator("/petstore/v20/items"));
            assertSame(selected.getResponseValidator("/petstore/items"), selected.getResponseValidator("/petstore/v20/items"));
            assertNull(selected.getRequestValidator("/petstorefoo/items"));
            assertNull(selected.getResponseValidator("/petstorefoo/items"));
            assertNull(OpenApiHandler.getHelper("/petstorefoo/items"));
        }
    }

    @Test
    void aBoundValidatorRejectsOtherMappings() {
        ResponseValidator pet = handler.getResponseValidator("/pet/items");
        assertEquals("ERR11018", pet.validateResponseContent("{\"value\":42}", "/petstore/items", "get").getCode());
        assertEquals("ERR11018", pet.validateResponseContent("{\"value\":true}", "/petstore/v2/items", "get").getCode());
    }

    @Test
    void adminHelperValidatesItsReferencedResponseSchema() {
        String uri = "/adm/messagereplaymetadata";
        ResponseValidator admin = handler.getResponseValidator(uri);
        assertNotNull(admin);
        assertNull(admin.validateResponseContent("{\"keySchemaId\":1}", uri, "POST"));
        assertEquals("ERR11004", admin.validateResponseContent("{\"keySchemaId\":\"bad\"}", uri, "POST").getCode());
    }

    @Test
    void retainedValidatorsKeepTheirOperationAndReferenceSnapshotAfterReload() {
        ResponseValidator retained = handler.getResponseValidator("/pet/items");
        String spec = Config.getInstance().getStringFromFile("response-validator-pet.yaml");
        OpenApiHelper replacement = new OpenApiHelper(spec.replace("type: string", "type: integer"));
        OpenApiHandler.publishRouting(new OpenApiRouting(null, Map.of("/pet", replacement)));
        ValidatorHandler reloaded = new ValidatorHandler();
        assertSchema(retained, "/pet/items", "{\"value\":\"dog\"}", "{\"value\":42}");
        assertSchema(reloaded.getResponseValidator("/pet/items"), "/pet/items", "{\"value\":42}", "{\"value\":\"dog\"}");
    }

    @Test
    void legacyConstructorBindsTheSchemaOwnerAndRejectsMissingInputs() {
        OpenApiHelper pet = OpenApiHandler.helperMap.get("/pet");
        ResponseValidator validator = new ResponseValidator(new SchemaValidator(pet.openApi3), ValidatorConfig.load());
        assertSchema(validator, "/pet/items", "{\"value\":\"dog\"}", "{\"value\":42}");
        assertEquals("ERR11018", validator.validateResponseContent("{}", "/petstore/items", "get").getCode());
        assertEquals("ERR11018", validator.validateResponseContent("{}", (String) null, "get").getCode());
        assertEquals("ERR11018", validator.validateResponseContent("{}", "/pet/items", null).getCode());
    }
}
