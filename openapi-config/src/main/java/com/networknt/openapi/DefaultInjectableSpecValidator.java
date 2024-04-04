package com.networknt.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class DefaultInjectableSpecValidator implements InjectableSpecValidator {
    public static String PATHS = "paths";
    Logger logger = LoggerFactory.getLogger(DefaultInjectableSpecValidator.class);

    @Override
    public boolean isValid(Map<String, Object> openapi, Map<String, Object> inject) {
        // openapi.yaml can be null in the light-gateway with multiple specs are disabled.
        if (inject == null || openapi == null) {
            return true;
        }
        if (inject.get(PATHS) instanceof Map && openapi.get(PATHS) instanceof Map) {
            // each path in injecting spec
            for (Map.Entry<String, Object> injectPath : ((Map<String, Object>) inject.get(PATHS)).entrySet()) {
                // if the path in injecting spec also exists in the original openapi spec
                if (((Map<String, Object>) openapi.get(PATHS)).containsKey(injectPath.getKey())) {
                    // get all the methods within the same path in the openapi spec and the injecting spec
                    Map<String, Object> openapiPath = (Map<String, Object>) ((Map<String, Object>) openapi.get(PATHS)).get(injectPath.getKey());
                    Set<String> openapiMethods = openapiPath.keySet();
                    Set<String> injectMethods = ((Map<String, Object>) injectPath.getValue()).keySet();
                    // compare if there are duplicates
                    for (String injectMethod : injectMethods) {
                        if (openapiMethods.contains(injectMethod)) {
                            logger.error("you are trying to overwritten an existing path: {} with method: {}", injectPath.getKey(), injectMethod);
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
