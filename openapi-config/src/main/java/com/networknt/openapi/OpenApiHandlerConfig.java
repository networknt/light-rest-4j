package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import com.networknt.config.JsonMapper;
import com.networknt.config.schema.BooleanField;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.MapField;
import com.networknt.config.schema.OutputFormat;
import com.networknt.server.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@ConfigSchema(
        configName = "openapi-handler",
        configKey = "openapi-handler",
        configDescription = "openapi-handler.yml",
        outputFormats = {OutputFormat.JSON_SCHEMA, OutputFormat.YAML}
)
public class OpenApiHandlerConfig {
    private static final Logger logger = LoggerFactory.getLogger(OpenApiHandlerConfig.class);
    public static final String CONFIG_NAME = "openapi-handler";
    private static final String MULTIPLE_SPEC = "multipleSpec";
    private static final String IGNORE_INVALID_PATH = "ignoreInvalidPath";
    private static final String PATH_SPEC_MAPPING = "pathSpecMapping";

    private final Map<String, Object> mappedConfig;
    private static OpenApiHandlerConfig instance;

    @BooleanField(
        configFieldName = MULTIPLE_SPEC,
        externalizedKeyName = MULTIPLE_SPEC,
        defaultValue = "false",
        description = "This configuration file is used to support multiple OpenAPI " +
                "specifications in the same light-rest-4j instance.\n" +
                "An indicator to allow multiple openapi specifications. " +
                "Default to false which only allow one spec named openapi.yml or openapi.yaml or openapi.json."
    )
    boolean multipleSpec;

    @BooleanField(
            configFieldName = IGNORE_INVALID_PATH,
            externalizedKeyName = IGNORE_INVALID_PATH,
            defaultValue = "false",
            description = "When the OpenApiHandler is used in a shared gateway and some backend APIs have no " +
                    "specifications deployed on the gateway, the handler will return\n" +
                    "an invalid request path error to the client. " +
                    "To allow the call to pass through the OpenApiHandler and route to the backend APIs, you can set this\n" +
                    "flag to true. In this mode, the handler will only add the endpoint " +
                    "specification to the auditInfo if it can find it. " +
                    "Otherwise, it will pass through."
    )
    boolean ignoreInvalidPath;

    @MapField(
            configFieldName = PATH_SPEC_MAPPING,
            externalizedKeyName = PATH_SPEC_MAPPING,
            description = "Path to spec mapping. One or more base paths can map to the same specifications. " +
                    "The key is the base path and the value is the specification name.\n" +
                    "If users want to use multiple specification files in the same instance, " +
                    "each specification must have a unique base path and it must be set as key.\n" +
                    "  v1: openapi-v1\n" +
                    "  v2: openapi-v2",
            valueType = String.class
    )
    Map<String, Object> pathSpecMapping;


    private OpenApiHandlerConfig() {
        this(CONFIG_NAME);
    }

    /**
     * Please note that this constructor is only for testing to load different config files
     * @param configName String
     */
    private OpenApiHandlerConfig(String configName) {
        mappedConfig = Config.getInstance().getJsonMapConfig(configName);
        setConfigData();
        setConfigMap();
    }

    public static OpenApiHandlerConfig load() {
        return load(CONFIG_NAME);
    }

    public static OpenApiHandlerConfig load(String configName) {
        if (CONFIG_NAME.equals(configName)) {
            Map<String, Object> mappedConfig = Config.getInstance().getJsonMapConfig(configName);
            if (instance != null && instance.getMappedConfig() == mappedConfig) {
                return instance;
            }
            synchronized (OpenApiHandlerConfig.class) {
                mappedConfig = Config.getInstance().getJsonMapConfig(configName);
                if (instance != null && instance.getMappedConfig() == mappedConfig) {
                    return instance;
                }
                instance = new OpenApiHandlerConfig(configName);
                ModuleRegistry.registerModule(CONFIG_NAME, OpenApiHandlerConfig.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
                return instance;
            }
        }
        return new OpenApiHandlerConfig(configName);
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
