/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package com.networknt.openapi;

import com.networknt.config.Config;
import com.networknt.oas.OpenApiParser;
import com.networknt.oas.model.OpenApi3;
import com.networknt.oas.model.SecurityScheme;
import com.networknt.oas.model.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

/**
 * This class load and cache openapi.json in a static block so that it can be
 * shared by security for scope validation and validator for schema validation
 *
 * This handler supports openapi.yml, openapi.yaml and openapi.json and above
 * is the loading sequence.
 *
 * @author Steve Hu
 */
public class OpenApiHelper {

    static final String OPENAPI_YML_CONFIG = "openapi.yml";
    static final String OPENAPI_YAML_CONFIG = "openapi.yaml";
    static final String OPENAPI_JSON_CONFIG = "openapi.json";

    static final Logger logger = LoggerFactory.getLogger(OpenApiHelper.class);

    public static OpenApi3 openApi3;
    public static String oauth2Name;
    public static String basePath;

    static {
        try {
            // first try to load the specification into a string regardless it is in YAML or JSON.
            String spec = Config.getInstance().getStringFromFile(OPENAPI_YML_CONFIG);
            if(spec == null) {
                spec = Config.getInstance().getStringFromFile(OPENAPI_YAML_CONFIG);
                if(spec == null) {
                    spec = Config.getInstance().getStringFromFile(OPENAPI_JSON_CONFIG);
                }
            }
            openApi3 = (OpenApi3) new OpenApiParser().parse(spec, new URL("https://oas.lightapi.net/"));
        } catch (MalformedURLException e) {
            logger.error("MalformedURLException", e);
        }
        if(openApi3 == null) {
            logger.error("Unable to load openapi.json");
            throw new RuntimeException("Unable to load openapi.json");
        } else {
            oauth2Name = getOAuth2Name();
            basePath = getBasePath();
        }
    }

    public static Optional<NormalisedPath> findMatchingApiPath(final NormalisedPath requestPath) {
        if(OpenApiHelper.openApi3 != null) {
            return OpenApiHelper.openApi3.getPaths().keySet()
                    .stream()
                    .map(p -> (NormalisedPath) new ApiNormalisedPath(p))
                    .filter(p -> pathMatches(requestPath, p))
                    .findFirst();
        } else {
            return Optional.empty();
        }
    }

    private static String getOAuth2Name() {
        String name = null;
        Map<String, SecurityScheme> defMap = openApi3.getSecuritySchemes();
        if(defMap != null) {
            for(Map.Entry<String, SecurityScheme> entry : defMap.entrySet()) {
                if(entry.getValue().getType().equals("oauth2")) {
                    name = entry.getKey();
                    break;
                }
            }
        }
        return name;
    }

    private static String getBasePath() {
        String basePath = "";
        Server server = openApi3.getServer(0);
        String url = server.getUrl();
        if(url != null) {
            // find "://" index
            int protocolIndex = url.indexOf("://");
            int pathIndex = url.indexOf('/', protocolIndex + 3);
            if(pathIndex > 0) {
                basePath = url.substring(pathIndex);
            }
        }
        return basePath;
    }

    private static boolean pathMatches(final NormalisedPath requestPath, final NormalisedPath apiPath) {
        if (requestPath.parts().size() != apiPath.parts().size()) {
            return false;
        }
        for (int i = 0; i < requestPath.parts().size(); i++) {
            if (requestPath.part(i).equalsIgnoreCase(apiPath.part(i)) || apiPath.isParam(i)) {
                continue;
            }
            return false;
        }
        return true;
    }
}
