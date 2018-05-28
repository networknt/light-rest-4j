package com.networknt.basic;

import java.util.Map;

public class BasicConfig {
    boolean enabled;
    Map<String, String> users;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getUsers() {
        return users;
    }

    public void setUsers(Map<String, String> users) {
        this.users = users;
    }
}
