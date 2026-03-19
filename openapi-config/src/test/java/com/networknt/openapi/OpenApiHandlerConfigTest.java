package com.networknt.openapi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OpenApiHandlerConfigTest {
    @Test
    public void testLoadConfig() {
        OpenApiHandlerConfig config = OpenApiHandlerConfig.load();
        Assertions.assertEquals(3, config.getMappedConfig().size());
    }
}
