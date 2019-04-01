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

import com.networknt.handler.config.EndpointSource;
import com.networknt.oas.model.Path;
import com.networknt.oas.model.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists endpoints defined in the OpenApi spec parsed by OpenApiHelper.
 */
public class OpenApiEndpointSource implements EndpointSource {

    private static final Logger log = LoggerFactory.getLogger(OpenApiEndpointSource.class);

    @Override
    public Iterable<Endpoint> listEndpoints() {

        List<Endpoint> endpoints = new ArrayList<>();
        String basePath = findBasePath();
        Map<String, Path> paths = OpenApiHelper.openApi3.getPaths();


        if(log.isInfoEnabled()) log.info("Generating paths from OpenApi spec");
        for (Map.Entry<String, Path> pathPair : paths.entrySet()) {
            String path = pathPair.getKey();
            for (String method : pathPair.getValue().getOperations().keySet()) {
                Endpoint endpoint = new Endpoint(basePath + path, method);
                if(log.isDebugEnabled()) log.debug(endpoint.toString());
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    public String findBasePath() {
        List<Server> servers = OpenApiHelper.openApi3.getServers();
        if(servers.isEmpty()) {
            log.warn("No server found in OpenApi spec. Using empty base path for API.");
            return "";
        }

        Server server = servers.get(0);
        String url = null;
        try {
            url = server.getUrl();
            URL urlObj = new URL(url);
            String basePath = urlObj.getPath();
            while (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            return basePath;
        } catch (Exception e) {
            throw new RuntimeException("Malformed servers[0].url in OpenApi spec: " + url, e);
        }
    }
}
