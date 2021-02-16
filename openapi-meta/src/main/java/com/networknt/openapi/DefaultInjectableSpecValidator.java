package com.networknt.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DefaultInjectableSpecValidator implements InjectableSpecValidator{
    public static String PATHS = "paths";
    Logger logger = LoggerFactory.getLogger(DefaultInjectableSpecValidator.class);
    @Override
    public boolean isValid(Map<String, Object> openapi, Map<String, Object> inject) {
        if (inject == null) {
            return true;
        }
        if (inject.get(PATHS) instanceof Map && openapi.get(PATHS) instanceof Map) {
            for (Map.Entry<String, Object> path : ((Map<String, Object>) inject.get(PATHS)).entrySet()) {
                if (((Map<String, Object>)openapi.get(PATHS)).containsKey(path.getKey())) {
                    logger.error("you are trying to overwritten an existing path: {}", path.getKey());
                    return false;
                }
            }
        }
        return true;
    }
}
