package com.networknt.openapi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiRoutingTest {
    private final OpenApiHelper helper = new OpenApiHelper("""
            {"openapi":"3.0.0","info":{"title":"Routing","version":"1"},"paths":{}}
            """);

    @Test
    void canonicalPrefixesHaveUniqueLongestMatchesRegardlessOfInsertionOrder() {
        for (boolean reverse : new boolean[]{false, true}) {
            Map<String, OpenApiHelper> mappings = new LinkedHashMap<>();
            if (reverse) mappings.put("/api/v1/", helper);
            mappings.put("/api/", helper);
            mappings.put("/api/v1/", helper);
            OpenApiRouting routing = new OpenApiRouting(null, mappings);
            assertEquals(Set.of("/api", "/api/v1"), routing.helpers().keySet());
            assertEquals("/api/v1", routing.matchingPrefix("/api/v1/items"));
            assertEquals("/api/v1", routing.matchingPrefix("/api/v1"));
            assertEquals("/api", routing.matchingPrefix("/api/v10/items"));
            assertNull(routing.matchingPrefix("/apix/items"));
            assertThrows(UnsupportedOperationException.class, () -> routing.helpers().clear());
        }
    }

    @Test
    void trailingSlashAliasesAndRootAliasesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiRouting(null, Map.of("/api", helper, "/api/", helper)));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiRouting(null, Map.of("", helper, "/", helper)));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenApiRouting(null, Map.of("api", helper, "/api/", helper)));
    }

    @Test
    void legacyPrefixUtilityRetainsAliasAndRelativeKeyBehaviorWithoutSorting() {
        assertEquals("/api", OpenApiHandler.findMatchingPrefix("/api/items", Set.of("/api", "/api/")));
        assertEquals("v1", OpenApiHandler.findMatchingPrefix("v1/items", Set.of("v1", "v1/")));
        assertEquals("/api/", OpenApiHandler.findMatchingPrefix("/api/items", Set.of("/api/")));
        assertNull(OpenApiHandler.findMatchingPrefix("/apix/items", Set.of("/api")));
        assertNull(OpenApiHandler.findMatchingPrefix(null, Set.of("/api")));
    }

    @Test
    void documentedMappingKeysWithoutLeadingSlashesAreAccepted() {
        OpenApiRouting routing = new OpenApiRouting(null, Map.of("v1", helper));
        assertSame(helper, routing.helperFor("/v1/items"));
        assertEquals(Set.of("/v1"), routing.helpers().keySet());
    }

    @Test
    @SuppressWarnings("deprecation")
    void directWritesToLegacyMirrorsDoNotChangeAuthoritativeRouting() {
        OpenApiRouting previous = OpenApiHandler.routingSnapshot();
        OpenApiHelper decoy = new OpenApiHelper("""
                {"openapi":"3.0.0","info":{"title":"Decoy","version":"1"},"paths":{}}
                """);
        try {
            for (boolean multiple : new boolean[]{false, true}) {
                OpenApiRouting published = new OpenApiRouting(multiple ? null : helper,
                        multiple ? Map.of("/api", helper) : null);
                OpenApiHandler.publishRouting(published);
                for (Runnable write : new Runnable[]{
                        () -> OpenApiHandler.helper = decoy,
                        () -> OpenApiHandler.helper = null,
                        () -> OpenApiHandler.helperMap = Map.of("/api", decoy),
                        () -> OpenApiHandler.helperMap = null}) {
                    write.run();
                    assertSame(published, OpenApiHandler.routingSnapshot());
                    assertSame(helper, OpenApiHandler.routingSnapshot().helperFor("/api/items"));
                    assertSame(helper, OpenApiHandler.getHelper("/api/items"));
                }
            }
        } finally {
            OpenApiHandler.publishRouting(previous);
        }
    }

    @Test
    void adminRoutingPrefixIsNotTheInjectedSpecBasePath() {
        OpenApiRouting routing = new OpenApiRouting(null, Map.of("/adm", helper));
        assertSame(helper, routing.helperFor("/adm/server/info"));
        assertEquals("", routing.basePathFor("/adm/server/info"));
        assertNull(routing.helperFor("/admin/server/info"));
        assertNull(routing.helperFor("/api/adm/server/info"));
    }
}
