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
        Assert.assertEquals("/api/v1", OpenApiHelper.basePath);
    }
}
