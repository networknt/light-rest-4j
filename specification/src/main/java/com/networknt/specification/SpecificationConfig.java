/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.specification;

import com.networknt.config.Config;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.OutputFormat;
import com.networknt.config.schema.StringField;
import com.networknt.server.ModuleRegistry;

import java.util.Map;

/**
 * Config class for Spec display Handler
 *
 */
@ConfigSchema(
        configName = "specification",
        configKey = "specification",
        configDescription = "Specification type and file name definition",
        outputFormats = {OutputFormat.JSON_SCHEMA, OutputFormat.YAML}
)
public class SpecificationConfig {
    public static final String CONFIG_NAME = "specification";
    public static final String FILE_NAME = "fileName";
    public static final String CONTENT_TYPE = "contentType";

    @StringField(
            configFieldName = FILE_NAME,
            externalizedKeyName = FILE_NAME,
            defaultValue = "openapi.yaml",
            description = "The filename with path of the specification file, and usually it is openapi.yaml"
    )
    private String fileName;

    @StringField(
            configFieldName = CONTENT_TYPE,
            externalizedKeyName = CONTENT_TYPE,
            defaultValue = "text/yaml",
            description = "The content type of the specification file. In most cases, we are using yaml format."
    )
    private String contentType;

    private final Map<String, Object> mappedConfig;
    private static SpecificationConfig instance;

    private SpecificationConfig(String configName) {
        mappedConfig = Config.getInstance().getJsonMapConfig(configName);
        if (this.mappedConfig != null) {
            setConfigData(this.mappedConfig);
        }
    }

    public static SpecificationConfig load() {
        return load(CONFIG_NAME);
    }

    public static SpecificationConfig load(String configName) {
        if (CONFIG_NAME.equals(configName)) {
            Map<String, Object> mappedConfig = Config.getInstance().getJsonMapConfig(configName);
            if (instance != null && instance.getMappedConfig() == mappedConfig) {
                return instance;
            }
            synchronized (SpecificationConfig.class) {
                mappedConfig = Config.getInstance().getJsonMapConfig(configName);
                if (instance != null && instance.getMappedConfig() == mappedConfig) {
                    return instance;
                }
                instance = new SpecificationConfig(configName);
                ModuleRegistry.registerModule(CONFIG_NAME, SpecificationConfig.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
                return instance;
            }
        }
        return new SpecificationConfig(configName);
    }



    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }

    private void setConfigData(Map<String, Object> mappedConfig) {
        Object object = mappedConfig.get(FILE_NAME);
        if (object != null) fileName = (String) object;
        object = mappedConfig.get(CONTENT_TYPE);
        if (object != null) contentType = (String) object;
    }
}
