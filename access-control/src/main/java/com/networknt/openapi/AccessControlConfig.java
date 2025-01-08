/*
 * Copyright (c) 2019 Network New Technologies Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.config.Config;
import com.networknt.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

class AccessControlConfig {
    private static final Logger logger = LoggerFactory.getLogger(AccessControlConfig.class);
    public static final String CONFIG_NAME = "access-control";
    private static final String ENABLED = "enabled";
    private static final String ACCESS_RULE_LOGIC = "accessRuleLogic";
    private static final String DEFAULT_DENY = "defaultDeny";
    private static final String SKIP_PATH_PREFIXES = "skipPathPrefixes";

    private Map<String, Object> mappedConfig;
    private final Config config;

    boolean enabled;
    String accessRuleLogic;
    boolean defaultDeny;
    private List<String> skipPathPrefixes;


    private AccessControlConfig() {
        this(CONFIG_NAME);
    }
    private AccessControlConfig(String configName) {
        config = Config.getInstance();
        mappedConfig = config.getJsonMapConfigNoCache(configName);
        setConfigData();
        setConfigList();
    }

    public static AccessControlConfig load() {
        return new AccessControlConfig(CONFIG_NAME);
    }
    public static AccessControlConfig load(String configName) {
        return new AccessControlConfig(configName);
    }

    public void reload() {
        mappedConfig = config.getJsonMapConfigNoCache(CONFIG_NAME);
        setConfigData();
        setConfigList();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessRuleLogic() {
        return accessRuleLogic;
    }

    public void setAccessRuleLogic(String accessRuleLogic) {
        this.accessRuleLogic = accessRuleLogic;
    }

    public boolean isDefaultDeny() {
        return defaultDeny;
    }

    public void setDefaultDeny(boolean defaultDeny) {
        this.defaultDeny = defaultDeny;
    }

    public List<String> getSkipPathPrefixes() {
        return skipPathPrefixes;
    }

    public void setSkipPathPrefixes(List<String> skipPathPrefixes) {
        this.skipPathPrefixes = skipPathPrefixes;
    }
    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }
    Config getConfig() {
        return config;
    }

    private void setConfigData() {
        if(getMappedConfig() != null) {
            Object object = getMappedConfig().get(ENABLED);
            if(object != null) enabled = Config.loadBooleanValue(ENABLED, object);
            object = getMappedConfig().get(DEFAULT_DENY);
            if(object != null) defaultDeny = Config.loadBooleanValue(DEFAULT_DENY, object);
            object = getMappedConfig().get(ACCESS_RULE_LOGIC);
            if(object != null) accessRuleLogic = (String)object;
        }
    }

    private void setConfigList() {
        if (mappedConfig != null && mappedConfig.get(SKIP_PATH_PREFIXES) != null) {
            Object object = mappedConfig.get(SKIP_PATH_PREFIXES);
            skipPathPrefixes = new ArrayList<>();
            if(object instanceof String) {
                String s = (String)object;
                s = s.trim();
                if(logger.isTraceEnabled()) logger.trace("s = {}", s);
                if(s.startsWith("[")) {
                    // json format
                    try {
                        skipPathPrefixes = Config.getInstance().getMapper().readValue(s, new TypeReference<List<String>>() {});
                    } catch (Exception e) {
                        throw new ConfigException("could not parse the skipPathPrefixes json with a list of strings.");
                    }
                } else {
                    // comma separated
                    skipPathPrefixes = Arrays.asList(s.split("\\s*,\\s*"));
                }
            } else if (object instanceof List) {
                List prefixes = (List)object;
                prefixes.forEach(item -> {
                    skipPathPrefixes.add((String)item);
                });
            } else {
                throw new ConfigException("skipPathPrefixes must be a string or a list of strings.");
            }
        }
    }

}
