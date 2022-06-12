package com.networknt.openapi;

import com.networknt.security.SecurityConfig;
import org.junit.Assert;
import org.junit.Test;

public class SecurityConfigTest {
    @Test
    public void testLoadConfig() {
        SecurityConfig config = SecurityConfig.load("openapi-security");
        Assert.assertTrue(config.isEnableVerifyJwt());
        Assert.assertFalse(config.isEnableVerifyJwtScopeToken());
        Assert.assertEquals(2, config.getCertificate().size());

    }
}
