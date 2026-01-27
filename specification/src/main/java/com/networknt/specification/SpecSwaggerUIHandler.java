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
import com.networknt.handler.MiddlewareHandler;
import com.networknt.server.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *  Display API Specification in Swagger editor UI
 *
 * @author Gavin Chen
 */
public class SpecSwaggerUIHandler implements MiddlewareHandler {
    // The url in this html is using the spec.yaml API which is served by SpecDisplayHandler. It needs to be in sync with the handler.yml
    public static String swaggerUITemplate =
            "<html>\n" +
            "<head>\n" +
            "    <title>OpenAPI Spec</title>\n" +
            "    <link rel=\"stylesheet\" type=\"text/css\" href=\"https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/3.19.3/swagger-ui.css\">\n" +
            "</head>\n" +
            "<body>\n" +
            "<div id=\"swagger-ui\"></div>\n" +
            "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/3.19.3/swagger-ui-bundle.js\"></script>\n" +
            "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/3.19.3/swagger-ui-standalone-preset.js\"></script>\n" +
            "<script>\n" +
            "window.onload = function() {\n" +
            "\n" +
            "  const ui = SwaggerUIBundle({\n" +
            "    url: \"/spec.yaml\",\n" +
            "    validatorUrl : false,\n" +
            "    dom_id: '#swagger-ui',\n" +
            "    deepLinking: true,\n" +
            "    presets: [\n" +
            "      SwaggerUIBundle.presets.apis,\n" +
            "      SwaggerUIStandalonePreset\n" +
            "    ],\n" +
            "    plugins: [\n" +
            "      SwaggerUIBundle.plugins.DownloadUrl\n" +
            "    ],\n" +
            "    layout: \"StandaloneLayout\"\n" +
            "  })\n" +
            "\n" +
            "  window.ui = ui\n" +
            "}\n" +
            "</script>\n" +
            "</body>\n" +
            "</html>";
    private volatile HttpHandler next;

    public SpecSwaggerUIHandler() {
        SpecificationConfig.load();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/html");
        exchange.getResponseSender().send(getDisplayHtml());
    }

    private String getDisplayHtml() {
        return swaggerUITemplate;
    }

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
