package com.networknt.openapi;

import java.util.Map;

public interface InjectableSpecValidator {
    boolean isValid(Map<String, Object> openapi, Map<String, Object> inject);
}
