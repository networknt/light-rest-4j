package com.networknt.openapi;

import java.util.*;

/** Immutable routing generation shared by metadata and schema validation. */
public final class OpenApiRouting {
    private final OpenApiHelper single;
    private final Map<String, OpenApiHelper> helpers;
    private final List<String> prefixes;

    OpenApiRouting(OpenApiHelper single, Map<String, OpenApiHelper> mappings) {
        this.single = single;
        Map<String, OpenApiHelper> canonical = new HashMap<>();
        if (single != null) {
            canonical.put("", single);
        } else if (mappings != null) {
            mappings.forEach((key, value) -> {
                String prefix = normalizePrefix(key);
                if (canonical.putIfAbsent(prefix, Objects.requireNonNull(value)) != null) {
                    throw new IllegalArgumentException("Duplicate OpenAPI routing prefix: " + prefix);
                }
            });
        }
        helpers = Map.copyOf(canonical);
        prefixes = sortedPrefixes(helpers.keySet());
    }

    static String normalizePrefix(String prefix) {
        Objects.requireNonNull(prefix, "OpenAPI routing prefix");
        if (!prefix.isEmpty() && !prefix.startsWith("/")) {
            throw new IllegalArgumentException("OpenAPI routing prefix must start with /: " + prefix);
        }
        int end = prefix.length();
        while (end > 0 && prefix.charAt(end - 1) == '/') end--;
        return prefix.substring(0, end);
    }

    static List<String> sortedPrefixes(Set<String> prefixes) {
        return prefixes.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    /** Prefixes are canonical and ordered longest first; segment boundaries are preserved. */
    public static String matchingPrefix(String path, List<String> prefixes) {
        if (path == null) return null;
        for (String prefix : prefixes) {
            if (path.equals(prefix) || (path.startsWith(prefix) && path.length() > prefix.length()
                    && path.charAt(prefix.length()) == '/')) return prefix;
        }
        return null;
    }

    public String matchingPrefix(String path) { return matchingPrefix(path, prefixes); }
    public List<String> prefixes() { return prefixes; }
    public Map<String, OpenApiHelper> helpers() { return helpers; }
    public OpenApiHelper single() { return single; }
    public OpenApiHelper helperFor(String path) {
        if (single != null) return single;
        String prefix = matchingPrefix(path);
        return prefix == null ? null : helpers.get(prefix);
    }
    public String basePathFor(String path) {
        OpenApiHelper selected = helperFor(path);
        return selected == null ? "" : selected.basePath;
    }
}
