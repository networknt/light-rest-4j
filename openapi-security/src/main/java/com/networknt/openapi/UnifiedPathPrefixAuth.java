package com.networknt.openapi;

import java.util.List;

public class UnifiedPathPrefixAuth {
    String pathPrefix;
    boolean basic;
    boolean jwt;
    boolean swt;
    boolean apikey;
    List<String> jwkServiceIds;

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public boolean isBasic() {
        return basic;
    }

    public void setBasic(boolean basic) {
        this.basic = basic;
    }

    public boolean isJwt() {
        return jwt;
    }

    public void setJwt(boolean jwt) {
        this.jwt = jwt;
    }

    public boolean isSwt() {
        return swt;
    }

    public void setSwt(boolean swt) {
        this.swt = swt;
    }

    public boolean isApikey() {
        return apikey;
    }

    public void setApikey(boolean apikey) {
        this.apikey = apikey;
    }

    public List<String> getJwkServiceIds() {
        return jwkServiceIds;
    }

    public void setJwkServiceIds(List<String> jwkServiceIds) {
        this.jwkServiceIds = jwkServiceIds;
    }
}
