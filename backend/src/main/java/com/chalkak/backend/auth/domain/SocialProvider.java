package com.chalkak.backend.auth.domain;

public enum SocialProvider {

    GOOGLE("Google"),
    KAKAO("Kakao"),
    APPLE("Apple");

    private final String displayName;

    SocialProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
