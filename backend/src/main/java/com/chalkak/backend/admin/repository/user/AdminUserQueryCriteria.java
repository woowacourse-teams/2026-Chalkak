package com.chalkak.backend.admin.repository.user;

public record AdminUserQueryCriteria(
        AdminUserStatus status,
        String email,
        AdminUserSort sort) {
}
