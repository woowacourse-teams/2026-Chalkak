package com.chalkak.backend.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AdminTest {

    @Test
    @DisplayName("사용자명과 단방향 비밀번호 해시로 관리자를 생성한다")
    void create_validAccount_createsAdmin() {
        // Given
        String username = "dev-admin";
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        // When
        Admin admin = Admin.create(username, passwordHash);

        // Then
        assertThat(admin.getUsername()).isEqualTo(username);
        assertThat(admin.getPasswordHash()).isEqualTo(passwordHash);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("사용자명이 비어 있으면 관리자를 생성할 수 없다")
    void create_blankUsername_throwsException(String username) {
        // Given
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        // When & Then
        assertThatThrownBy(() -> Admin.create(username, passwordHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 계정 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사용자명이 100자를 초과하면 관리자를 생성할 수 없다")
    void create_overlongUsername_throwsException() {
        // Given
        String username = "a".repeat(101);
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        // When & Then
        assertThatThrownBy(() -> Admin.create(username, passwordHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 계정 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("사용자명이 100자이면 관리자를 생성할 수 있다")
    void create_maxLengthUsername_createsAdmin() {
        // Given
        String username = "a".repeat(100);
        String passwordHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

        // When
        Admin admin = Admin.create(username, passwordHash);

        // Then
        assertThat(admin.getUsername()).isEqualTo(username);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @DisplayName("비밀번호 해시가 비어 있으면 관리자를 생성할 수 없다")
    void create_blankPasswordHash_throwsException(String passwordHash) {
        // When & Then
        assertThatThrownBy(() -> Admin.create("dev-admin", passwordHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 계정 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 해시가 255자를 초과하면 관리자를 생성할 수 없다")
    void create_overlongPasswordHash_throwsException() {
        // Given
        String passwordHash = "a".repeat(256);

        // When & Then
        assertThatThrownBy(() -> Admin.create("dev-admin", passwordHash))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 계정 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("BCrypt 형식이 아닌 비밀번호 값으로 관리자를 생성할 수 없다")
    void create_plainPassword_throwsException() {
        // When & Then
        assertThatThrownBy(() -> Admin.create("dev-admin", "plain-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 계정 정보가 올바르지 않습니다.");
    }
}
