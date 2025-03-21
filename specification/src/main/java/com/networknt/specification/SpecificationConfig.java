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

import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.OutputFormat;
import com.networknt.config.schema.StringField;

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
            externalized = true,
            defaultValue = "openapi.yaml",
            description = "The filename with path of the specification file, and usually it is openapi.yaml"
    )
    String fileName;

    @StringField(
            configFieldName = CONTENT_TYPE,
            externalizedKeyName = CONTENT_TYPE,
            externalized = true,
            defaultValue = "text/yaml",
            description = "The content type of the specification file. In most cases, we are using yaml format."
    )
    String contentType;

    public SpecificationConfig() {
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
}
