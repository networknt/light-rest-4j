package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class DefaultInjectableSpecValidatorTest {
    @Test
    public void testValidatorInValid() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-neg");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assert.assertFalse(isValid);
    }

    @Test
    public void testValidatorValid() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-pos");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assert.assertTrue(isValid);
    }

    @Test
    public void testNoInject() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = null;
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assert.assertTrue(isValid);
    }
}
