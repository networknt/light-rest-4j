package com.networknt.openapi;

import com.networknt.test.PooledConnectionReuseTest;
import org.junit.jupiter.api.util.SetSystemProperty;

@SetSystemProperty(key = "light_4j_config_password", value = "light")
class HttpFixturePoolReuseTest extends PooledConnectionReuseTest { }
