package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

public class BasePathRewriteTest {
    @Before
    public void testOAuth2Name() {
        String spec = Config.getInstance().getStringFromFile("openapi-server-url-rewrite.yaml");
        OpenApiHelper.init(spec);
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field instance = OpenApiHelper.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void testBasePath() {
        Assert.assertEquals("/namespace/application/v1", OpenApiHelper.basePath);
    }

    @Test
    public void testApiNormalizedPathWithRegex() {
        ApiNormalisedPath normalisedPath = new ApiNormalisedPath("/v1/pets/26", "\\/[^\\/]+");
        Assert.assertEquals(normalisedPath.normalised(), "/pets/26");
    }
}
