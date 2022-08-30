package com.networknt.openapi;

import org.junit.Assert;
import org.junit.Test;

public class OpenApiHandlerConfigTest {
    @Test
    public void testLoadConfig() {
        OpenApiHandlerConfig config = OpenApiHandlerConfig.load();
        Assert.assertEquals(2, config.getMappedConfig().size());
    }
}
