package com.chalkak.backend.auth.domain;

public enum AccessTokenScope {
    USER,
    ADMIN;

    public String toAuthority() {
        return "SCOPE_" + name();
    }
}
