package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import com.networknt.config.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class OpenApiHandlerConfig {
    private static final Logger logger = LoggerFactory.getLogger(OpenApiHandlerConfig.class);
    public static final String CONFIG_NAME = "openapi-handler";
    private static final String MULTIPLE_SPEC = "multipleSpec";
    private static final String IGNORE_INVALID_PATH = "ignoreInvalidPath";
    private static final String PATH_SPEC_MAPPING = "pathSpecMapping";

    boolean multipleSpec;
    boolean ignoreInvalidPath;
    Map<String, Object> pathSpecMapping;

    private Config config;
    private Map<String, Object> mappedConfig;

    private OpenApiHandlerConfig() {
        this(CONFIG_NAME);
    }

    /**
     * Please note that this constructor is only for testing to load different config files
     * to test different configurations.
     * @param configName String
     */
    private OpenApiHandlerConfig(String configName) {
        config = Config.getInstance();
        mappedConfig = config.getJsonMapConfigNoCache(configName);
        setConfigData();
        setConfigMap();
    }

    public static OpenApiHandlerConfig load() {
        return new OpenApiHandlerConfig();
    }

    public static OpenApiHandlerConfig load(String configName) {
        return new OpenApiHandlerConfig(configName);
    }

    void reload() {
        mappedConfig = config.getJsonMapConfigNoCache(CONFIG_NAME);
        setConfigData();
        setConfigMap();
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }

    public boolean isMultipleSpec() {
        return multipleSpec;
    }
    public boolean isIgnoreInvalidPath() { return ignoreInvalidPath; }
    private void setConfigData() {
        Object object = mappedConfig.get(MULTIPLE_SPEC);
        if (object != null) multipleSpec = Config.loadBooleanValue(MULTIPLE_SPEC, object);
        object = mappedConfig.get(IGNORE_INVALID_PATH);
        if (object != null) ignoreInvalidPath = Config.loadBooleanValue(IGNORE_INVALID_PATH, object);
    }

    public Map<String, Object> getPathSpecMapping() {
        return pathSpecMapping;
    }

    public void setPathSpecMapping(Map<String, Object> pathSpecMapping) {
        this.pathSpecMapping = pathSpecMapping;
    }

    private void setConfigMap() {
        if (mappedConfig.get(PATH_SPEC_MAPPING) != null) {
            Object object = mappedConfig.get(PATH_SPEC_MAPPING);
            pathSpecMapping = new HashMap<>();
            if(object instanceof String) {
                String s = (String)object;
                s = s.trim();
                if(logger.isTraceEnabled()) logger.trace("s = " + s);
                if(s.startsWith("{")) {
                    // json format
                    try {
                        pathSpecMapping = JsonMapper.string2Map(s);
                    } catch (Exception e) {
                        throw new ConfigException("could not parse the pathSpecMapping json with a map of strings.");
                    }
                } else {
                    // comma separated
                    String[] pairs = s.split(",");
                    for (int i = 0; i < pairs.length; i++) {
                        String pair = pairs[i];
                        String[] keyValue = pair.split(":");
                        pathSpecMapping.put(keyValue[0], keyValue[1]);
                    }
                }
            } else if (object instanceof Map) {
                pathSpecMapping = (Map)object;
            } else {
                throw new ConfigException("pathSpecMapping must be a string or a list of strings.");
            }
        }
    }
}
