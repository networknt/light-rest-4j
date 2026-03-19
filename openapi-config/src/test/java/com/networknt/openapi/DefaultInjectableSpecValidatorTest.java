package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class DefaultInjectableSpecValidatorTest {
    @Test
    public void testValidatorInValid() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-neg");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assertions.assertFalse(isValid);
    }

    @Test
    public void testValidatorValid() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-pos");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assertions.assertTrue(isValid);
    }

    @Test
    public void testNoInject() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = null;
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assertions.assertTrue(isValid);
    }

    @Test
    public void testValidatorDuplicatePath() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-dup-path");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assertions.assertTrue(isValid);
    }

    @Test
    public void testValidatorDuplicateMethod() {
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfig("openapi");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig("openapi-inject-test-dup-method");
        DefaultInjectableSpecValidator validator = new DefaultInjectableSpecValidator();
        boolean isValid = validator.isValid(openapi, inject);
        Assertions.assertFalse(isValid);
    }
}
