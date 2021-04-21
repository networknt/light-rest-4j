package com.networknt.openapi;

import com.networknt.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BasePathTest {
    @Before
    public void testOAuth2Name() {
        String spec = Config.getInstance().getStringFromFile("openapi-relative-server-url.yaml");
        OpenApiHelper.init(spec);
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
