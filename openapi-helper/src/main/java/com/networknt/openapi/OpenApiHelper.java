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

package com.networknt.openapi;

import com.networknt.oas.OpenApiParser;
import com.networknt.oas.model.OpenApi3;
import com.networknt.oas.model.SecurityScheme;
import com.networknt.oas.model.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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
    static final Logger logger = LoggerFactory.getLogger(OpenApiHelper.class);

    public static OpenApi3 openApi3;
    public static List<String> oauth2Names;
    public static String basePath;
    private static OpenApiHelper INSTANCE = null;

    private OpenApiHelper(String spec) {
        try {
            openApi3 = (OpenApi3) new OpenApiParser().parse(spec, new URL("https://oas.lightapi.net/"));
        } catch (MalformedURLException e) {
            logger.error("MalformedURLException", e);
        }
        if(openApi3 == null) {
            logger.error("Unable to load openapi.json");
            throw new RuntimeException("Unable to load openapi.json");
        } else {
            oauth2Names = getOAuth2Name();
            basePath = getBasePath();
        }

    }

    public static OpenApiHelper getInstance() {
        if(INSTANCE == null) {
            return null;
        }
        return INSTANCE;
    }

    public synchronized static OpenApiHelper init(String spec) {
        if(INSTANCE != null) {
            return INSTANCE;
        }
        INSTANCE = new OpenApiHelper(spec);
        return INSTANCE;
    }

    public Optional<NormalisedPath> findMatchingApiPath(final NormalisedPath requestPath) {
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

    private List<String> getOAuth2Name() {
        List<String> names = new ArrayList<>();
        Map<String, SecurityScheme> defMap = openApi3.getSecuritySchemes();
        if(defMap != null) {
            for(Map.Entry<String, SecurityScheme> entry : defMap.entrySet()) {
                if(entry.getValue().getType().equals("oauth2")) {
                    names.add(entry.getKey());
                }
            }
        }
        return names;
    }

    private String getBasePath() {

        String basePath = "";
        String url = null;
        if (openApi3.getServers().size() > 0) {
            Server server = openApi3.getServer(0);
            url = server.getUrl();
        }
        if (url != null) {
            // find "://" index
            int protocolIndex = url.indexOf("://");
            int pathIndex = url.indexOf('/', protocolIndex + 3);
            if (pathIndex > 0) {
                basePath = url.substring(pathIndex);
            }
        }
        return basePath;
    }

    private boolean pathMatches(final NormalisedPath requestPath, final NormalisedPath apiPath) {
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
