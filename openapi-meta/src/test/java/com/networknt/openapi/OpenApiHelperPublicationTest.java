package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiHelperPublicationTest {
    @Test
    void readersKeepThePreviousCompleteMapUntilReloadFinishes() throws Exception {
        OpenApiHelper previousHelper = OpenApiHandler.helper;
        Map<String, OpenApiHelper> previousMap = OpenApiHandler.helperMap;
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OpenApiHelper old = new OpenApiHelper("""
                {"openapi":"3.0.0","info":{"title":"old","version":"1"},
                 "servers":[{"url":"/old"}],"paths":{}}
                """);
        Map<String, OpenApiHelper> published = Map.of("/old", old);
        try {
            OpenApiHandler.helper = null;
            OpenApiHandler.helperMap = published;
            Config.getInstance().setClassLoader(new ClassLoader(getClass().getClassLoader()) {
                @Override
                public InputStream getResourceAsStream(String name) {
                    if (name.endsWith("openapi-market.yaml")) {
                        loading.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("reload timed out");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return super.getResourceAsStream(name);
                }
            });
            try (var executor = Executors.newSingleThreadExecutor()) {
                var reload = executor.submit(() -> new OpenApiHandler("openapi-handler-multiple"));
                try {
                    assertTrue(loading.await(5, TimeUnit.SECONDS));
                    assertSame(published, OpenApiHandler.helperMap);
                    assertSame(old, OpenApiHandler.getHelper("/old/items"));
                    assertNull(OpenApiHandler.getHelper("/market/store/products"));
                } finally {
                    release.countDown();
                }
                reload.get(10, TimeUnit.SECONDS);
            }
            assertEquals(3, OpenApiHandler.helperMap.size());
            assertNotNull(OpenApiHandler.getHelper("/market/store/products"));
            assertThrows(UnsupportedOperationException.class,
                    () -> OpenApiHandler.helperMap.put("/extra", old));
        } finally {
            release.countDown();
            Config.getInstance().setClassLoader(getClass().getClassLoader());
            OpenApiHandler.helper = previousHelper;
            OpenApiHandler.helperMap = previousMap;
        }
    }
}
