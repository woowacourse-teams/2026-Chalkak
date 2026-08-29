package com.chalkak.backend.auth.domain;

public enum AccessTokenScope {
    USER,
    ADMIN;

    public String authority() {
        return "SCOPE_" + name();
    }
}
