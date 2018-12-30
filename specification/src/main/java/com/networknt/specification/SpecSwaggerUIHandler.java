
package com.networknt.specification;

import com.networknt.handler.LightHttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *  Display API Specification in Swagger editor UI
 *
 * @author Gavin Chen
 */
public class SpecSwaggerUIHandler implements LightHttpHandler {

    public SpecSwaggerUIHandler(){}



    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/html");
        exchange.getResponseSender().send(getDisplayHtml());
    }

    private String getDisplayHtml() {
        return "<html>\n" +
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
                "    url: \"spec.yaml\",\n" +
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
    }
}
