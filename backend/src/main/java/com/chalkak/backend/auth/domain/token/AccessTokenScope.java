package com.chalkak.backend.auth.domain.token;

public enum AccessTokenScope {
    USER, ADMIN;

    public String toAuthority() {
        return "SCOPE_" + name();
    }
}
