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
 *  Display API Specification
 *
 * @author Gavin Chen
 */
public class SpecDisplayHandler implements MiddlewareHandler {
    private static SpecificationConfig config;
    private volatile HttpHandler next;

    public SpecDisplayHandler() {
        config = SpecificationConfig.load();
        if(logger.isInfoEnabled()) logger.info("SpecDisplayHandler is constructed");
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final String payload = Config.getInstance().getStringFromFile(config.getFileName());
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), config.getContentType());
        exchange.getResponseSender().send(payload);
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

    @Override
    public void register() {
        ModuleRegistry.registerModule(SpecificationConfig.CONFIG_NAME, SpecDisplayHandler.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(SpecificationConfig.CONFIG_NAME), null);
    }

    @Override
    public void reload() {
        SpecificationConfig.reload();
        config = SpecificationConfig.load();
    }
}
