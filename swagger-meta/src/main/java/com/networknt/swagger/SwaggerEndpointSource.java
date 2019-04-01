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

package com.networknt.swagger;

import com.networknt.handler.config.EndpointSource;
import io.swagger.models.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SwaggerEndpointSource implements EndpointSource {

    private static final Logger log = LoggerFactory.getLogger(SwaggerEndpointSource.class);

    @Override
    public Iterable<Endpoint> listEndpoints() {

        List<Endpoint> endpoints = new ArrayList<>();
        String basePath = findBasePath();
        Map<String, Path> paths = SwaggerHelper.swagger.getPaths();

        if(log.isInfoEnabled()) log.info("Generating paths from Swagger spec");
        for (Map.Entry<String, Path> pathPair : paths.entrySet()) {
            String path = basePath + pathPair.getKey();
            Path pathImpl = pathPair.getValue();

            if(pathImpl.getGet() != null) addEndpoint(endpoints, path, "get");
            if(pathImpl.getPut() != null) addEndpoint(endpoints, path, "put");
            if(pathImpl.getHead() != null) addEndpoint(endpoints, path, "head");
            if(pathImpl.getPost() != null) addEndpoint(endpoints, path, "post");
            if(pathImpl.getDelete() != null) addEndpoint(endpoints, path, "delete");
            if(pathImpl.getPatch() != null) addEndpoint(endpoints, path, "patch");
            if(pathImpl.getOptions() != null) addEndpoint(endpoints, path, "options");
        }
        return endpoints;
    }

    private static void addEndpoint(List<Endpoint> endpoints, String path, String method) {
        Endpoint endpoint = new Endpoint(path, method);
        if(log.isDebugEnabled()) log.debug(endpoint.toString());
        endpoints.add(endpoint);
    }

    public String findBasePath() {
        String basePath = SwaggerHelper.swagger.getBasePath();
        if(basePath == null) {
            log.warn("No basePath found in Swagger spec. Using empty base path for API.");
            return "";
        }
        while (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }

}
